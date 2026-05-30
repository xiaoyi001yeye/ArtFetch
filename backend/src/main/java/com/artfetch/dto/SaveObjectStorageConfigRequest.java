package com.artfetch.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveObjectStorageConfigRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String endpoint;
    @NotBlank
    private String region;
    @NotBlank
    private String bucket;
    private String pathPrefix;
    private String accessKey;
    private String secretKey;
    private String publicBaseUrl;
    private String networkType = "PUBLIC";
    private boolean uploadEnabled;
    private boolean migrateEnabled;
}
