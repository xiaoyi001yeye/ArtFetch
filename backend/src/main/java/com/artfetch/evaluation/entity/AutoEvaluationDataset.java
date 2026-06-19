package com.artfetch.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "auto_evaluation_datasets", indexes = {
        @Index(name = "idx_auto_eval_dataset_status", columnList = "status"),
        @Index(name = "idx_auto_eval_dataset_source_eval", columnList = "source_evaluation_id"),
        @Index(name = "idx_auto_eval_dataset_created_at", columnList = "created_at")
})
public class AutoEvaluationDataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "source_evaluation_id", nullable = false)
    private Long sourceEvaluationId;

    @Column(name = "source_evaluation_name", nullable = false, length = 255)
    private String sourceEvaluationName;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "template_code", nullable = false, length = 100)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregation_strategy", nullable = false, length = 50)
    private AutoEvaluationAggregationStrategy aggregationStrategy = AutoEvaluationAggregationStrategy.AVERAGE_ALL_EXPERTS;

    @Column(name = "selected_expert_id")
    private Long selectedExpertId;

    @Column(name = "selected_expert_name", length = 100)
    private String selectedExpertName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AutoEvaluationDatasetStatus status = AutoEvaluationDatasetStatus.DRAFT;

    @Column(name = "selected_count", nullable = false)
    private int selectedCount = 0;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount = 0;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount = 0;

    @Column(name = "excluded_by_user_count", nullable = false)
    private int excludedByUserCount = 0;

    @Column(name = "estimated_selected_image_size", nullable = false)
    private long estimatedSelectedImageSize = 0;

    @Column(name = "storage_path", columnDefinition = "TEXT")
    private String storagePath;

    @Column(name = "zip_file_path", columnDefinition = "TEXT")
    private String zipFilePath;

    @Column(name = "zip_file_size")
    private Long zipFileSize;

    @Column(name = "zip_sha256", length = 64)
    private String zipSha256;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_name", length = 100)
    private String createdByName;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
