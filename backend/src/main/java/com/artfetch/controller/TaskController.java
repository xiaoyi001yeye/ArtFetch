package com.artfetch.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.artfetch.auth.service.AuditLogService;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.dto.CreateTaskRequest;
import com.artfetch.dto.FetchFailureDto;
import com.artfetch.dto.PageResult;
import com.artfetch.dto.TaskDto;
import com.artfetch.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final AuditLogService auditLogService;

    @PostMapping
    @SaCheckPermission(PermissionCodes.TASK_CREATE)
    public ResponseEntity<TaskDto> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskDto task = taskService.createTask(request);
        auditLogService.recordSuccess("task.create", "TASK", String.valueOf(task.getId()), "创建任务 " + task.getName());
        return ResponseEntity.ok(task);
    }

    @GetMapping
    @SaCheckPermission(PermissionCodes.TASK_VIEW)
    public ResponseEntity<PageResult<TaskDto>> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(taskService.listTasks(page, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(PermissionCodes.TASK_VIEW)
    public ResponseEntity<TaskDto> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTask(id));
    }

    @PostMapping("/{id}/start")
    @SaCheckPermission(PermissionCodes.TASK_START)
    public ResponseEntity<TaskDto> startTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.startTask(id));
    }

    @PostMapping("/{id}/pause")
    @SaCheckPermission(PermissionCodes.TASK_PAUSE)
    public ResponseEntity<TaskDto> pauseTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.pauseTask(id));
    }

    @PostMapping("/{id}/resume")
    @SaCheckPermission(PermissionCodes.TASK_RESUME)
    public ResponseEntity<TaskDto> resumeTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.resumeTask(id));
    }

    @PostMapping("/{id}/cancel")
    @SaCheckPermission(PermissionCodes.TASK_CANCEL)
    public ResponseEntity<TaskDto> cancelTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.cancelTask(id));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.TASK_DELETE)
    public ResponseEntity<Map<String, String>> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        auditLogService.recordSuccess("task.delete", "TASK", String.valueOf(id), "删除任务");
        return ResponseEntity.ok(Map.of("message", "任务已删除"));
    }

    @GetMapping("/{id}/failures")
    @SaCheckPermission(PermissionCodes.TASK_FAILURE_VIEW)
    public ResponseEntity<List<FetchFailureDto>> listFailures(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.listFailures(id));
    }

    @PostMapping("/{id}/failures/retry")
    @SaCheckPermission(PermissionCodes.TASK_FAILURE_RETRY)
    public ResponseEntity<List<FetchFailureDto>> retryFailures(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.retryFailures(id));
    }

    @PostMapping("/{taskId}/failures/{failureId}/retry")
    @SaCheckPermission(PermissionCodes.TASK_FAILURE_RETRY)
    public ResponseEntity<FetchFailureDto> retryFailure(@PathVariable Long taskId,
                                                        @PathVariable Long failureId) {
        return ResponseEntity.ok(taskService.retryFailure(taskId, failureId));
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
