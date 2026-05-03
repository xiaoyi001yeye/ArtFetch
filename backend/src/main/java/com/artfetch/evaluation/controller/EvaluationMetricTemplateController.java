package com.artfetch.evaluation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.dto.PageResult;
import com.artfetch.evaluation.dto.CreateEvaluationMetricTemplateRequest;
import com.artfetch.evaluation.dto.EvaluationMetricTemplateDto;
import com.artfetch.evaluation.dto.MetricConfigDto;
import com.artfetch.evaluation.dto.UpdateEvaluationMetricTemplateRequest;
import com.artfetch.evaluation.service.EvaluationMetricTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluation-metric-templates")
@RequiredArgsConstructor
public class EvaluationMetricTemplateController {

    private final EvaluationMetricTemplateService service;

    @GetMapping
    @SaCheckPermission(PermissionCodes.EVALUATION_TEMPLATE_VIEW)
    public ResponseEntity<PageResult<EvaluationMetricTemplateDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(service.list(page, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(PermissionCodes.EVALUATION_TEMPLATE_VIEW)
    public ResponseEntity<EvaluationMetricTemplateDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @GetMapping("/{id}/items")
    @SaCheckPermission(PermissionCodes.EVALUATION_TEMPLATE_VIEW)
    public ResponseEntity<List<MetricConfigDto>> items(@PathVariable Long id) {
        return ResponseEntity.ok(service.listItems(id));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.EVALUATION_TEMPLATE_CREATE)
    public ResponseEntity<EvaluationMetricTemplateDto> create(@Valid @RequestBody CreateEvaluationMetricTemplateRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.EVALUATION_TEMPLATE_UPDATE)
    public ResponseEntity<EvaluationMetricTemplateDto> update(@PathVariable Long id,
                                                              @Valid @RequestBody UpdateEvaluationMetricTemplateRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.EVALUATION_TEMPLATE_DISABLE)
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "评估模板已删除"));
    }
}
