package com.artfetch.evaluation.dto;

import com.artfetch.evaluation.entity.AutoEvaluationDataset;

import java.time.LocalDateTime;

public record AutoEvaluationDatasetDto(
        Long id,
        String name,
        Long sourceEvaluationId,
        String sourceEvaluationName,
        Long templateId,
        String templateCode,
        String aggregationStrategy,
        Long selectedExpertId,
        String selectedExpertName,
        String status,
        int selectedCount,
        int sampleCount,
        int skippedCount,
        int excludedByUserCount,
        long estimatedSelectedImageSize,
        Long zipFileSize,
        String zipSha256,
        String errorMessage,
        Long createdBy,
        String createdByName,
        LocalDateTime generatedAt,
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AutoEvaluationDatasetDto from(AutoEvaluationDataset dataset) {
        return new AutoEvaluationDatasetDto(
                dataset.getId(),
                dataset.getName(),
                dataset.getSourceEvaluationId(),
                dataset.getSourceEvaluationName(),
                dataset.getTemplateId(),
                dataset.getTemplateCode(),
                dataset.getAggregationStrategy().name(),
                dataset.getSelectedExpertId(),
                dataset.getSelectedExpertName(),
                dataset.getStatus().name(),
                dataset.getSelectedCount(),
                dataset.getSampleCount(),
                dataset.getSkippedCount(),
                dataset.getExcludedByUserCount(),
                dataset.getEstimatedSelectedImageSize(),
                dataset.getZipFileSize(),
                dataset.getZipSha256(),
                dataset.getErrorMessage(),
                dataset.getCreatedBy(),
                dataset.getCreatedByName(),
                dataset.getGeneratedAt(),
                dataset.getArchivedAt(),
                dataset.getCreatedAt(),
                dataset.getUpdatedAt()
        );
    }
}
