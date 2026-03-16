package com.artfetch.dto;

import com.artfetch.entity.SearchTask;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskDto {

    private Long id;
    private String name;
    private String keyword;
    private String status;
    private int currentPage;
    private int totalPages;
    private int totalFetched;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long artworkCount;

    public static TaskDto from(SearchTask task) {
        TaskDto dto = new TaskDto();
        dto.setId(task.getId());
        dto.setName(task.getName());
        dto.setKeyword(task.getKeyword());
        dto.setStatus(task.getStatus().name());
        dto.setCurrentPage(task.getCurrentPage());
        dto.setTotalPages(task.getTotalPages());
        dto.setTotalFetched(task.getTotalFetched());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        return dto;
    }
}
