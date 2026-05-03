package com.artfetch.evaluation.repository;

import com.artfetch.evaluation.entity.EvaluationMetricTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationMetricTemplateRepository extends JpaRepository<EvaluationMetricTemplate, Long> {
    Page<EvaluationMetricTemplate> findAllByOrderByUpdatedAtDesc(Pageable pageable);
}
