package com.artfetch.evaluation.dto;

import com.artfetch.evaluation.entity.EvaluationProject;

import java.time.LocalDateTime;
import java.util.List;

public record EvaluationProjectDto(
        Long id,
        String name,
        String description,
        String status,
        Long auditorId,
        String auditorName,
        List<CriterionItemDto> criteria,
        int artworkCount,
        int expertCount,
        int expectedReviewCount,
        int completedCount,
        int rejectedReviewCount,
        LocalDateTime submittedForReviewAt,
        LocalDateTime reviewedAt,
        String auditResult,
        String auditComment,
        LocalDateTime configLockedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        List<EvaluationProjectExpertDto> experts,
        List<EvaluationArtworkItemDto> artworks,
        List<MetricConfigDto> metrics
) {
    public static EvaluationProjectDto from(
            EvaluationProject project,
            List<CriterionItemDto> criteria,
            List<EvaluationProjectExpertDto> experts,
            List<EvaluationArtworkItemDto> artworks,
            List<MetricConfigDto> metrics
    ) {
        return new EvaluationProjectDto(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getAuditorId(),
                project.getAuditorName(),
                criteria,
                project.getArtworkCount(),
                project.getExpertCount(),
                project.getExpectedReviewCount(),
                project.getCompletedCount(),
                project.getRejectedReviewCount(),
                project.getSubmittedForReviewAt(),
                project.getReviewedAt(),
                project.getAuditResult() == null ? null : project.getAuditResult().name(),
                project.getAuditComment(),
                project.getConfigLockedAt(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getCompletedAt(),
                experts,
                artworks,
                metrics
        );
    }
}
