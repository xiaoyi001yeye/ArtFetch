package com.artfetch.evaluation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.dto.PageResult;
import com.artfetch.evaluation.dto.CreateEvaluationMetricDefinitionRequest;
import com.artfetch.evaluation.dto.EvaluationMetricDefinitionDto;
import com.artfetch.evaluation.dto.UpdateEvaluationMetricDefinitionRequest;
import com.artfetch.evaluation.service.EvaluationMetricDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluation-metrics")
@RequiredArgsConstructor
public class EvaluationMetricDefinitionController {

    private final EvaluationMetricDefinitionService service;

    @GetMapping
    @SaCheckPermission(PermissionCodes.EVALUATION_METRIC_VIEW)
    public ResponseEntity<PageResult<EvaluationMetricDefinitionDto>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(service.list(keyword, page, size));
    }

    @GetMapping("/enabled")
    @SaCheckPermission(PermissionCodes.EVALUATION_METRIC_VIEW)
    public ResponseEntity<List<EvaluationMetricDefinitionDto>> listEnabled() {
        return ResponseEntity.ok(service.listEnabled());
    }

    @GetMapping("/{id}")
    @SaCheckPermission(PermissionCodes.EVALUATION_METRIC_VIEW)
    public ResponseEntity<EvaluationMetricDefinitionDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.EVALUATION_METRIC_CREATE)
    public ResponseEntity<EvaluationMetricDefinitionDto> create(@Valid @RequestBody CreateEvaluationMetricDefinitionRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.EVALUATION_METRIC_UPDATE)
    public ResponseEntity<EvaluationMetricDefinitionDto> update(@PathVariable Long id,
                                                                @Valid @RequestBody UpdateEvaluationMetricDefinitionRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.EVALUATION_METRIC_DISABLE)
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "评估指标已删除"));
    }
}
