package com.artfetch.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.artfetch.auth.service.AuditLogService;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.dto.CreateHdImageMigrationTaskRequest;
import com.artfetch.dto.HdImageMigrationItemDto;
import com.artfetch.dto.HdImageMigrationTaskDto;
import com.artfetch.dto.PageResult;
import com.artfetch.service.HdImageMigrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/hd-image-migrations")
@RequiredArgsConstructor
public class HdImageMigrationController {

    private final HdImageMigrationService service;
    private final AuditLogService auditLogService;

    @GetMapping
    @SaCheckPermission(PermissionCodes.HD_IMAGE_MIGRATION_VIEW)
    public ResponseEntity<PageResult<HdImageMigrationTaskDto>> list(@RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listTasks(page, size));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.HD_IMAGE_MIGRATION_MANAGE)
    public ResponseEntity<HdImageMigrationTaskDto> create(@Valid @RequestBody CreateHdImageMigrationTaskRequest request) {
        HdImageMigrationTaskDto task = service.createTask(request);
        auditLogService.recordSuccess("hd-image-migration.create", "HD_IMAGE_MIGRATION", String.valueOf(task.getId()), "创建高清图迁移任务 " + task.getName());
        return ResponseEntity.ok(task);
    }

    @GetMapping("/{id}")
    @SaCheckPermission(PermissionCodes.HD_IMAGE_MIGRATION_VIEW)
    public ResponseEntity<HdImageMigrationTaskDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getTask(id));
    }

    @PostMapping("/{id}/start")
    @SaCheckPermission(PermissionCodes.HD_IMAGE_MIGRATION_MANAGE)
    public ResponseEntity<HdImageMigrationTaskDto> start(@PathVariable Long id) {
        HdImageMigrationTaskDto task = service.start(id);
        auditLogService.recordSuccess("hd-image-migration.start", "HD_IMAGE_MIGRATION", String.valueOf(id), "启动高清图迁移任务");
        return ResponseEntity.ok(task);
    }

    @PostMapping("/{id}/pause")
    @SaCheckPermission(PermissionCodes.HD_IMAGE_MIGRATION_MANAGE)
    public ResponseEntity<HdImageMigrationTaskDto> pause(@PathVariable Long id) {
        HdImageMigrationTaskDto task = service.pause(id);
        auditLogService.recordSuccess("hd-image-migration.pause", "HD_IMAGE_MIGRATION", String.valueOf(id), "暂停高清图迁移任务");
        return ResponseEntity.ok(task);
    }

    @PostMapping("/{id}/resume")
    @SaCheckPermission(PermissionCodes.HD_IMAGE_MIGRATION_MANAGE)
    public ResponseEntity<HdImageMigrationTaskDto> resume(@PathVariable Long id) {
        HdImageMigrationTaskDto task = service.start(id);
        auditLogService.recordSuccess("hd-image-migration.resume", "HD_IMAGE_MIGRATION", String.valueOf(id), "恢复高清图迁移任务");
        return ResponseEntity.ok(task);
    }

    @PostMapping("/{id}/cancel")
    @SaCheckPermission(PermissionCodes.HD_IMAGE_MIGRATION_MANAGE)
    public ResponseEntity<HdImageMigrationTaskDto> cancel(@PathVariable Long id) {
        HdImageMigrationTaskDto task = service.cancel(id);
        auditLogService.recordSuccess("hd-image-migration.cancel", "HD_IMAGE_MIGRATION", String.valueOf(id), "取消高清图迁移任务");
        return ResponseEntity.ok(task);
    }

    @PostMapping("/{id}/retry-failed")
    @SaCheckPermission(PermissionCodes.HD_IMAGE_MIGRATION_MANAGE)
    public ResponseEntity<HdImageMigrationTaskDto> retryFailed(@PathVariable Long id) {
        HdImageMigrationTaskDto task = service.retryFailed(id);
        auditLogService.recordSuccess("hd-image-migration.retry-failed", "HD_IMAGE_MIGRATION", String.valueOf(id), "重试高清图迁移失败项");
        return ResponseEntity.ok(task);
    }

    @GetMapping("/{id}/items")
    @SaCheckPermission(PermissionCodes.HD_IMAGE_MIGRATION_VIEW)
    public ResponseEntity<PageResult<HdImageMigrationItemDto>> listItems(@PathVariable Long id,
                                                                        @RequestParam(required = false) String status,
                                                                        @RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listItems(id, status, page, size));
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
