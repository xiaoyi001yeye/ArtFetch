package com.artfetch.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAutoEvaluationDatasetRequest(
        @NotBlank String name,
        @NotNull Long sourceEvaluationId,
        String aggregationStrategy,
        Long selectedExpertId
) {
}
