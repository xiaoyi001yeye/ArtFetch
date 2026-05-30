package com.artfetch.evaluation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.dto.PageResult;
import com.artfetch.evaluation.dto.*;
import com.artfetch.evaluation.service.ExpertEvaluationAccessService;
import com.artfetch.evaluation.service.ExpertEvaluationImageService;
import com.artfetch.evaluation.service.ExpertEvaluationService;
import com.artfetch.evaluation.service.ExpertReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/expert/evaluations")
@RequiredArgsConstructor
public class ExpertEvaluationController {

    private final ExpertEvaluationService expertEvaluationService;
    private final ExpertEvaluationAccessService accessService;
    private final ExpertReviewService expertReviewService;
    private final ExpertEvaluationImageService imageService;

    @GetMapping
    @SaCheckPermission(PermissionCodes.EVALUATION_REVIEW_ASSIGNED_VIEW)
    public ResponseEntity<PageResult<ExpertAssignedProjectListItemDto>> list(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(expertEvaluationService.listProjects(filter, page, size));
    }

    @GetMapping("/{evaluationId}")
    @SaCheckPermission(PermissionCodes.EVALUATION_REVIEW_ASSIGNED_VIEW)
    public ResponseEntity<ExpertAssignedProjectDto> get(@PathVariable Long evaluationId) {
        return ResponseEntity.ok(expertEvaluationService.getProjectSummary(evaluationId));
    }

    @GetMapping("/{evaluationId}/artworks")
    @SaCheckPermission(PermissionCodes.EVALUATION_REVIEW_OWN_VIEW)
    public ResponseEntity<ExpertAssignedProjectDto> artworks(@PathVariable Long evaluationId) {
        return ResponseEntity.ok(expertEvaluationService.getProject(evaluationId));
    }

    @GetMapping("/{evaluationId}/artworks/{artworkId}/review")
    @SaCheckPermission(PermissionCodes.EVALUATION_REVIEW_OWN_VIEW)
    public ResponseEntity<ExpertReviewMobileFormDto> review(@PathVariable Long evaluationId,
                                                            @PathVariable Long artworkId) {
        return ResponseEntity.ok(expertEvaluationService.getReviewForm(evaluationId, artworkId));
    }

    @PutMapping("/{evaluationId}/artworks/{artworkId}/review")
    @SaCheckPermission(PermissionCodes.EVALUATION_REVIEW_OWN_SAVE)
    public ResponseEntity<ExpertReviewDto> saveReview(@PathVariable Long evaluationId,
                                                      @PathVariable Long artworkId,
                                                      @Valid @RequestBody SaveExpertReviewRequest request) {
        accessService.requireExpertArtworkAccess(evaluationId, artworkId);
        return ResponseEntity.ok(expertReviewService.saveMyReview(evaluationId, artworkId, request));
    }

    @PostMapping("/{evaluationId}/artworks/{artworkId}/review/submit")
    @SaCheckPermission(PermissionCodes.EVALUATION_REVIEW_OWN_SUBMIT)
    public ResponseEntity<ExpertReviewDto> submitReview(@PathVariable Long evaluationId,
                                                        @PathVariable Long artworkId,
                                                        @Valid @RequestBody SaveExpertReviewRequest request) {
        accessService.requireExpertArtworkAccess(evaluationId, artworkId);
        return ResponseEntity.ok(expertReviewService.submitMyReview(evaluationId, artworkId, request));
    }

    @GetMapping("/{evaluationId}/artworks/{artworkId}/images/preview")
    @SaCheckPermission(PermissionCodes.ARTWORK_IMAGE_VIEW)
    public ResponseEntity<byte[]> previewImage(@PathVariable Long evaluationId, @PathVariable Long artworkId) {
        var payload = imageService.loadPreview(evaluationId, artworkId);
        return ResponseEntity.ok().contentType(payload.mediaType()).body(payload.bytes());
    }

    @GetMapping("/{evaluationId}/artworks/{artworkId}/images/original")
    @SaCheckPermission(PermissionCodes.ARTWORK_IMAGE_VIEW)
    public ResponseEntity<Resource> originalImage(@PathVariable Long evaluationId, @PathVariable Long artworkId) {
        return imageResponse(
                imageService.loadOriginal(evaluationId, artworkId),
                imageService.originalFilename(artworkId),
                imageService.originalMediaType(artworkId)
        );
    }

    @GetMapping("/{evaluationId}/artworks/{artworkId}/images/hd")
    @SaCheckPermission(PermissionCodes.ARTWORK_IMAGE_VIEW)
    public ResponseEntity<Resource> hdImage(@PathVariable Long evaluationId, @PathVariable Long artworkId) {
        return imageResponse(
                imageService.loadHd(evaluationId, artworkId),
                imageService.hdFilename(artworkId),
                imageService.hdMediaType(artworkId)
        );
    }

    private ResponseEntity<Resource> imageResponse(Resource resource, String filename, MediaType mediaType) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" +
                        URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"))
                .body(resource);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }
}
