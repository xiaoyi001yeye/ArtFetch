package com.artfetch.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record CreateRoleRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        Set<String> permissions
) {
}
