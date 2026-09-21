package com.legacyforge.auth.dto;

/**
 * Returned to the client after a successful login/register/refresh.
 * The refresh token is set as an httpOnly cookie by the controller,
 * so it does not appear here.
 */
public record AuthResponse(
        String accessToken,
        long expiresInSeconds,
        UserSummary user
) {}
