package com.artfetch.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "evaluation_projects", indexes = {
        @Index(name = "idx_evaluation_project_status", columnList = "status"),
        @Index(name = "idx_evaluation_project_auditor_id", columnList = "auditor_id"),
        @Index(name = "idx_evaluation_project_deleted_at", columnList = "deleted_at")
})
public class EvaluationProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EvaluationProjectStatus status = EvaluationProjectStatus.PENDING;

    @Column(name = "config_locked_at")
    private LocalDateTime configLockedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "auditor_id")
    private Long auditorId;

    @Column(name = "auditor_name", length = 100)
    private String auditorName;

    @Column(name = "criteria_snapshot", columnDefinition = "TEXT")
    private String criteriaSnapshot;

    @Column(name = "artwork_count", nullable = false)
    private int artworkCount = 0;

    @Column(name = "expert_count", nullable = false)
    private int expertCount = 0;

    @Column(name = "expected_review_count", nullable = false)
    private int expectedReviewCount = 0;

    @Column(name = "completed_count", nullable = false)
    private int completedCount = 0;

    @Column(name = "rejected_review_count", nullable = false)
    private int rejectedReviewCount = 0;

    @Column(name = "submitted_for_review_at")
    private LocalDateTime submittedForReviewAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_result", length = 20)
    private EvaluationAuditResult auditResult;

    @Column(name = "audit_comment", columnDefinition = "TEXT")
    private String auditComment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

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
