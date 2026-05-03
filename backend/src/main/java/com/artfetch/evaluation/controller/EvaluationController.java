package com.artfetch.evaluation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.dto.PageResult;
import com.artfetch.evaluation.dto.*;
import com.artfetch.evaluation.service.EvaluationAuditService;
import com.artfetch.evaluation.service.EvaluationProjectService;
import com.artfetch.evaluation.service.ExpertReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationProjectService projectService;
    private final ExpertReviewService expertReviewService;
    private final EvaluationAuditService auditService;

    @GetMapping
    @SaCheckPermission(PermissionCodes.EVALUATION_VIEW)
    public ResponseEntity<PageResult<EvaluationProjectListItemDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(projectService.list(null, page, size));
    }

    @GetMapping("/assigned")
    @SaCheckPermission(PermissionCodes.EVALUATION_REVIEW_ASSIGNED_VIEW)
    public ResponseEntity<PageResult<EvaluationProjectListItemDto>> listAssigned(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(projectService.list("assigned", page, size));
    }

    @PostMapping("/preview-artworks")
    @SaCheckPermission(PermissionCodes.EVALUATION_CREATE)
    public ResponseEntity<PageResult<ArtworkPreviewDto>> previewArtworks(@Valid @RequestBody PreviewArtworksRequest request) {
        return ResponseEntity.ok(projectService.previewArtworks(request));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.EVALUATION_CREATE)
    public ResponseEntity<EvaluationProjectDto> create(@Valid @RequestBody CreateEvaluationProjectRequest request) {
        return ResponseEntity.ok(projectService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EvaluationProjectDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.get(id));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.EVALUATION_UPDATE)
    public ResponseEntity<EvaluationProjectDto> update(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateEvaluationProjectRequest request) {
        return ResponseEntity.ok(projectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.EVALUATION_DELETE)
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.ok(Map.of("message", "评估项目已删除"));
    }

    @GetMapping("/{id}/metrics")
    public ResponseEntity<List<MetricConfigDto>> metrics(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.listMetrics(id));
    }

    @GetMapping("/{id}/artworks")
    public ResponseEntity<List<EvaluationArtworkItemDto>> artworks(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.listArtworks(id));
    }

    @GetMapping("/{id}/experts")
    public ResponseEntity<List<EvaluationProjectExpertDto>> experts(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.listExperts(id));
    }

    @GetMapping("/{evaluationId}/artworks/{artworkId}/my-review")
    @SaCheckPermission(PermissionCodes.EVALUATION_REVIEW_OWN_VIEW)
    public ResponseEntity<ExpertReviewFormDto> myReview(@PathVariable Long evaluationId, @PathVariable Long artworkId) {
        return ResponseEntity.ok(expertReviewService.getMyReviewForm(evaluationId, artworkId));
    }

    @PostMapping("/{evaluationId}/artworks/{artworkId}/my-review")
    @SaCheckPermission(PermissionCodes.EVALUATION_REVIEW_OWN_SAVE)
    public ResponseEntity<ExpertReviewDto> createOrSaveMyReview(@PathVariable Long evaluationId,
                                                                @PathVariable Long artworkId,
                                                                @Valid @RequestBody SaveExpertReviewRequest request) {
        return ResponseEntity.ok(expertReviewService.saveMyReview(evaluationId, artworkId, request));
    }

    @PutMapping("/{evaluationId}/artworks/{artworkId}/my-review")
    @SaCheckPermission(PermissionCodes.EVALUATION_REVIEW_OWN_SAVE)
    public ResponseEntity<ExpertReviewDto> saveMyReview(@PathVariable Long evaluationId,
                                                        @PathVariable Long artworkId,
                                                        @Valid @RequestBody SaveExpertReviewRequest request) {
        return ResponseEntity.ok(expertReviewService.saveMyReview(evaluationId, artworkId, request));
    }

    @PostMapping("/{evaluationId}/artworks/{artworkId}/my-review/submit")
    @SaCheckPermission(PermissionCodes.EVALUATION_REVIEW_OWN_SUBMIT)
    public ResponseEntity<ExpertReviewDto> submitMyReview(@PathVariable Long evaluationId,
                                                          @PathVariable Long artworkId,
                                                          @Valid @RequestBody SaveExpertReviewRequest request) {
        return ResponseEntity.ok(expertReviewService.submitMyReview(evaluationId, artworkId, request));
    }

    @GetMapping("/{evaluationId}/artworks/{artworkId}/reviews")
    public ResponseEntity<ArtworkReviewSummaryDto> reviewSummary(@PathVariable Long evaluationId,
                                                                 @PathVariable Long artworkId) {
        return ResponseEntity.ok(expertReviewService.getArtworkReviewSummary(evaluationId, artworkId));
    }

    @PostMapping("/{id}/submit-review")
    @SaCheckPermission(PermissionCodes.EVALUATION_SUBMIT_REVIEW)
    public ResponseEntity<EvaluationProjectDto> submitReview(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.submitForReview(id));
    }

    @GetMapping("/{id}/audit-records")
    public ResponseEntity<List<EvaluationAuditRecordDto>> auditRecords(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.listRecords(id));
    }

    @PostMapping("/{id}/audit/approve")
    public ResponseEntity<EvaluationProjectDto> approve(@PathVariable Long id,
                                                        @RequestBody(required = false) AuditCommentRequest request) {
        return ResponseEntity.ok(auditService.approve(id, request));
    }

    @PostMapping("/{id}/expert-reviews/{reviewId}/audit/reject")
    public ResponseEntity<EvaluationProjectDto> rejectReview(@PathVariable Long id,
                                                             @PathVariable Long reviewId,
                                                             @Valid @RequestBody RejectExpertReviewRequest request) {
        return ResponseEntity.ok(auditService.rejectReview(id, reviewId, request.reason()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
