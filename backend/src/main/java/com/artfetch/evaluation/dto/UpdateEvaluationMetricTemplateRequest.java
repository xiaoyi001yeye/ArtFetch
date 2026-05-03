package com.artfetch.evaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpdateEvaluationMetricTemplateRequest(
        @NotBlank String name,
        String description,
        Boolean enabled,
        @Valid List<MetricConfigRequest> items
) {
}
