package com.artfetch.evaluation.dto;

import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;

public record SaveExpertReviewRequest(
        String finalEstimate,
        BigDecimal finalEstimateAmount,
        String finalEstimateCurrency,
        String comment,
        @Valid List<ExpertReviewScoreRequest> scores
) {
}
