package com.artfetch.evaluation.repository;

import com.artfetch.evaluation.entity.EvaluationAuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationAuditRecordRepository extends JpaRepository<EvaluationAuditRecord, Long> {
    List<EvaluationAuditRecord> findByEvaluationIdOrderByCreatedAtDescIdDesc(Long evaluationId);
    boolean existsByEvaluationId(Long evaluationId);
}
