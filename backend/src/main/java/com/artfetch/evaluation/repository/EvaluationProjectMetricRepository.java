package com.artfetch.evaluation.repository;

import com.artfetch.evaluation.entity.EvaluationProjectMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EvaluationProjectMetricRepository extends JpaRepository<EvaluationProjectMetric, Long> {
    List<EvaluationProjectMetric> findByEvaluationIdOrderBySortOrderAscIdAsc(Long evaluationId);
    void deleteByEvaluationId(Long evaluationId);
    List<EvaluationProjectMetric> findByIdIn(Collection<Long> ids);
}
