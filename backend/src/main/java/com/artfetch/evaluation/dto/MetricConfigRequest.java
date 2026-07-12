package com.artfetch.evaluation.dto;

import jakarta.validation.constraints.NotBlank;

public record MetricConfigRequest(
        Long id,
        Long sourceMetricDefinitionId,
        Long sourceTemplateId,
        Integer sourceVersion,
        @NotBlank String code,
        @NotBlank String exportField,
        @NotBlank String name,
        String description,
        String category,
        String scoreType,
        Double minScore,
        Double maxScore,
        Double scoreStep,
        Double weight,
        Boolean required,
        String inputComponent,
        String optionValues,
        String scoringGuide,
        String scoringRubric,
        Integer sortOrder
) {
}
