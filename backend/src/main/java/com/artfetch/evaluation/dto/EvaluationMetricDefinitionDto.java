package com.artfetch.evaluation.dto;

import com.artfetch.evaluation.entity.EvaluationMetricDefinition;

import java.time.LocalDateTime;

public record EvaluationMetricDefinitionDto(
        Long id,
        String code,
        String exportField,
        String name,
        String description,
        String category,
        String applicableArtworkTypes,
        String scoreType,
        Double minScore,
        Double maxScore,
        Double scoreStep,
        Double defaultWeight,
        boolean required,
        String inputComponent,
        String optionValues,
        String scoringGuide,
        String scoringRubric,
        String unit,
        String tags,
        boolean enabled,
        boolean builtIn,
        int sortOrder,
        int version,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EvaluationMetricDefinitionDto from(EvaluationMetricDefinition item) {
        return new EvaluationMetricDefinitionDto(
                item.getId(),
                item.getCode(),
                item.getExportField(),
                item.getName(),
                item.getDescription(),
                item.getCategory(),
                item.getApplicableArtworkTypes(),
                item.getScoreType(),
                item.getMinScore(),
                item.getMaxScore(),
                item.getScoreStep(),
                item.getDefaultWeight(),
                item.isRequired(),
                item.getInputComponent(),
                item.getOptionValues(),
                item.getScoringGuide(),
                item.getScoringRubric(),
                item.getUnit(),
                item.getTags(),
                item.isEnabled(),
                item.isBuiltIn(),
                item.getSortOrder(),
                item.getVersion(),
                item.getCreatedBy(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
