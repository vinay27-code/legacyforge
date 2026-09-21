package com.legacyforge.auth.dto;

import com.legacyforge.auth.entity.User;

import java.util.UUID;

public record UserSummary(UUID id, String email, String role) {
    public static UserSummary from(User u) {
        return new UserSummary(u.getId(), u.getEmail(), u.getRole().name());
    }
}
