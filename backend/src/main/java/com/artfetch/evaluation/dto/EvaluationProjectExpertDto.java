package com.artfetch.evaluation.dto;

import com.artfetch.evaluation.entity.EvaluationProjectExpert;

public record EvaluationProjectExpertDto(
        Long id,
        Long expertId,
        String expertName,
        String status,
        int completedCount,
        int totalCount,
        int rejectedCount
) {
    public static EvaluationProjectExpertDto from(EvaluationProjectExpert item) {
        return new EvaluationProjectExpertDto(
                item.getId(),
                item.getExpertId(),
                item.getExpertName(),
                item.getStatus(),
                item.getCompletedCount(),
                item.getTotalCount(),
                item.getRejectedCount()
        );
    }
}
