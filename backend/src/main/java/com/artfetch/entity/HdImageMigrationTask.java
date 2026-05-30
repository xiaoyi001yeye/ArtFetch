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
@Table(name = "hd_image_migration_tasks", indexes = {
        @Index(name = "idx_hd_image_migration_tasks_status", columnList = "status"),
        @Index(name = "idx_hd_image_migration_tasks_target_task", columnList = "target_task_id")
})
public class HdImageMigrationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "config_id", nullable = false)
    private Long configId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private ScopeType scopeType;

    @Column(name = "target_task_id")
    private Long targetTaskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "total_count")
    private int totalCount = 0;

    @Column(name = "processed_count")
    private int processedCount = 0;

    @Column(name = "success_count")
    private int successCount = 0;

    @Column(name = "skipped_count")
    private int skippedCount = 0;

    @Column(name = "failed_count")
    private int failedCount = 0;

    @Column(name = "current_artwork_id")
    private Long currentArtworkId;

    @Column(name = "upload_concurrency")
    private int uploadConcurrency = 4;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum Mode {
        FULL,
        INCREMENTAL,
        RETRY_FAILED
    }

    public enum ScopeType {
        ALL,
        SEARCH_TASK
    }

    public enum Status {
        PENDING,
        RUNNING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
