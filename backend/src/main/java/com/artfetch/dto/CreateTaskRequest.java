package com.artfetch.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateTaskRequest {

    private String name;

    private String keyword;

    private List<String> keywords;

    private String taskType;

    private Long targetTaskId;
}
