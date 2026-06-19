package com.artfetch.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "auto_evaluation_dataset_artworks", indexes = {
        @Index(name = "idx_auto_eval_dataset_artworks_dataset_id", columnList = "dataset_id"),
        @Index(name = "idx_auto_eval_dataset_artworks_artwork_id", columnList = "artwork_id")
})
public class AutoEvaluationDatasetArtwork {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @Column(name = "artwork_id", nullable = false)
    private Long artworkId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
