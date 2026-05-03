package com.artfetch.evaluation.dto;

import com.artfetch.dto.ArtworkDto;

import java.util.List;

public record ExpertReviewFormDto(
        Long evaluationId,
        String evaluationName,
        String evaluationStatus,
        ArtworkDto artwork,
        List<MetricConfigDto> metrics,
        ExpertReviewDto review
) {
}
