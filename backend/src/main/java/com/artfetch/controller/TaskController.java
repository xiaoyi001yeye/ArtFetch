package com.artfetch.controller;

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

    @PostMapping
    public ResponseEntity<TaskDto> createTask(@Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.ok(taskService.createTask(request));
    }

    @GetMapping
    public ResponseEntity<PageResult<TaskDto>> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(taskService.listTasks(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDto> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTask(id));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<TaskDto> startTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.startTask(id));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<TaskDto> pauseTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.pauseTask(id));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<TaskDto> resumeTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.resumeTask(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<TaskDto> cancelTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.cancelTask(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok(Map.of("message", "任务已删除"));
    }

    @GetMapping("/{id}/failures")
    public ResponseEntity<List<FetchFailureDto>> listFailures(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.listFailures(id));
    }

    @PostMapping("/{id}/failures/retry")
    public ResponseEntity<List<FetchFailureDto>> retryFailures(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.retryFailures(id));
    }

    @PostMapping("/{taskId}/failures/{failureId}/retry")
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
