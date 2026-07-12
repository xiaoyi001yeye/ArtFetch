package com.artfetch.evaluation.repository;

import com.artfetch.evaluation.entity.EvaluationMetricDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationMetricDefinitionRepository extends JpaRepository<EvaluationMetricDefinition, Long> {
    Optional<EvaluationMetricDefinition> findByCode(String code);
    Optional<EvaluationMetricDefinition> findByExportField(String exportField);
    Page<EvaluationMetricDefinition> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCaseOrderBySortOrderAscIdAsc(
            String name, String code, Pageable pageable);
    List<EvaluationMetricDefinition> findAllByEnabledTrueOrderBySortOrderAscIdAsc();
}
