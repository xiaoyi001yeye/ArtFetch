package com.artfetch.dto;

import com.artfetch.entity.HdImageMigrationTask;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HdImageMigrationTaskDto {
    private Long id;
    private String name;
    private Long configId;
    private String mode;
    private String scopeType;
    private Long targetTaskId;
    private String status;
    private int totalCount;
    private int processedCount;
    private int successCount;
    private int skippedCount;
    private int failedCount;
    private double progressPercent;
    private Long currentArtworkId;
    private int uploadConcurrency;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    public static HdImageMigrationTaskDto from(HdImageMigrationTask task) {
        HdImageMigrationTaskDto dto = new HdImageMigrationTaskDto();
        dto.setId(task.getId());
        dto.setName(task.getName());
        dto.setConfigId(task.getConfigId());
        dto.setMode(task.getMode().name());
        dto.setScopeType(task.getScopeType().name());
        dto.setTargetTaskId(task.getTargetTaskId());
        dto.setStatus(task.getStatus().name());
        dto.setTotalCount(task.getTotalCount());
        dto.setProcessedCount(task.getProcessedCount());
        dto.setSuccessCount(task.getSuccessCount());
        dto.setSkippedCount(task.getSkippedCount());
        dto.setFailedCount(task.getFailedCount());
        dto.setProgressPercent(task.getTotalCount() <= 0 ? 0D : task.getProcessedCount() * 100D / task.getTotalCount());
        dto.setCurrentArtworkId(task.getCurrentArtworkId());
        dto.setUploadConcurrency(task.getUploadConcurrency());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setStartedAt(task.getStartedAt());
        dto.setCompletedAt(task.getCompletedAt());
        dto.setCreatedAt(task.getCreatedAt());
        return dto;
    }
}
