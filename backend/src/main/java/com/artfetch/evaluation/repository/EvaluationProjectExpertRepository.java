package com.artfetch.evaluation.repository;

import com.artfetch.evaluation.entity.EvaluationProjectExpert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EvaluationProjectExpertRepository extends JpaRepository<EvaluationProjectExpert, Long> {
    List<EvaluationProjectExpert> findByEvaluationIdOrderByExpertNameAscIdAsc(Long evaluationId);
    Optional<EvaluationProjectExpert> findByEvaluationIdAndExpertId(Long evaluationId, Long expertId);
    List<EvaluationProjectExpert> findByExpertIdOrderByAssignedAtDesc(Long expertId);
    void deleteByEvaluationId(Long evaluationId);
    List<EvaluationProjectExpert> findByEvaluationIdInAndExpertId(Collection<Long> evaluationIds, Long expertId);
}
