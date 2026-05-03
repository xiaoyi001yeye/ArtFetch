package com.artfetch.dto;

import com.artfetch.entity.SearchTask;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskDto {

    private Long id;
    private String name;
    private String keyword;
    private String taskType;
    private Long parentTaskId;
    private String parentTaskName;
    private Long targetTaskId;
    private String targetTaskName;
    private String status;
    private int currentPage;
    private int totalPages;
    private int totalFetched;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long artworkCount;
    private long pendingFailureCount;
    private int detailFetchConcurrency;
    private long detailRequestCount;
    private long detailSuccessCount;
    private long detailFailureCount;
    private long avgDetailLatencyMs;
    private long p95DetailLatencyMs;
    private long maxDetailLatencyMs;
    private long lastPageDurationMs;
    private double lastPageItemsPerMinute;
    private double detailFailureRate;
    private String concurrencyAdvice;
    private Long estimatedRemainingMs;

    public static TaskDto from(SearchTask task) {
        TaskDto dto = new TaskDto();
        dto.setId(task.getId());
        dto.setName(task.getName());
        dto.setKeyword(task.getKeyword());
        dto.setTaskType(task.getTaskType() == null ? SearchTask.TaskType.SEARCH.name() : task.getTaskType().name());
        dto.setParentTaskId(task.getParentTaskId());
        dto.setTargetTaskId(task.getTargetTaskId());
        dto.setStatus(task.getStatus().name());
        dto.setCurrentPage(task.getCurrentPage());
        dto.setTotalPages(task.getTotalPages());
        dto.setTotalFetched(task.getTotalFetched());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        dto.setDetailFetchConcurrency(task.getDetailFetchConcurrency());
        dto.setDetailRequestCount(task.getDetailRequestCount());
        dto.setDetailSuccessCount(task.getDetailSuccessCount());
        dto.setDetailFailureCount(task.getDetailFailureCount());
        dto.setAvgDetailLatencyMs(task.getAvgDetailLatencyMs());
        dto.setP95DetailLatencyMs(task.getP95DetailLatencyMs());
        dto.setMaxDetailLatencyMs(task.getMaxDetailLatencyMs());
        dto.setLastPageDurationMs(task.getLastPageDurationMs());
        dto.setLastPageItemsPerMinute(task.getLastPageItemsPerMinute());
        dto.setDetailFailureRate(task.getDetailFailureRate());
        dto.setConcurrencyAdvice(task.getConcurrencyAdvice());
        return dto;
    }
}
