package com.artfetch.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "expert_reviews", indexes = {
        @Index(name = "idx_expert_review_evaluation_id", columnList = "evaluation_id"),
        @Index(name = "idx_expert_review_expert_id", columnList = "expert_id"),
        @Index(name = "uk_expert_review_unique", columnList = "evaluation_id,artwork_id,expert_id", unique = true)
})
public class ExpertReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Column(name = "artwork_id", nullable = false)
    private Long artworkId;

    @Column(name = "expert_id", nullable = false)
    private Long expertId;

    @Column(name = "expert_name", nullable = false, length = 100)
    private String expertName;

    @Column(name = "final_estimate", columnDefinition = "TEXT")
    private String finalEstimate;

    @Column(name = "final_estimate_amount", precision = 19, scale = 2)
    private BigDecimal finalEstimateAmount;

    @Column(name = "final_estimate_currency", length = 20)
    private String finalEstimateCurrency;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpertReviewStatus status = ExpertReviewStatus.NOT_STARTED;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "resubmitted_at")
    private LocalDateTime resubmittedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

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
