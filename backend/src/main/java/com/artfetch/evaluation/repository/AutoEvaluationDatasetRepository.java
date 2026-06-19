package com.artfetch.evaluation.repository;

import com.artfetch.evaluation.entity.AutoEvaluationDataset;
import com.artfetch.evaluation.entity.AutoEvaluationDatasetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoEvaluationDatasetRepository extends JpaRepository<AutoEvaluationDataset, Long> {
    Page<AutoEvaluationDataset> findByStatusNotOrderByCreatedAtDesc(AutoEvaluationDatasetStatus status, Pageable pageable);
}
