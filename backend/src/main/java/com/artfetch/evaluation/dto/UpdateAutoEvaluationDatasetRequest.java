package com.artfetch.evaluation.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAutoEvaluationDatasetRequest(
        @NotBlank String name,
        String aggregationStrategy,
        Long selectedExpertId
) {
}
