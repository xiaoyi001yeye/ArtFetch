package com.artfetch.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "evaluation_artworks", indexes = {
        @Index(name = "idx_evaluation_artwork_evaluation_id", columnList = "evaluation_id"),
        @Index(name = "uk_evaluation_artwork_unique", columnList = "evaluation_id,artwork_id", unique = true)
})
public class EvaluationArtwork {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Column(name = "artwork_id", nullable = false)
    private Long artworkId;

    @Column(length = 30)
    private String status;

    @Column(name = "review_page_generated", nullable = false)
    private boolean reviewPageGenerated = false;

    @Column(name = "review_page_generated_at")
    private LocalDateTime reviewPageGeneratedAt;

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
