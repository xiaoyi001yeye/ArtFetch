package com.artfetch.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRoleRequest(
        @NotBlank String name,
        String description
) {
}
