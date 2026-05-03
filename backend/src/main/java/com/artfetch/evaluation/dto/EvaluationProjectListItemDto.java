package com.artfetch.evaluation.dto;

import com.artfetch.evaluation.entity.EvaluationProject;

import java.time.LocalDateTime;
import java.util.List;

public record EvaluationProjectListItemDto(
        Long id,
        String name,
        String description,
        String status,
        int artworkCount,
        int expertCount,
        int expectedReviewCount,
        int completedCount,
        int rejectedReviewCount,
        String auditorName,
        List<String> experts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EvaluationProjectListItemDto from(EvaluationProject project, List<String> experts) {
        return new EvaluationProjectListItemDto(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getArtworkCount(),
                project.getExpertCount(),
                project.getExpectedReviewCount(),
                project.getCompletedCount(),
                project.getRejectedReviewCount(),
                project.getAuditorName(),
                experts,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
