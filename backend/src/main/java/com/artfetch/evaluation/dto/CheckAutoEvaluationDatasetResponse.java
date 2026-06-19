package com.artfetch.evaluation.dto;

import java.util.List;

public record CheckAutoEvaluationDatasetResponse(
        Long datasetId,
        int selectedCount,
        int sampleCount,
        int skippedCount,
        long estimatedPackageSize,
        boolean exceedsMobileSoftLimit,
        boolean exceedsMobileHardLimit,
        List<AutoEvaluationDatasetSamplePreviewDto> samples,
        List<AutoEvaluationDatasetSkippedSampleDto> skippedSamples
) {
}
