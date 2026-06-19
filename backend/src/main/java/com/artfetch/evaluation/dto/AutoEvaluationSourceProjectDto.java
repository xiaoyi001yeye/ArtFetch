package com.artfetch.evaluation.dto;

import com.artfetch.evaluation.entity.EvaluationProject;

import java.time.LocalDateTime;

public record AutoEvaluationSourceProjectDto(
        Long id,
        String name,
        int artworkCount,
        int expertCount,
        LocalDateTime completedAt
) {
    public static AutoEvaluationSourceProjectDto from(EvaluationProject project) {
        return new AutoEvaluationSourceProjectDto(
                project.getId(),
                project.getName(),
                project.getArtworkCount(),
                project.getExpertCount(),
                project.getCompletedAt()
        );
    }
}
