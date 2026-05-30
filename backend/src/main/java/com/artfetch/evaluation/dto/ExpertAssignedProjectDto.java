package com.artfetch.evaluation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ExpertAssignedProjectDto(
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
        LocalDateTime updatedAt,
        List<ExpertArtworkListItemDto> artworks
) {
}
