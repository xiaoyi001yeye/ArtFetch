package com.artfetch.auth.dto;

import com.artfetch.auth.entity.AuthUser;

import java.util.Comparator;
import java.util.List;

public record CurrentUserDto(
        Long id,
        String username,
        String displayName,
        List<String> roles,
        List<String> permissions
) {
    public static CurrentUserDto from(AuthUser user) {
        List<String> roles = user.getRoles().stream()
                .filter(role -> role.isEnabled())
                .map(role -> role.getCode())
                .sorted()
                .toList();
        List<String> permissions = user.getRoles().stream()
                .filter(role -> role.isEnabled())
                .flatMap(role -> role.getPermissions().stream())
                .filter(permission -> permission.isEnabled())
                .map(permission -> permission.getCode())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        return new CurrentUserDto(user.getId(), user.getUsername(), user.getDisplayName(), roles, permissions);
    }
}
