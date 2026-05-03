package com.artfetch.evaluation.service;

import com.artfetch.dto.ArtworkDto;
import com.artfetch.entity.Artwork;
import com.artfetch.evaluation.dto.*;
import com.artfetch.evaluation.entity.EvaluationProject;
import com.artfetch.evaluation.entity.EvaluationProjectStatus;
import com.artfetch.evaluation.entity.ExpertReview;
import com.artfetch.evaluation.entity.ExpertReviewScore;
import com.artfetch.evaluation.entity.ExpertReviewStatus;
import com.artfetch.evaluation.repository.ExpertReviewRepository;
import com.artfetch.evaluation.repository.ExpertReviewScoreRepository;
import com.artfetch.evaluation.repository.EvaluationProjectMetricRepository;
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
public class ExpertReviewService {

    private final EvaluationProjectService projectService;
    private final EvaluationAccessService accessService;
    private final ExpertReviewRepository reviewRepository;
    private final ExpertReviewScoreRepository scoreRepository;
    private final EvaluationProjectMetricRepository metricRepository;
    private final ArtworkRepository artworkRepository;

    @Transactional(readOnly = true)
    public ExpertReviewFormDto getMyReviewForm(Long evaluationId, Long artworkId) {
        accessService.requireOwnAssignedProject(evaluationId);
        EvaluationProject project = projectService.requireProject(evaluationId);
        ExpertReview review = requireOwnReview(evaluationId, artworkId);
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "艺术品不存在"));
        return new ExpertReviewFormDto(
                project.getId(),
                project.getName(),
                project.getStatus().name(),
                ArtworkDto.from(artwork),
                metricRepository.findByEvaluationIdOrderBySortOrderAscIdAsc(evaluationId).stream().map(MetricConfigDto::from).toList(),
                toReviewDto(review)
        );
    }

    @Transactional
    public ExpertReviewDto saveMyReview(Long evaluationId, Long artworkId, SaveExpertReviewRequest request) {
        accessService.requireOwnAssignedProject(evaluationId);
        EvaluationProject project = projectService.requireProject(evaluationId);
        ensureReviewEditable(project, false);
        ExpertReview review = requireOwnReview(evaluationId, artworkId);
        if (review.getStatus() == ExpertReviewStatus.SUBMITTED || review.getStatus() == ExpertReviewStatus.RESUBMITTED) {
            throw new IllegalStateException("已提交的评估不能直接修改");
        }
        applyReviewContent(review, request, false);
        if (review.getStatus() == ExpertReviewStatus.NOT_STARTED) {
            review.setStatus(ExpertReviewStatus.DRAFT);
        }
        if (project.getConfigLockedAt() == null) {
            project.setConfigLockedAt(LocalDateTime.now());
        }
        if (project.getStatus() == EvaluationProjectStatus.PENDING) {
            project.setStatus(EvaluationProjectStatus.IN_PROGRESS);
        }
        reviewRepository.save(review);
        projectService.recalculateProjectState(evaluationId);
        return toReviewDto(review);
    }

    @Transactional
    public ExpertReviewDto submitMyReview(Long evaluationId, Long artworkId, SaveExpertReviewRequest request) {
        accessService.requireOwnAssignedProject(evaluationId);
        EvaluationProject project = projectService.requireProject(evaluationId);
        ensureReviewEditable(project, true);
        ExpertReview review = requireOwnReview(evaluationId, artworkId);
        applyReviewContent(review, request, true);
        if (review.getStatus() == ExpertReviewStatus.REVIEW_REJECTED) {
            review.setStatus(ExpertReviewStatus.RESUBMITTED);
            review.setResubmittedAt(LocalDateTime.now());
        } else {
            review.setStatus(ExpertReviewStatus.SUBMITTED);
        }
        review.setSubmittedAt(LocalDateTime.now());
        review.setRejectedReason(null);
        review.setRejectedAt(null);
        reviewRepository.save(review);
        projectService.recalculateProjectState(evaluationId);
        return toReviewDto(review);
    }

    @Transactional(readOnly = true)
    public ArtworkReviewSummaryDto getArtworkReviewSummary(Long evaluationId, Long artworkId) {
        EvaluationProject project = projectService.requireProject(evaluationId);
        accessService.requireResultSummaryAccess(project);
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "艺术品不存在"));
        List<ExpertReview> reviews = reviewRepository.findByEvaluationIdAndArtworkIdOrderByExpertNameAsc(evaluationId, artworkId);
        boolean includeDrafts = accessService.canViewDraftSummaries(project) && (project.getAuditorId() == null || !project.getAuditorId().equals(accessService.currentUserId()));
        if (!includeDrafts) {
            reviews = reviews.stream()
                    .filter(review -> review.getStatus() == ExpertReviewStatus.SUBMITTED || review.getStatus() == ExpertReviewStatus.RESUBMITTED)
                    .toList();
        }
        return new ArtworkReviewSummaryDto(ArtworkDto.from(artwork), reviews.stream().map(this::toReviewDto).toList());
    }

    @Transactional(readOnly = true)
    public ExpertReview requireOwnReview(Long evaluationId, Long artworkId) {
        Long currentUserId = accessService.currentUserId();
        return reviewRepository.findByEvaluationIdAndArtworkIdAndExpertId(evaluationId, artworkId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "专家评估记录不存在"));
    }

    @Transactional(readOnly = true)
    public ExpertReview requireReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "专家评估记录不存在"));
    }

    @Transactional
    public void markReviewRejected(ExpertReview review, String reason) {
        review.setStatus(ExpertReviewStatus.REVIEW_REJECTED);
        review.setRejectedReason(reason);
        review.setRejectedAt(LocalDateTime.now());
        reviewRepository.save(review);
        projectService.recalculateProjectState(review.getEvaluationId());
    }

    @Transactional(readOnly = true)
    public ExpertReviewDto toReviewDto(ExpertReview review) {
        return ExpertReviewDto.from(review, scoreRepository.findByReviewId(review.getId()).stream().map(ExpertReviewScoreDto::from).toList());
    }

    private void ensureReviewEditable(EvaluationProject project, boolean submitting) {
        if (project.getStatus() == EvaluationProjectStatus.COMPLETED || project.getStatus() == EvaluationProjectStatus.CANCELLED) {
            throw new IllegalStateException("当前评估项目已结束，不能继续修改");
        }
        if (!submitting && project.getStatus() == EvaluationProjectStatus.IN_REVIEW) {
            throw new IllegalStateException("项目审核中，不能保存草稿");
        }
    }

    private void applyReviewContent(ExpertReview review, SaveExpertReviewRequest request, boolean validateForSubmit) {
        review.setFinalEstimate(blankToNull(request.finalEstimate()));
        review.setFinalEstimateCurrency(blankToNull(request.finalEstimateCurrency()));
        review.setComment(blankToNull(request.comment()));

        Map<Long, ExpertReviewScore> existing = scoreRepository.findByReviewId(review.getId()).stream()
                .collect(Collectors.toMap(ExpertReviewScore::getProjectMetricId, Function.identity()));
        Set<Long> currentMetricIds = metricRepository.findByEvaluationIdOrderBySortOrderAscIdAsc(review.getEvaluationId()).stream()
                .map(metric -> metric.getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (request.scores() != null) {
            for (ExpertReviewScoreRequest item : request.scores()) {
                if (!currentMetricIds.contains(item.projectMetricId())) {
                    throw new IllegalArgumentException("存在无效的评估指标");
                }
                ExpertReviewScore score = existing.getOrDefault(item.projectMetricId(), new ExpertReviewScore());
                score.setReviewId(review.getId());
                score.setProjectMetricId(item.projectMetricId());
                score.setScore(item.score());
                score.setOptionValue(blankToNull(item.optionValue()));
                score.setTextValue(blankToNull(item.textValue()));
                score.setComment(blankToNull(item.comment()));
                existing.put(item.projectMetricId(), score);
            }
        }
        scoreRepository.saveAll(existing.values());

        if (validateForSubmit) {
            if (review.getFinalEstimate() == null || review.getFinalEstimateCurrency() == null) {
                throw new IllegalArgumentException("提交前必须填写最终估价和币种");
            }
            Map<Long, ExpertReviewScore> savedScores = scoreRepository.findByReviewId(review.getId()).stream()
                    .collect(Collectors.toMap(ExpertReviewScore::getProjectMetricId, Function.identity()));
            metricRepository.findByEvaluationIdOrderBySortOrderAscIdAsc(review.getEvaluationId()).forEach(metric -> {
                if (metric.isRequired()) {
                    ExpertReviewScore score = savedScores.get(metric.getId());
                    boolean hasNumeric = score != null && score.getScore() != null;
                    boolean hasText = score != null && score.getTextValue() != null && !score.getTextValue().isBlank();
                    boolean hasOption = score != null && score.getOptionValue() != null && !score.getOptionValue().isBlank();
                    if (!(hasNumeric || hasText || hasOption)) {
                        throw new IllegalArgumentException("必填指标未完成: " + metric.getName());
                    }
                }
            });
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
