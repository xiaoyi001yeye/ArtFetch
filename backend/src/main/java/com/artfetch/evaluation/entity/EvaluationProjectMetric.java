package com.artfetch.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "evaluation_project_metrics", indexes = {
        @Index(name = "idx_evaluation_project_metric_evaluation_id", columnList = "evaluation_id")
})
public class EvaluationProjectMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Column(name = "source_metric_definition_id")
    private Long sourceMetricDefinitionId;

    @Column(name = "source_template_id")
    private Long sourceTemplateId;

    @Column(name = "source_version")
    private Integer sourceVersion;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(name = "export_field", length = 100)
    private String exportField;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(name = "score_type", length = 50)
    private String scoreType;

    @Column(name = "min_score")
    private Double minScore;

    @Column(name = "max_score")
    private Double maxScore;

    @Column(name = "score_step")
    private Double scoreStep;

    @Column
    private Double weight;

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

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

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
