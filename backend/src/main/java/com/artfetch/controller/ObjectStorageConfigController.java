package com.artfetch.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.artfetch.auth.service.AuditLogService;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.dto.ObjectStorageConfigDto;
import com.artfetch.dto.ObjectStorageTestResultDto;
import com.artfetch.dto.SaveObjectStorageConfigRequest;
import com.artfetch.service.ObjectStorageConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings/object-storage")
@RequiredArgsConstructor
public class ObjectStorageConfigController {

    private final ObjectStorageConfigService service;
    private final AuditLogService auditLogService;

    @GetMapping
    @SaCheckPermission(PermissionCodes.SETTINGS_OBJECT_STORAGE_VIEW)
    public ResponseEntity<List<ObjectStorageConfigDto>> list() {
        return ResponseEntity.ok(service.listConfigs());
    }

    @GetMapping("/{id}/edit")
    @SaCheckPermission(PermissionCodes.SETTINGS_OBJECT_STORAGE_MANAGE)
    public ResponseEntity<ObjectStorageConfigDto> getForEdit(@PathVariable Long id) {
        ObjectStorageConfigDto config = service.getConfigForEdit(id);
        auditLogService.recordSuccess("object-storage.secret.view", "OBJECT_STORAGE", String.valueOf(id), "查看火山 TOS 配置凭据 " + config.getName());
        return ResponseEntity.ok(config);
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.SETTINGS_OBJECT_STORAGE_MANAGE)
    public ResponseEntity<ObjectStorageConfigDto> create(@Valid @RequestBody SaveObjectStorageConfigRequest request) {
        ObjectStorageConfigDto config = service.create(request);
        auditLogService.recordSuccess("object-storage.create", "OBJECT_STORAGE", String.valueOf(config.getId()), "创建火山 TOS 配置 " + config.getName());
        return ResponseEntity.ok(config);
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.SETTINGS_OBJECT_STORAGE_MANAGE)
    public ResponseEntity<ObjectStorageConfigDto> update(@PathVariable Long id,
                                                         @Valid @RequestBody SaveObjectStorageConfigRequest request) {
        ObjectStorageConfigDto config = service.update(id, request);
        auditLogService.recordSuccess("object-storage.update", "OBJECT_STORAGE", String.valueOf(id), "更新火山 TOS 配置 " + config.getName());
        return ResponseEntity.ok(config);
    }

    @PostMapping("/{id}/test")
    @SaCheckPermission(PermissionCodes.SETTINGS_OBJECT_STORAGE_MANAGE)
    public ResponseEntity<ObjectStorageTestResultDto> test(@PathVariable Long id) {
        ObjectStorageTestResultDto result = service.test(id);
        auditLogService.recordSuccess("object-storage.test", "OBJECT_STORAGE", String.valueOf(id), "测试火山 TOS 连接: " + result.getMessage());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/enable")
    @SaCheckPermission(PermissionCodes.SETTINGS_OBJECT_STORAGE_MANAGE)
    public ResponseEntity<ObjectStorageConfigDto> enable(@PathVariable Long id) {
        ObjectStorageConfigDto config = service.enable(id);
        auditLogService.recordSuccess("object-storage.enable", "OBJECT_STORAGE", String.valueOf(id), "启用火山 TOS 配置 " + config.getName());
        return ResponseEntity.ok(config);
    }

    @PostMapping("/{id}/disable")
    @SaCheckPermission(PermissionCodes.SETTINGS_OBJECT_STORAGE_MANAGE)
    public ResponseEntity<ObjectStorageConfigDto> disable(@PathVariable Long id) {
        ObjectStorageConfigDto config = service.disable(id);
        auditLogService.recordSuccess("object-storage.disable", "OBJECT_STORAGE", String.valueOf(id), "禁用火山 TOS 配置 " + config.getName());
        return ResponseEntity.ok(config);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidState(IllegalStateException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
