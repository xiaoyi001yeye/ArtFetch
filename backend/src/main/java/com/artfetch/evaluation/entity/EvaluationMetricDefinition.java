package com.artfetch.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "evaluation_metric_definitions", indexes = {
        @Index(name = "idx_evaluation_metric_definition_enabled", columnList = "enabled"),
        @Index(name = "idx_evaluation_metric_definition_code", columnList = "code")
})
public class EvaluationMetricDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(name = "applicable_artwork_types", columnDefinition = "TEXT")
    private String applicableArtworkTypes;

    @Column(name = "score_type", length = 50)
    private String scoreType;

    @Column(name = "min_score")
    private Double minScore;

    @Column(name = "max_score")
    private Double maxScore;

    @Column(name = "score_step")
    private Double scoreStep;

    @Column(name = "default_weight")
    private Double defaultWeight;

    @Column(nullable = false)
    private boolean required = false;

    @Column(name = "input_component", length = 50)
    private String inputComponent;

    @Column(name = "option_values", columnDefinition = "TEXT")
    private String optionValues;

    @Column(name = "scoring_guide", columnDefinition = "TEXT")
    private String scoringGuide;

    @Column(name = "scoring_rubric", columnDefinition = "TEXT")
    private String scoringRubric;

    @Column(length = 50)
    private String unit;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "created_by", length = 100)
    private String createdBy;

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
