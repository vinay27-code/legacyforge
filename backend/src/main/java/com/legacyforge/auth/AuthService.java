package com.legacyforge.auth;

import com.legacyforge.auth.dto.AuthResponse;
import com.legacyforge.auth.dto.LoginRequest;
import com.legacyforge.auth.dto.RegisterRequest;
import com.legacyforge.auth.dto.UserSummary;
import com.legacyforge.auth.entity.RefreshToken;
import com.legacyforge.auth.entity.User;
import com.legacyforge.auth.repo.RefreshTokenRepository;
import com.legacyforge.auth.repo.UserRepository;
import com.legacyforge.common.ApiException;
import com.legacyforge.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Duration refreshTtl;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.refresh-ttl-days:7}") long refreshTtlDays
    ) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
    }

    /** Register a new USER account. Returns access token + refresh token value. */
    @Transactional
    public IssuedTokens register(RegisterRequest req) {
        String email = req.email().toLowerCase().trim();
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = new User(email, passwordEncoder.encode(req.password()), User.Role.USER);
        users.save(user);
        log.info("Registered new user id={}", user.getId());
        return issueTokens(user);
    }

    /** Verify credentials and issue tokens. */
    @Transactional
    public IssuedTokens login(LoginRequest req) {
        String email = req.email().toLowerCase().trim();
        User user = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        log.info("Login success user id={}", user.getId());
        return issueTokens(user);
    }

    /** Rotate refresh token: validate old, revoke it, issue a new pair. */
    @Transactional
    public IssuedTokens refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            // Defense in depth: if a revoked or expired token is presented, revoke every session for the user.
            refreshTokens.revokeAllByUserId(stored.getUserId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token invalid; please log in again");
        }

        // Rotate: mark old as revoked, mint a fresh pair.
        stored.setRevoked(true);
        refreshTokens.save(stored);

        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        return issueTokens(user);
    }

    /** Revoke the given refresh token (or all sessions for the user if not found). */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        refreshTokens.findByTokenHash(sha256(rawRefreshToken))
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokens.save(rt);
                });
    }

    public AuthResponse toAuthResponse(User user, String accessToken) {
        return new AuthResponse(accessToken, jwtService.getAccessTtlSeconds(), UserSummary.from(user));
    }

    private IssuedTokens issueTokens(User user) {
        String access = jwtService.generateAccessToken(user);
        String refreshRaw = JwtService.generateRefreshTokenValue();
        Instant expiresAt = Instant.now().plus(refreshTtl);
        refreshTokens.save(new RefreshToken(user.getId(), sha256(refreshRaw), expiresAt));
        return new IssuedTokens(user, access, refreshRaw, refreshTtl);
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record IssuedTokens(User user, String accessToken, String refreshTokenRaw, Duration refreshTtl) {}
}
