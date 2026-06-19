package com.artfetch.evaluation.repository;

import com.artfetch.evaluation.entity.AutoEvaluationDatasetArtwork;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AutoEvaluationDatasetArtworkRepository extends JpaRepository<AutoEvaluationDatasetArtwork, Long> {
    List<AutoEvaluationDatasetArtwork> findByDatasetIdOrderByIdAsc(Long datasetId);
    List<AutoEvaluationDatasetArtwork> findByDatasetIdAndArtworkIdIn(Long datasetId, Collection<Long> artworkIds);
    Optional<AutoEvaluationDatasetArtwork> findByDatasetIdAndArtworkId(Long datasetId, Long artworkId);
    boolean existsByDatasetIdAndArtworkId(Long datasetId, Long artworkId);
    long countByDatasetId(Long datasetId);
    void deleteByDatasetIdAndArtworkId(Long datasetId, Long artworkId);
    void deleteByDatasetId(Long datasetId);
}
