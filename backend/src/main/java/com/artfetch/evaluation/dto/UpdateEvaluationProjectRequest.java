package com.artfetch.evaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateEvaluationProjectRequest(
        @NotBlank String name,
        String description,
        @NotNull Long auditorId,
        @Valid List<CriterionItemDto> criteria,
        List<Long> artworkIds,
        List<Long> expertIds,
        @Valid List<MetricConfigRequest> metrics
) {
}
