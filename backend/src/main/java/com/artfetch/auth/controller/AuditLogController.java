package com.artfetch.auth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.artfetch.auth.dto.AuditLogDto;
import com.artfetch.auth.service.AuditLogQueryService;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping
    @SaCheckPermission(PermissionCodes.AUDIT_LOG_VIEW)
    public ResponseEntity<PageResult<AuditLogDto>> listLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditLogQueryService.listLogs(username, action, success, page, size));
    }
}
