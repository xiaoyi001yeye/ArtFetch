package com.artfetch.evaluation.service;

import com.artfetch.auth.service.CurrentUserService;
import com.artfetch.auth.service.DataScopeService;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.evaluation.entity.EvaluationProject;
import com.artfetch.evaluation.repository.EvaluationProjectExpertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EvaluationAccessService {

    private final CurrentUserService currentUserService;
    private final DataScopeService dataScopeService;
    private final EvaluationProjectExpertRepository evaluationProjectExpertRepository;

    public Long currentUserId() {
        currentUserService.requireEnabledCurrentUser();
        return currentUserService.currentUserId();
    }

    @Transactional(readOnly = true)
    public void requireProjectView(EvaluationProject project) {
        Long currentUserId = currentUserId();
        if (dataScopeService.isAdmin() || dataScopeService.hasPermission(PermissionCodes.EVALUATION_VIEW)) {
            return;
        }
        if (dataScopeService.hasPermission(PermissionCodes.EVALUATION_REVIEW_ASSIGNED_VIEW)
                && evaluationProjectExpertRepository.findByEvaluationIdAndExpertId(project.getId(), currentUserId).isPresent()) {
            return;
        }
        if ((dataScopeService.hasPermission(PermissionCodes.EVALUATION_AUDIT_VIEW)
                || dataScopeService.hasPermission(PermissionCodes.EVALUATION_RESULT_VIEW))
                && project.getAuditorId() != null
                && project.getAuditorId().equals(currentUserId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有访问该评估项目的权限");
    }

    public void requireProjectManage() {
        currentUserService.requireEnabledCurrentUser();
        dataScopeService.requirePermission(PermissionCodes.EVALUATION_UPDATE);
    }

    public void requireProjectCreate() {
        currentUserService.requireEnabledCurrentUser();
        dataScopeService.requirePermission(PermissionCodes.EVALUATION_CREATE);
    }

    public void requireProjectDelete() {
        currentUserService.requireEnabledCurrentUser();
        dataScopeService.requirePermission(PermissionCodes.EVALUATION_DELETE);
    }

    @Transactional(readOnly = true)
    public void requireOwnAssignedProject(Long evaluationId) {
        Long currentUserId = currentUserId();
        dataScopeService.requirePermission(PermissionCodes.EVALUATION_REVIEW_ASSIGNED_VIEW);
        if (evaluationProjectExpertRepository.findByEvaluationIdAndExpertId(evaluationId, currentUserId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号未被分配到该评估项目");
        }
    }

    @Transactional(readOnly = true)
    public void requireResultSummaryAccess(EvaluationProject project) {
        Long currentUserId = currentUserId();
        if (dataScopeService.isAdmin() || dataScopeService.hasPermission(PermissionCodes.EVALUATION_RESULT_VIEW)) {
            return;
        }
        if (dataScopeService.hasPermission(PermissionCodes.EVALUATION_AUDIT_VIEW)
                && project.getAuditorId() != null
                && project.getAuditorId().equals(currentUserId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有查看评估结果的权限");
    }

    @Transactional(readOnly = true)
    public void requireAuditAccess(EvaluationProject project) {
        Long currentUserId = currentUserId();
        if (dataScopeService.isAdmin()) {
            return;
        }
        dataScopeService.requirePermission(PermissionCodes.EVALUATION_AUDIT_VIEW);
        if (project.getAuditorId() == null || !project.getAuditorId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不是该评估项目的审核人");
        }
    }

    @Transactional(readOnly = true)
    public void requireAuditApprove(EvaluationProject project) {
        if (!dataScopeService.isAdmin()) {
            dataScopeService.requirePermission(PermissionCodes.EVALUATION_AUDIT_APPROVE);
        }
        requireAuditAccess(project);
    }

    @Transactional(readOnly = true)
    public void requireAuditReject(EvaluationProject project) {
        if (!dataScopeService.isAdmin()) {
            dataScopeService.requirePermission(PermissionCodes.EVALUATION_AUDIT_REJECT_REVIEW);
        }
        requireAuditAccess(project);
    }

    public boolean canViewDraftSummaries(EvaluationProject project) {
        Long currentUserId = currentUserId();
        return dataScopeService.isAdmin()
                || dataScopeService.hasPermission(PermissionCodes.EVALUATION_RESULT_VIEW)
                || dataScopeService.hasPermission(PermissionCodes.EVALUATION_VIEW)
                || (project.getAuditorId() != null && project.getAuditorId().equals(currentUserId) && dataScopeService.hasPermission(PermissionCodes.EVALUATION_AUDIT_VIEW));
    }
}
