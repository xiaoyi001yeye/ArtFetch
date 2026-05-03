package com.artfetch.evaluation.dto;

import com.artfetch.evaluation.entity.EvaluationMetricTemplate;

import java.time.LocalDateTime;
import java.util.List;

public record EvaluationMetricTemplateDto(
        Long id,
        String name,
        String description,
        boolean enabled,
        int itemCount,
        List<MetricConfigDto> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EvaluationMetricTemplateDto from(EvaluationMetricTemplate template, List<MetricConfigDto> items) {
        return new EvaluationMetricTemplateDto(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.isEnabled(),
                items.size(),
                items,
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
