package com.artfetch.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank String displayName,
        String email,
        String phone
) {
}
