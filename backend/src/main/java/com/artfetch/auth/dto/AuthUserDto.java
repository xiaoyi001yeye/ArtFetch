package com.artfetch.auth.dto;

import com.artfetch.auth.entity.AuthUser;
import com.artfetch.auth.entity.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AuthUserDto(
        Long id,
        String username,
        String displayName,
        String email,
        String phone,
        UserStatus status,
        List<String> roles,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AuthUserDto from(AuthUser user) {
        return new AuthUserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getRoles().stream().map(role -> role.getCode()).sorted().toList(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
