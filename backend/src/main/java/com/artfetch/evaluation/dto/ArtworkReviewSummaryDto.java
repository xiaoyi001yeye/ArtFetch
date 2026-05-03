package com.artfetch.evaluation.dto;

import com.artfetch.dto.ArtworkDto;

import java.util.List;

public record ArtworkReviewSummaryDto(
        ArtworkDto artwork,
        List<ExpertReviewDto> reviews
) {
}
