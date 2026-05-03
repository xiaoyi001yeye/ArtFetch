package com.artfetch.evaluation.repository;

import com.artfetch.evaluation.entity.EvaluationArtwork;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EvaluationArtworkRepository extends JpaRepository<EvaluationArtwork, Long> {
    List<EvaluationArtwork> findByEvaluationIdOrderByIdAsc(Long evaluationId);
    List<EvaluationArtwork> findByEvaluationIdAndArtworkIdInOrderByIdAsc(Long evaluationId, Collection<Long> artworkIds);
    Optional<EvaluationArtwork> findByEvaluationIdAndArtworkId(Long evaluationId, Long artworkId);
    void deleteByEvaluationId(Long evaluationId);
}
