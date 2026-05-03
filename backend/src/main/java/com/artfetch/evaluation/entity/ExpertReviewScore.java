package com.artfetch.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "expert_review_scores", indexes = {
        @Index(name = "idx_expert_review_score_review_id", columnList = "review_id"),
        @Index(name = "uk_expert_review_score_unique", columnList = "review_id,project_metric_id", unique = true)
})
public class ExpertReviewScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "project_metric_id", nullable = false)
    private Long projectMetricId;

    @Column
    private Double score;

    @Column(name = "option_value", columnDefinition = "TEXT")
    private String optionValue;

    @Column(name = "text_value", columnDefinition = "TEXT")
    private String textValue;

    @Column(columnDefinition = "TEXT")
    private String comment;
}
