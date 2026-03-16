package com.artfetch.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTaskRequest {

    @NotBlank(message = "任务名称不能为空")
    private String name;

    @NotBlank(message = "检索关键词不能为空")
    private String keyword;
}
