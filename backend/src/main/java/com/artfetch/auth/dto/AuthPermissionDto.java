package com.artfetch.auth.dto;

import com.artfetch.auth.entity.AuthPermission;
import com.artfetch.auth.entity.PermissionResourceType;

public record AuthPermissionDto(
        Long id,
        String code,
        String name,
        String module,
        PermissionResourceType resourceType,
        String description,
        boolean enabled,
        boolean builtIn
) {
    public static AuthPermissionDto from(AuthPermission permission) {
        return new AuthPermissionDto(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getModule(),
                permission.getResourceType(),
                permission.getDescription(),
                permission.isEnabled(),
                permission.isBuiltIn()
        );
    }
}
