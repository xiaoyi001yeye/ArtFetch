package com.artfetch.evaluation.repository;

import com.artfetch.evaluation.entity.EvaluationMetricTemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationMetricTemplateItemRepository extends JpaRepository<EvaluationMetricTemplateItem, Long> {
    List<EvaluationMetricTemplateItem> findByTemplateIdOrderBySortOrderAscIdAsc(Long templateId);
    void deleteByTemplateId(Long templateId);
}
