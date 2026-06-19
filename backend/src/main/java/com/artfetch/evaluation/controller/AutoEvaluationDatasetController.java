package com.artfetch.evaluation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.dto.PageResult;
import com.artfetch.evaluation.dto.*;
import com.artfetch.evaluation.service.AutoEvaluationDatasetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auto-evaluation/datasets")
@RequiredArgsConstructor
public class AutoEvaluationDatasetController {

    private final AutoEvaluationDatasetService service;

    @GetMapping
    @SaCheckPermission(PermissionCodes.AUTO_EVALUATION_DATASET_VIEW)
    public ResponseEntity<PageResult<AutoEvaluationDatasetDto>> list(@RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(page, size));
    }

    @GetMapping("/source-evaluations")
    @SaCheckPermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE)
    public ResponseEntity<PageResult<AutoEvaluationSourceProjectDto>> listSourceProjects(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listSourceProjects(keyword, page, size));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE)
    public ResponseEntity<AutoEvaluationDatasetDto> create(@Valid @RequestBody CreateAutoEvaluationDatasetRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(PermissionCodes.AUTO_EVALUATION_DATASET_VIEW)
    public ResponseEntity<AutoEvaluationDatasetDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE)
    public ResponseEntity<AutoEvaluationDatasetDto> update(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateAutoEvaluationDatasetRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/archive")
    @SaCheckPermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE)
    public ResponseEntity<AutoEvaluationDatasetDto> archive(@PathVariable Long id) {
        return ResponseEntity.ok(service.archive(id));
    }

    @GetMapping("/{id}/artworks")
    @SaCheckPermission(PermissionCodes.AUTO_EVALUATION_DATASET_VIEW)
    public ResponseEntity<PageResult<AutoEvaluationArtworkCandidateDto>> listArtworks(
            @PathVariable Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean selectedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listCandidateArtworks(id, keyword, selectedOnly, page, size));
    }

    @PostMapping("/{id}/selected-artworks")
    @SaCheckPermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE)
    public ResponseEntity<AutoEvaluationDatasetDto> updateSelection(@PathVariable Long id,
                                                                    @RequestBody UpdateDatasetArtworkSelectionRequest request) {
        return ResponseEntity.ok(service.updateSelection(id, request));
    }

    @DeleteMapping("/{id}/selected-artworks")
    @SaCheckPermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE)
    public ResponseEntity<AutoEvaluationDatasetDto> clearSelection(@PathVariable Long id) {
        return ResponseEntity.ok(service.clearSelection(id));
    }

    @PostMapping("/{id}/check")
    @SaCheckPermission(PermissionCodes.AUTO_EVALUATION_DATASET_VIEW)
    public ResponseEntity<CheckAutoEvaluationDatasetResponse> check(@PathVariable Long id) {
        return ResponseEntity.ok(service.checkSelected(id));
    }

    @PostMapping("/{id}/generate")
    @SaCheckPermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE)
    public ResponseEntity<AutoEvaluationDatasetDto> generate(@PathVariable Long id) {
        return ResponseEntity.ok(service.startGeneration(id));
    }

    @GetMapping("/{id}/download")
    @SaCheckPermission(PermissionCodes.AUTO_EVALUATION_DATASET_EXPORT)
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Resource resource = service.downloadZip(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(service.zipFilename(id)).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
