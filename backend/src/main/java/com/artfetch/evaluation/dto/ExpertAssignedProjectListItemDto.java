package com.artfetch.evaluation.dto;

import java.time.LocalDateTime;

public record ExpertAssignedProjectListItemDto(
        Long evaluationId,
        String name,
        String description,
        String evaluationStatus,
        int totalCount,
        int submittedCount,
        int pendingCount,
        int rejectedCount,
        int draftCount,
        Long nextArtworkId,
        LocalDateTime updatedAt
) {
}
