package com.artfetch.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "evaluation_metric_template_items", indexes = {
        @Index(name = "idx_evaluation_metric_template_item_template_id", columnList = "template_id")
})
public class EvaluationMetricTemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "metric_definition_id")
    private Long metricDefinitionId;

    @Column(name = "metric_definition_version")
    private Integer metricDefinitionVersion;

    @Column(name = "code_snapshot", length = 100)
    private String codeSnapshot;

    @Column(name = "export_field_snapshot", length = 100)
    private String exportFieldSnapshot;

    @Column(name = "name_snapshot", nullable = false, length = 100)
    private String nameSnapshot;

    @Column(name = "description_snapshot", columnDefinition = "TEXT")
    private String descriptionSnapshot;

    @Column(name = "category_snapshot", length = 100)
    private String categorySnapshot;

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

    @Column(nullable = false)
    private boolean enabled = true;

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
