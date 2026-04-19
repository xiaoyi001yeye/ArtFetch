package com.artfetch.dto;

import com.artfetch.entity.FetchFailure;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FetchFailureDto {

    private Long id;
    private String failureType;
    private int pageNumber;
    private String externalId;
    private String requestUrl;
    private String sourceUrl;
    private String errorType;
    private String errorMessage;
    private int failureCount;
    private boolean resolved;
    private LocalDateTime firstOccurredAt;
    private LocalDateTime lastOccurredAt;
    private LocalDateTime lastRetriedAt;
    private LocalDateTime resolvedAt;

    public static FetchFailureDto from(FetchFailure failure) {
        FetchFailureDto dto = new FetchFailureDto();
        dto.setId(failure.getId());
        dto.setFailureType(failure.getFailureType().name());
        dto.setPageNumber(failure.getPageNumber());
        dto.setExternalId(failure.getExternalId());
        dto.setRequestUrl(failure.getRequestUrl());
        dto.setSourceUrl(failure.getSourceUrl());
        dto.setErrorType(failure.getErrorType());
        dto.setErrorMessage(failure.getErrorMessage());
        dto.setFailureCount(failure.getFailureCount());
        dto.setResolved(failure.isResolved());
        dto.setFirstOccurredAt(failure.getFirstOccurredAt());
        dto.setLastOccurredAt(failure.getLastOccurredAt());
        dto.setLastRetriedAt(failure.getLastRetriedAt());
        dto.setResolvedAt(failure.getResolvedAt());
        return dto;
    }
}
