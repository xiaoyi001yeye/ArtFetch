package com.artfetch.evaluation.dto;

import jakarta.validation.Valid;

import java.util.List;

public record SaveExpertReviewRequest(
        String finalEstimate,
        String finalEstimateCurrency,
        String comment,
        @Valid List<ExpertReviewScoreRequest> scores
) {
}
