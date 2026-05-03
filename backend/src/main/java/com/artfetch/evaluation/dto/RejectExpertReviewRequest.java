package com.artfetch.evaluation.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectExpertReviewRequest(@NotBlank String reason) {
}
