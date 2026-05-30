package com.artfetch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateHdImageMigrationTaskRequest {
    @NotBlank
    private String name;
    @NotNull
    private Long configId;
    @NotBlank
    private String mode;
    @NotBlank
    private String scopeType;
    private Long targetTaskId;
    private Integer uploadConcurrency;
}
