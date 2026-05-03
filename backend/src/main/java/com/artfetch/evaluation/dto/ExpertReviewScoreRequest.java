package com.artfetch.evaluation.dto;

import jakarta.validation.constraints.NotNull;

public record ExpertReviewScoreRequest(
        @NotNull Long projectMetricId,
        Double score,
        String optionValue,
        String textValue,
        String comment
) {
}
