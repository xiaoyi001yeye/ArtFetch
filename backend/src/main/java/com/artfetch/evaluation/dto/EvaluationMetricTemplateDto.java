package com.artfetch.evaluation.dto;

import com.artfetch.evaluation.entity.EvaluationMetricTemplate;

import java.time.LocalDateTime;
import java.util.List;

public record EvaluationMetricTemplateDto(
        Long id,
        String code,
        String name,
        String description,
        boolean enabled,
        boolean builtIn,
        int itemCount,
        List<MetricConfigDto> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EvaluationMetricTemplateDto from(EvaluationMetricTemplate template, List<MetricConfigDto> items) {
        return new EvaluationMetricTemplateDto(
                template.getId(),
                template.getCode(),
                template.getName(),
                template.getDescription(),
                template.isEnabled(),
                template.isBuiltIn(),
                items.size(),
                items,
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
