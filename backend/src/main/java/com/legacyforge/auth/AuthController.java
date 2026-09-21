package com.legacyforge.auth;

import com.legacyforge.auth.dto.AuthResponse;
import com.legacyforge.auth.dto.LoginRequest;
import com.legacyforge.auth.dto.RegisterRequest;
import com.legacyforge.auth.dto.UserSummary;
import com.legacyforge.auth.repo.UserRepository;
import com.legacyforge.common.ApiException;
import com.legacyforge.security.UserPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "lf_rt";

    private final AuthService authService;
    private final UserRepository users;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthController(
            AuthService authService,
            UserRepository users,
            @Value("${app.cookie.secure:true}") boolean cookieSecure,
            @Value("${app.cookie.samesite:None}") String cookieSameSite
    ) {
        this.authService = authService;
        this.users = users;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest req,
            HttpServletResponse res
    ) {
        AuthService.IssuedTokens tokens = authService.register(req);
        setRefreshCookie(res, tokens.refreshTokenRaw(), tokens.refreshTtl());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.toAuthResponse(tokens.user(), tokens.accessToken()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletResponse res
    ) {
        AuthService.IssuedTokens tokens = authService.login(req);
        setRefreshCookie(res, tokens.refreshTokenRaw(), tokens.refreshTtl());
        return ResponseEntity.ok(authService.toAuthResponse(tokens.user(), tokens.accessToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest req,
            HttpServletResponse res
    ) {
        String raw = readRefreshCookie(req);
        if (raw == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "No refresh token");
        AuthService.IssuedTokens tokens = authService.refresh(raw);
        setRefreshCookie(res, tokens.refreshTokenRaw(), tokens.refreshTtl());
        return ResponseEntity.ok(authService.toAuthResponse(tokens.user(), tokens.accessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res) {
        String raw = readRefreshCookie(req);
        authService.logout(raw);
        clearRefreshCookie(res);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/whoami")
    public ResponseEntity<UserSummary> whoami(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        return users.findById(principal.getId())
                .map(u -> ResponseEntity.ok(UserSummary.from(u)))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    // --- cookie helpers ---

    private void setRefreshCookie(HttpServletResponse res, String value, Duration ttl) {
        // Use Set-Cookie header directly so we can specify SameSite (Servlet Cookie has no SameSite API).
        StringBuilder sb = new StringBuilder();
        sb.append(REFRESH_COOKIE).append("=").append(value);
        sb.append("; Path=/api/auth");
        sb.append("; Max-Age=").append(ttl.getSeconds());
        sb.append("; HttpOnly");
        if (cookieSecure) sb.append("; Secure");
        sb.append("; SameSite=").append(cookieSameSite);
        res.addHeader("Set-Cookie", sb.toString());
    }

    private void clearRefreshCookie(HttpServletResponse res) {
        StringBuilder sb = new StringBuilder();
        sb.append(REFRESH_COOKIE).append("=");
        sb.append("; Path=/api/auth");
        sb.append("; Max-Age=0");
        sb.append("; HttpOnly");
        if (cookieSecure) sb.append("; Secure");
        sb.append("; SameSite=").append(cookieSameSite);
        res.addHeader("Set-Cookie", sb.toString());
    }

    private String readRefreshCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> REFRESH_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
