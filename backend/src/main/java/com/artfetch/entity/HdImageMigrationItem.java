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
@Table(name = "hd_image_migration_items", indexes = {
        @Index(name = "idx_hd_image_migration_items_status", columnList = "migration_task_id,status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_hd_image_migration_items_task_artwork", columnNames = {"migration_task_id", "artwork_id"})
})
public class HdImageMigrationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "migration_task_id", nullable = false)
    private Long migrationTaskId;

    @Column(name = "artwork_id", nullable = false)
    private Long artworkId;

    @Column(name = "local_path", columnDefinition = "TEXT")
    private String localPath;

    @Column(name = "object_key", columnDefinition = "TEXT")
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_size")
    private Long uploadedSize;

    private String etag;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "attempt_count")
    private int attemptCount = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum Status {
        PENDING,
        UPLOADING,
        MIGRATED,
        SKIPPED,
        FAILED
    }
}
