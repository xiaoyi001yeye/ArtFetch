package com.artfetch.auth.dto;

import com.artfetch.auth.entity.AuditLog;

import java.time.LocalDateTime;

public record AuditLogDto(
        Long id,
        Long userId,
        String username,
        String action,
        String resourceType,
        String resourceId,
        String description,
        String ipAddress,
        String userAgent,
        boolean success,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static AuditLogDto from(AuditLog log) {
        return new AuditLogDto(
                log.getId(),
                log.getUserId(),
                log.getUsername(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getDescription(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.isSuccess(),
                log.getErrorMessage(),
                log.getCreatedAt()
        );
    }
}
