package com.artfetch.evaluation.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateEvaluationMetricDefinitionRequest(
        @NotBlank String name,
        String description,
        String category,
        String applicableArtworkTypes,
        String scoreType,
        Double minScore,
        Double maxScore,
        Double scoreStep,
        Double defaultWeight,
        Boolean required,
        String inputComponent,
        String optionValues,
        String scoringGuide,
        String scoringRubric,
        String unit,
        String tags,
        Boolean enabled,
        Integer sortOrder
) {
}
