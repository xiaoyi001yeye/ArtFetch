package com.artfetch.auth.dto;

import com.artfetch.auth.entity.AuthRole;

import java.time.LocalDateTime;
import java.util.List;

public record AuthRoleDto(
        Long id,
        String code,
        String name,
        String description,
        boolean enabled,
        boolean builtIn,
        List<String> permissions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AuthRoleDto from(AuthRole role) {
        return new AuthRoleDto(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isEnabled(),
                role.isBuiltIn(),
                role.getPermissions().stream().map(permission -> permission.getCode()).sorted().toList(),
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }
}
