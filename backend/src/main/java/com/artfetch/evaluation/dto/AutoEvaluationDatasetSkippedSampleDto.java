package com.artfetch.evaluation.dto;

import java.util.List;

public record AutoEvaluationDatasetSkippedSampleDto(
        Long artworkId,
        String title,
        String lotNumber,
        String artist,
        List<String> reasons,
        List<Long> expertReviewIds,
        List<String> missingMetricCodes,
        Long selectedExpertId
) {
}
