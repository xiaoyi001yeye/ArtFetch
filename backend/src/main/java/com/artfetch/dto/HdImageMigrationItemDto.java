package com.artfetch.dto;

import com.artfetch.entity.HdImageMigrationItem;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HdImageMigrationItemDto {
    private Long id;
    private Long migrationTaskId;
    private Long artworkId;
    private String localPath;
    private String objectKey;
    private String status;
    private Long fileSize;
    private Long uploadedSize;
    private String etag;
    private String errorMessage;
    private int attemptCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    public static HdImageMigrationItemDto from(HdImageMigrationItem item) {
        HdImageMigrationItemDto dto = new HdImageMigrationItemDto();
        dto.setId(item.getId());
        dto.setMigrationTaskId(item.getMigrationTaskId());
        dto.setArtworkId(item.getArtworkId());
        dto.setLocalPath(item.getLocalPath());
        dto.setObjectKey(item.getObjectKey());
        dto.setStatus(item.getStatus().name());
        dto.setFileSize(item.getFileSize());
        dto.setUploadedSize(item.getUploadedSize());
        dto.setEtag(item.getEtag());
        dto.setErrorMessage(item.getErrorMessage());
        dto.setAttemptCount(item.getAttemptCount());
        dto.setStartedAt(item.getStartedAt());
        dto.setCompletedAt(item.getCompletedAt());
        dto.setCreatedAt(item.getCreatedAt());
        return dto;
    }
}
