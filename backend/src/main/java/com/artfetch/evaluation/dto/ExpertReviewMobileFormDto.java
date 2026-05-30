package com.artfetch.evaluation.dto;

import java.util.List;

public record ExpertReviewMobileFormDto(
        Long evaluationId,
        String evaluationName,
        String evaluationStatus,
        int artworkIndex,
        int artworkTotal,
        Long previousArtworkId,
        Long nextArtworkId,
        Long nextPendingArtworkId,
        ExpertArtworkDto artwork,
        List<MetricConfigDto> metrics,
        ExpertReviewDto review
) {
}
