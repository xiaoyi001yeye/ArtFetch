package com.artfetch.evaluation.service;

import com.artfetch.dto.PageResult;
import com.artfetch.entity.Artwork;
import com.artfetch.evaluation.dto.*;
import com.artfetch.evaluation.entity.*;
import com.artfetch.evaluation.repository.EvaluationArtworkRepository;
import com.artfetch.evaluation.repository.EvaluationProjectExpertRepository;
import com.artfetch.evaluation.repository.EvaluationProjectMetricRepository;
import com.artfetch.evaluation.repository.EvaluationProjectRepository;
import com.artfetch.evaluation.repository.ExpertReviewRepository;
import com.artfetch.repository.ArtworkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpertEvaluationService {

    private static final Set<ExpertReviewStatus> SUBMITTED_STATUSES =
            EnumSet.of(ExpertReviewStatus.SUBMITTED, ExpertReviewStatus.RESUBMITTED);

    private final ExpertEvaluationAccessService accessService;
    private final EvaluationProjectRepository projectRepository;
    private final EvaluationProjectExpertRepository projectExpertRepository;
    private final EvaluationArtworkRepository evaluationArtworkRepository;
    private final ExpertReviewRepository reviewRepository;
    private final EvaluationProjectMetricRepository metricRepository;
    private final ArtworkRepository artworkRepository;
    private final ExpertReviewService expertReviewService;

    @Transactional(readOnly = true)
    public PageResult<ExpertAssignedProjectListItemDto> listProjects(String filter, int page, int size) {
        Long currentUserId = accessService.currentUserId();
        List<EvaluationProjectExpert> assignments = projectExpertRepository.findByExpertIdOrderByAssignedAtDesc(currentUserId);
        Map<Long, EvaluationProject> projects = projectRepository.findAllById(
                        assignments.stream().map(EvaluationProjectExpert::getEvaluationId).toList()
                ).stream()
                .filter(project -> project.getDeletedAt() == null)
                .collect(Collectors.toMap(EvaluationProject::getId, Function.identity()));

        List<ExpertAssignedProjectListItemDto> items = assignments.stream()
                .map(assignment -> {
                    EvaluationProject project = projects.get(assignment.getEvaluationId());
                    if (project == null || !isPublishedForExperts(project)) {
                        return null;
                    }
                    return toProjectListItem(project, assignment,
                            reviewRepository.findByEvaluationIdAndExpertIdOrderByArtworkIdAsc(project.getId(), currentUserId));
                })
                .filter(Objects::nonNull)
                .filter(item -> matchesFilter(item, filter))
                .toList();

        return page(items, page, size);
    }

    @Transactional(readOnly = true)
    public ExpertAssignedProjectDto getProjectSummary(Long evaluationId) {
        return getProject(evaluationId, false);
    }

    @Transactional(readOnly = true)
    public ExpertAssignedProjectDto getProject(Long evaluationId) {
        return getProject(evaluationId, true);
    }

    private ExpertAssignedProjectDto getProject(Long evaluationId, boolean includeArtworks) {
        EvaluationProject project = accessService.requireAssignedProject(evaluationId);
        Long currentUserId = accessService.currentUserId();
        EvaluationProjectExpert assignment = projectExpertRepository.findByEvaluationIdAndExpertId(evaluationId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号未被分配到该评估项目"));
        List<ReviewRow> rows = loadRows(evaluationId, currentUserId);
        ExpertAssignedProjectListItemDto summary = toProjectListItem(project, assignment,
                rows.stream().map(ReviewRow::review).toList());
        List<ExpertArtworkListItemDto> artworks = includeArtworks ? rows.stream()
                .sorted(Comparator.comparingInt(row -> statusPriority(row.review().getStatus())))
                .map(row -> ExpertArtworkListItemDto.from(row.artwork(), row.review()))
                .toList() : List.of();
        return new ExpertAssignedProjectDto(
                summary.evaluationId(),
                summary.name(),
                summary.description(),
                summary.evaluationStatus(),
                summary.totalCount(),
                summary.submittedCount(),
                summary.pendingCount(),
                summary.rejectedCount(),
                summary.draftCount(),
                summary.nextArtworkId(),
                summary.updatedAt(),
                artworks
        );
    }

    @Transactional(readOnly = true)
    public ExpertReviewMobileFormDto getReviewForm(Long evaluationId, Long artworkId) {
        accessService.requireExpertArtworkAccess(evaluationId, artworkId);
        EvaluationProject project = accessService.requireAssignedProject(evaluationId);
        Long currentUserId = accessService.currentUserId();
        List<ReviewRow> rows = loadRows(evaluationId, currentUserId);
        int index = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (Objects.equals(rows.get(i).artwork().getId(), artworkId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "专家评估记录不存在");
        }
        ReviewRow current = rows.get(index);
        return new ExpertReviewMobileFormDto(
                evaluationId,
                project.getName(),
                project.getStatus().name(),
                index + 1,
                rows.size(),
                index > 0 ? rows.get(index - 1).artwork().getId() : null,
                index + 1 < rows.size() ? rows.get(index + 1).artwork().getId() : null,
                nextPendingArtworkId(rows.stream().map(ReviewRow::review).toList()),
                ExpertArtworkDto.from(current.artwork()),
                metricRepository.findByEvaluationIdOrderBySortOrderAscIdAsc(evaluationId).stream()
                        .map(MetricConfigDto::from)
                        .toList(),
                expertReviewService.toReviewDto(current.review())
        );
    }

    private ExpertAssignedProjectListItemDto toProjectListItem(EvaluationProject project,
                                                               EvaluationProjectExpert assignment,
                                                               List<ExpertReview> reviews) {
        int submitted = (int) reviews.stream().filter(review -> SUBMITTED_STATUSES.contains(review.getStatus())).count();
        int rejected = (int) reviews.stream().filter(review -> review.getStatus() == ExpertReviewStatus.REVIEW_REJECTED).count();
        int drafts = (int) reviews.stream().filter(review -> review.getStatus() == ExpertReviewStatus.DRAFT).count();
        LocalDateTime updatedAt = reviews.stream()
                .map(ExpertReview::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(assignment.getUpdatedAt());
        return new ExpertAssignedProjectListItemDto(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                reviews.size(),
                submitted,
                reviews.size() - submitted,
                rejected,
                drafts,
                nextPendingArtworkId(reviews),
                updatedAt == null ? project.getUpdatedAt() : updatedAt
        );
    }

    private List<ReviewRow> loadRows(Long evaluationId, Long expertId) {
        List<EvaluationArtwork> evaluationArtworks = evaluationArtworkRepository.findByEvaluationIdOrderByIdAsc(evaluationId);
        Map<Long, Artwork> artworks = artworkRepository.findByIdInOrderByIdAsc(
                        evaluationArtworks.stream().map(EvaluationArtwork::getArtworkId).toList()
                ).stream()
                .collect(Collectors.toMap(Artwork::getId, Function.identity()));
        Map<Long, ExpertReview> reviews = reviewRepository.findByEvaluationIdAndExpertIdOrderByArtworkIdAsc(evaluationId, expertId).stream()
                .collect(Collectors.toMap(ExpertReview::getArtworkId, Function.identity()));
        return evaluationArtworks.stream()
                .map(item -> new ReviewRow(artworks.get(item.getArtworkId()), reviews.get(item.getArtworkId())))
                .filter(row -> row.artwork() != null && row.review() != null)
                .toList();
    }

    private Long nextPendingArtworkId(List<ExpertReview> reviews) {
        return reviews.stream()
                .filter(review -> !SUBMITTED_STATUSES.contains(review.getStatus()))
                .min(Comparator.comparingInt(review -> statusPriority(review.getStatus())))
                .map(ExpertReview::getArtworkId)
                .orElse(null);
    }

    private int statusPriority(ExpertReviewStatus status) {
        return switch (status) {
            case REVIEW_REJECTED -> 0;
            case DRAFT -> 1;
            case NOT_STARTED -> 2;
            case SUBMITTED -> 3;
            case RESUBMITTED -> 4;
        };
    }

    private boolean matchesFilter(ExpertAssignedProjectListItemDto item, String filter) {
        if ("pending".equalsIgnoreCase(filter)) {
            return item.pendingCount() > 0;
        }
        if ("completed".equalsIgnoreCase(filter)) {
            return item.totalCount() > 0 && item.pendingCount() == 0;
        }
        return true;
    }

    private boolean isPublishedForExperts(EvaluationProject project) {
        return project.getStatus() != EvaluationProjectStatus.DRAFT
                && project.getStatus() != EvaluationProjectStatus.PENDING;
    }

    private <T> PageResult<T> page(List<T> items, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(100, requestedSize));
        int start = Math.min(items.size(), page * size);
        int end = Math.min(items.size(), start + size);
        PageResult<T> result = new PageResult<>();
        result.setItems(items.subList(start, end));
        result.setTotal(items.size());
        result.setPage(page);
        result.setSize(size);
        result.setTotalPages((items.size() + size - 1) / size);
        return result;
    }

    private record ReviewRow(Artwork artwork, ExpertReview review) {
    }
}
