package com.artfetch.evaluation.dto;

import com.artfetch.evaluation.entity.EvaluationAuditRecord;

import java.time.LocalDateTime;

public record EvaluationAuditRecordDto(
        Long id,
        Long evaluationId,
        Long expertReviewId,
        Long artworkId,
        Long expertId,
        String expertName,
        Long auditorId,
        String auditorName,
        String result,
        String comment,
        String action,
        String previousStatus,
        String nextStatus,
        LocalDateTime createdAt
) {
    public static EvaluationAuditRecordDto from(EvaluationAuditRecord item) {
        return new EvaluationAuditRecordDto(
                item.getId(),
                item.getEvaluationId(),
                item.getExpertReviewId(),
                item.getArtworkId(),
                item.getExpertId(),
                item.getExpertName(),
                item.getAuditorId(),
                item.getAuditorName(),
                item.getResult() == null ? null : item.getResult().name(),
                item.getComment(),
                item.getAction(),
                item.getPreviousStatus(),
                item.getNextStatus(),
                item.getCreatedAt()
        );
    }
}
