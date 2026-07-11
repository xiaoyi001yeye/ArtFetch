package com.artfetch.evaluation.dto;

import com.artfetch.evaluation.entity.EvaluationMetricTemplateItem;
import com.artfetch.evaluation.entity.EvaluationProjectMetric;

public record MetricConfigDto(
        Long id,
        Long sourceMetricDefinitionId,
        Long sourceTemplateId,
        Integer sourceVersion,
        String code,
        String exportField,
        String name,
        String description,
        String category,
        String scoreType,
        Double minScore,
        Double maxScore,
        Double scoreStep,
        Double weight,
        boolean required,
        String inputComponent,
        String optionValues,
        String scoringGuide,
        String scoringRubric,
        int sortOrder
) {
    public static MetricConfigDto from(EvaluationProjectMetric metric) {
        return new MetricConfigDto(
                metric.getId(),
                metric.getSourceMetricDefinitionId(),
                metric.getSourceTemplateId(),
                metric.getSourceVersion(),
                metric.getCode(),
                metric.getExportField(),
                metric.getName(),
                metric.getDescription(),
                metric.getCategory(),
                metric.getScoreType(),
                metric.getMinScore(),
                metric.getMaxScore(),
                metric.getScoreStep(),
                metric.getWeight(),
                metric.isRequired(),
                metric.getInputComponent(),
                metric.getOptionValues(),
                metric.getScoringGuide(),
                metric.getScoringRubric(),
                metric.getSortOrder()
        );
    }

    public static MetricConfigDto fromTemplateItem(EvaluationMetricTemplateItem item) {
        return new MetricConfigDto(
                item.getId(),
                item.getMetricDefinitionId(),
                item.getTemplateId(),
                item.getMetricDefinitionVersion(),
                item.getCodeSnapshot(),
                item.getExportFieldSnapshot(),
                item.getNameSnapshot(),
                item.getDescriptionSnapshot(),
                item.getCategorySnapshot(),
                item.getScoreType(),
                item.getMinScore(),
                item.getMaxScore(),
                item.getScoreStep(),
                item.getWeight(),
                item.isRequired(),
                item.getInputComponent(),
                item.getOptionValues(),
                item.getScoringGuide(),
                item.getScoringRubric(),
                item.getSortOrder()
        );
    }
}
