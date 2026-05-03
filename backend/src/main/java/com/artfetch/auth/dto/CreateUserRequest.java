package com.artfetch.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String displayName,
        String email,
        String phone,
        @NotEmpty Set<String> roles
) {
}
