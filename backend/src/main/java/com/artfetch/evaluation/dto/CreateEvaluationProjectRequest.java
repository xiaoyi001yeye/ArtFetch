package com.artfetch.evaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateEvaluationProjectRequest(
        @NotBlank String name,
        String description,
        @NotNull Long auditorId,
        @Valid List<CriterionItemDto> criteria,
        @NotEmpty List<Long> artworkIds,
        @NotEmpty List<Long> expertIds,
        @Valid @NotEmpty List<MetricConfigRequest> metrics
) {
}
