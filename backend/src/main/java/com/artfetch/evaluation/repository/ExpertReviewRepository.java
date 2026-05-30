package com.artfetch.evaluation.repository;

import com.artfetch.evaluation.entity.ExpertReview;
import com.artfetch.evaluation.entity.ExpertReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExpertReviewRepository extends JpaRepository<ExpertReview, Long> {
    Optional<ExpertReview> findByEvaluationIdAndArtworkIdAndExpertId(Long evaluationId, Long artworkId, Long expertId);
    List<ExpertReview> findByEvaluationIdOrderByArtworkIdAscExpertNameAsc(Long evaluationId);
    List<ExpertReview> findByEvaluationIdAndArtworkIdOrderByExpertNameAsc(Long evaluationId, Long artworkId);
    List<ExpertReview> findByEvaluationIdAndExpertIdOrderByArtworkIdAsc(Long evaluationId, Long expertId);
    List<ExpertReview> findByArtworkIdAndExpertId(Long artworkId, Long expertId);
    List<ExpertReview> findByEvaluationIdAndStatusIn(Long evaluationId, Collection<ExpertReviewStatus> statuses);
    long countByEvaluationId(Long evaluationId);
    long countByEvaluationIdAndStatusIn(Long evaluationId, Collection<ExpertReviewStatus> statuses);
    void deleteByEvaluationId(Long evaluationId);
}
