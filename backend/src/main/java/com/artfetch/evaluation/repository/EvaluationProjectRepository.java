package com.artfetch.evaluation.repository;

import com.artfetch.evaluation.entity.EvaluationProject;
import com.artfetch.evaluation.entity.EvaluationProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EvaluationProjectRepository extends JpaRepository<EvaluationProject, Long> {
    Page<EvaluationProject> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);
    Page<EvaluationProject> findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(Collection<Long> ids, Pageable pageable);
    List<EvaluationProject> findByStatusInAndDeletedAtIsNull(Collection<EvaluationProjectStatus> statuses);
}
