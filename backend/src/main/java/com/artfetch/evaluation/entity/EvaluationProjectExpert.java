package com.artfetch.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "evaluation_project_experts", indexes = {
        @Index(name = "idx_evaluation_project_expert_evaluation_id", columnList = "evaluation_id"),
        @Index(name = "idx_evaluation_project_expert_expert_id", columnList = "expert_id"),
        @Index(name = "uk_evaluation_project_expert_unique", columnList = "evaluation_id,expert_id", unique = true)
})
public class EvaluationProjectExpert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Column(name = "expert_id", nullable = false)
    private Long expertId;

    @Column(name = "expert_name", nullable = false, length = 100)
    private String expertName;

    @Column(length = 30)
    private String status;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "completed_count", nullable = false)
    private int completedCount = 0;

    @Column(name = "total_count", nullable = false)
    private int totalCount = 0;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (assignedAt == null) {
            assignedAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
