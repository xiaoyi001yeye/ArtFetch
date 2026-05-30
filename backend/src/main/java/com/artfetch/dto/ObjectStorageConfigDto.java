package com.artfetch.dto;

import com.artfetch.entity.ObjectStorageConfig;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ObjectStorageConfigDto {
    private Long id;
    private String name;
    private String provider;
    private String endpoint;
    private String region;
    private String bucket;
    private String pathPrefix;
    private String accessKey;
    private String secretKey;
    private String accessKeyMasked;
    private boolean secretConfigured;
    private String publicBaseUrl;
    private String sdkMode;
    private String networkType;
    private boolean enabled;
    private boolean uploadEnabled;
    private boolean migrateEnabled;
    private String lastTestStatus;
    private String lastTestMessage;
    private LocalDateTime lastTestAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ObjectStorageConfigDto from(ObjectStorageConfig config) {
        ObjectStorageConfigDto dto = new ObjectStorageConfigDto();
        dto.setId(config.getId());
        dto.setName(config.getName());
        dto.setProvider(config.getProvider().name());
        dto.setEndpoint(config.getEndpoint());
        dto.setRegion(config.getRegion());
        dto.setBucket(config.getBucket());
        dto.setPathPrefix(config.getPathPrefix());
        dto.setAccessKeyMasked(mask(config.getAccessKey()));
        dto.setSecretConfigured(config.getSecretKeyEncrypted() != null && !config.getSecretKeyEncrypted().isBlank());
        dto.setPublicBaseUrl(config.getPublicBaseUrl());
        dto.setSdkMode(config.getSdkMode().name());
        dto.setNetworkType(config.getNetworkType().name());
        dto.setEnabled(config.isEnabled());
        dto.setUploadEnabled(config.isUploadEnabled());
        dto.setMigrateEnabled(config.isMigrateEnabled());
        dto.setLastTestStatus(config.getLastTestStatus());
        dto.setLastTestMessage(config.getLastTestMessage());
        dto.setLastTestAt(config.getLastTestAt());
        dto.setCreatedAt(config.getCreatedAt());
        dto.setUpdatedAt(config.getUpdatedAt());
        return dto;
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 8) {
            return value.charAt(0) + "****" + value.charAt(value.length() - 1);
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
