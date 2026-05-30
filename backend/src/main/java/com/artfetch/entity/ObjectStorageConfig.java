package com.artfetch.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "object_storage_configs")
public class ObjectStorageConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider = Provider.VOLCENGINE_TOS;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String endpoint;

    private String region;

    @Column(nullable = false)
    private String bucket;

    @Column(name = "path_prefix")
    private String pathPrefix;

    @Column(name = "access_key", nullable = false, columnDefinition = "TEXT")
    private String accessKey;

    @Column(name = "secret_key_encrypted", nullable = false, columnDefinition = "TEXT")
    private String secretKeyEncrypted;

    @Column(name = "public_base_url", columnDefinition = "TEXT")
    private String publicBaseUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "sdk_mode", nullable = false)
    private SdkMode sdkMode = SdkMode.VOLCENGINE_TOS_SDK;

    @Enumerated(EnumType.STRING)
    @Column(name = "network_type", nullable = false)
    private NetworkType networkType = NetworkType.PUBLIC;

    private boolean enabled = false;

    @Column(name = "upload_enabled")
    private boolean uploadEnabled = false;

    @Column(name = "migrate_enabled")
    private boolean migrateEnabled = false;

    @Column(name = "last_test_status")
    private String lastTestStatus;

    @Column(name = "last_test_message", columnDefinition = "TEXT")
    private String lastTestMessage;

    @Column(name = "last_test_at")
    private LocalDateTime lastTestAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum Provider {
        VOLCENGINE_TOS
    }

    public enum SdkMode {
        VOLCENGINE_TOS_SDK
    }

    public enum NetworkType {
        PUBLIC,
        INTERNAL
    }
}
