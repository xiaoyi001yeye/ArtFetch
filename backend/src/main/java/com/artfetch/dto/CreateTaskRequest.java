package com.artfetch.dto;

import lombok.Data;

@Data
public class CreateTaskRequest {

    private String name;

    private String keyword;

    private String taskType;

    private Long targetTaskId;
}
