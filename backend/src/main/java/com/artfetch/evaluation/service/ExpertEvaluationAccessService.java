package com.artfetch.evaluation.service;

import com.artfetch.auth.service.CurrentUserService;
import com.artfetch.auth.service.DataScopeService;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.evaluation.entity.EvaluationProject;
import com.artfetch.evaluation.entity.EvaluationProjectStatus;
import com.artfetch.evaluation.repository.EvaluationArtworkRepository;
import com.artfetch.evaluation.repository.EvaluationProjectExpertRepository;
import com.artfetch.evaluation.repository.EvaluationProjectRepository;
import com.artfetch.evaluation.repository.ExpertReviewRepository;
import com.artfetch.repository.ArtworkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExpertEvaluationAccessService {

    private final CurrentUserService currentUserService;
    private final DataScopeService dataScopeService;
    private final EvaluationProjectRepository projectRepository;
    private final EvaluationProjectExpertRepository projectExpertRepository;
    private final EvaluationArtworkRepository evaluationArtworkRepository;
    private final ExpertReviewRepository expertReviewRepository;
    private final ArtworkRepository artworkRepository;

    public Long currentUserId() {
        currentUserService.requireEnabledCurrentUser();
        return currentUserService.currentUserId();
    }

    @Transactional(readOnly = true)
    public EvaluationProject requireAssignedProject(Long evaluationId) {
        Long currentUserId = currentUserId();
        dataScopeService.requirePermission(PermissionCodes.EVALUATION_REVIEW_ASSIGNED_VIEW);
        EvaluationProject project = projectRepository.findById(evaluationId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "评估项目不存在"));
        if (project.getStatus() == EvaluationProjectStatus.DRAFT || project.getStatus() == EvaluationProjectStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评估项目不存在");
        }
        if (projectExpertRepository.findByEvaluationIdAndExpertId(evaluationId, currentUserId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号未被分配到该评估项目");
        }
        return project;
    }

    @Transactional(readOnly = true)
    public void requireExpertArtworkAccess(Long evaluationId, Long artworkId) {
        Long currentUserId = currentUserId();
        requireAssignedProject(evaluationId);
        if (!artworkRepository.existsById(artworkId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "艺术品不存在");
        }
        if (evaluationArtworkRepository.findByEvaluationIdAndArtworkId(evaluationId, artworkId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该艺术品不属于当前评估项目");
        }
        if (expertReviewRepository.findByEvaluationIdAndArtworkIdAndExpertId(evaluationId, artworkId, currentUserId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有访问该专家评估记录的权限");
        }
    }

    @Transactional(readOnly = true)
    public void requireExpertImageAccess(Long evaluationId, Long artworkId) {
        dataScopeService.requirePermission(PermissionCodes.ARTWORK_IMAGE_VIEW);
        requireExpertArtworkAccess(evaluationId, artworkId);
    }

    @Transactional(readOnly = true)
    public void requireGeneralArtworkImageAccess(Long artworkId) {
        Long currentUserId = currentUserId();
        dataScopeService.requirePermission(PermissionCodes.ARTWORK_IMAGE_VIEW);
        if (dataScopeService.hasPermission(PermissionCodes.ARTWORK_VIEW)) {
            return;
        }
        if (!artworkRepository.existsById(artworkId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "艺术品不存在");
        }
        boolean assigned = expertReviewRepository.findByArtworkIdAndExpertId(artworkId, currentUserId).stream()
                .anyMatch(review -> projectRepository.findById(review.getEvaluationId())
                        .filter(project -> project.getDeletedAt() == null)
                        .filter(project -> projectExpertRepository
                                .findByEvaluationIdAndExpertId(project.getId(), currentUserId)
                                .isPresent())
                        .isPresent());
        if (!assigned) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有访问该艺术品图片的权限");
        }
    }
}
