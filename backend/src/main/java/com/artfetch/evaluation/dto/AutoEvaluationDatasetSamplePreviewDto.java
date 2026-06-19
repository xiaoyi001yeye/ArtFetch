package com.artfetch.evaluation.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AutoEvaluationDatasetSamplePreviewDto(
        Long artworkId,
        String title,
        String imagePath,
        String imageSourceType,
        long estimatedImageSize,
        List<Long> expertReviewIds,
        BigDecimal finalEstimateAmountCny,
        Map<String, Double> features
) {
}
