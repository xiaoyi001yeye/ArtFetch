package com.artfetch.evaluation.service;

import com.artfetch.auth.service.AuditLogService;
import com.artfetch.auth.service.CurrentUserService;
import com.artfetch.evaluation.dto.AuditCommentRequest;
import com.artfetch.evaluation.dto.EvaluationAuditRecordDto;
import com.artfetch.evaluation.dto.EvaluationProjectDto;
import com.artfetch.evaluation.entity.*;
import com.artfetch.evaluation.repository.EvaluationAuditRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationAuditService {

    private final EvaluationProjectService projectService;
    private final EvaluationAccessService accessService;
    private final ExpertReviewService expertReviewService;
    private final EvaluationAuditRecordRepository auditRecordRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<EvaluationAuditRecordDto> listRecords(Long evaluationId) {
        EvaluationProject project = projectService.requireProject(evaluationId);
        accessService.requireProjectView(project);
        return auditRecordRepository.findByEvaluationIdOrderByCreatedAtDescIdDesc(evaluationId).stream()
                .map(EvaluationAuditRecordDto::from)
                .toList();
    }

    @Transactional
    public EvaluationProjectDto approve(Long evaluationId, AuditCommentRequest request) {
        EvaluationProject project = projectService.requireProject(evaluationId);
        accessService.requireAuditApprove(project);
        if (project.getStatus() != EvaluationProjectStatus.IN_REVIEW) {
            throw new IllegalStateException("只有审核中的项目才能审核通过");
        }
        project.setStatus(EvaluationProjectStatus.COMPLETED);
        project.setReviewedAt(LocalDateTime.now());
        project.setCompletedAt(LocalDateTime.now());
        project.setAuditResult(EvaluationAuditResult.APPROVED);
        project.setAuditComment(request == null ? null : request.comment());
        createAuditRecord(project, null, EvaluationAuditResult.APPROVED, request == null ? null : request.comment(),
                "APPROVE_PROJECT", EvaluationProjectStatus.IN_REVIEW.name(), EvaluationProjectStatus.COMPLETED.name());
        projectService.recalculateProjectState(evaluationId);
        auditLogService.recordSuccess(
                "evaluation.audit.approve",
                "EVALUATION",
                String.valueOf(project.getId()),
                "审核通过评估项目 " + project.getName()
        );
        return projectService.get(evaluationId);
    }

    @Transactional
    public EvaluationProjectDto rejectReview(Long evaluationId, Long reviewId, String reason) {
        EvaluationProject project = projectService.requireProject(evaluationId);
        accessService.requireAuditReject(project);
        if (project.getStatus() != EvaluationProjectStatus.IN_REVIEW) {
            throw new IllegalStateException("只有审核中的项目才能驳回评估");
        }
        ExpertReview review = expertReviewService.requireReview(reviewId);
        if (!review.getEvaluationId().equals(evaluationId)) {
            throw new IllegalArgumentException("评估记录与项目不匹配");
        }
        String previous = review.getStatus().name();
        expertReviewService.markReviewRejected(review, reason);
        project = projectService.requireProject(evaluationId);
        project.setStatus(EvaluationProjectStatus.REVIEW_REJECTED);
        project.setAuditResult(EvaluationAuditResult.REJECTED);
        project.setAuditComment(reason);
        createAuditRecord(project, review, EvaluationAuditResult.REJECTED, reason,
                "REJECT_REVIEW", previous, ExpertReviewStatus.REVIEW_REJECTED.name());
        projectService.recalculateProjectState(evaluationId);
        auditLogService.recordSuccess(
                "evaluation.audit.reject-review",
                "EVALUATION",
                String.valueOf(project.getId()),
                "驳回专家评估 " + review.getExpertName() + " / 项目 " + project.getName()
        );
        return projectService.get(evaluationId);
    }

    private void createAuditRecord(EvaluationProject project,
                                   ExpertReview review,
                                   EvaluationAuditResult result,
                                   String comment,
                                   String action,
                                   String previousStatus,
                                   String nextStatus) {
        var currentUser = currentUserService.currentUserEntity();
        EvaluationAuditRecord record = new EvaluationAuditRecord();
        record.setEvaluationId(project.getId());
        if (review != null) {
            record.setExpertReviewId(review.getId());
            record.setArtworkId(review.getArtworkId());
            record.setExpertId(review.getExpertId());
            record.setExpertName(review.getExpertName());
        }
        record.setAuditorId(currentUser.getId());
        record.setAuditorName(currentUser.getDisplayName());
        record.setResult(result);
        record.setComment(comment);
        record.setAction(action);
        record.setPreviousStatus(previousStatus);
        record.setNextStatus(nextStatus);
        auditRecordRepository.save(record);
    }
}
