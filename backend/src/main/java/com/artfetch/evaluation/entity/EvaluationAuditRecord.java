package com.artfetch.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "evaluation_audit_records", indexes = {
        @Index(name = "idx_evaluation_audit_record_evaluation_id", columnList = "evaluation_id"),
        @Index(name = "idx_evaluation_audit_record_review_id", columnList = "expert_review_id")
})
public class EvaluationAuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Column(name = "expert_review_id")
    private Long expertReviewId;

    @Column(name = "artwork_id")
    private Long artworkId;

    @Column(name = "expert_id")
    private Long expertId;

    @Column(name = "expert_name", length = 100)
    private String expertName;

    @Column(name = "auditor_id")
    private Long auditorId;

    @Column(name = "auditor_name", length = 100)
    private String auditorName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EvaluationAuditResult result;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(length = 50)
    private String action;

    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(name = "next_status", length = 30)
    private String nextStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
