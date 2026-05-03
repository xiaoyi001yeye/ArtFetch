package com.artfetch.auth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.artfetch.auth.dto.AuthPermissionDto;
import com.artfetch.auth.service.PermissionService;
import com.artfetch.auth.support.PermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @SaCheckPermission(PermissionCodes.ROLE_VIEW)
    public ResponseEntity<List<AuthPermissionDto>> listPermissions() {
        return ResponseEntity.ok(permissionService.listPermissions());
    }
}
