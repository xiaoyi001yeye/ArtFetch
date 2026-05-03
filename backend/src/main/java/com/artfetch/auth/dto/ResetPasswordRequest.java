package com.artfetch.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(@NotBlank String newPassword) {
}
