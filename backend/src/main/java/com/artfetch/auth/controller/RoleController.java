package com.artfetch.auth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.artfetch.auth.dto.*;
import com.artfetch.auth.service.RoleService;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.dto.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @SaCheckPermission(PermissionCodes.ROLE_VIEW)
    public ResponseEntity<PageResult<AuthRoleDto>> listRoles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(roleService.listRoles(page, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(PermissionCodes.ROLE_VIEW)
    public ResponseEntity<AuthRoleDto> getRole(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRole(id));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.ROLE_CREATE)
    public ResponseEntity<AuthRoleDto> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.ok(roleService.createRole(request));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.ROLE_UPDATE)
    public ResponseEntity<AuthRoleDto> updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(roleService.updateRole(id, request));
    }

    @PutMapping("/{id}/status")
    @SaCheckPermission(PermissionCodes.ROLE_DISABLE)
    public ResponseEntity<AuthRoleDto> updateStatus(@PathVariable Long id, @RequestBody UpdateRoleStatusRequest request) {
        return ResponseEntity.ok(roleService.updateStatus(id, request));
    }

    @PutMapping("/{id}/permissions")
    @SaCheckPermission(PermissionCodes.ROLE_UPDATE)
    public ResponseEntity<AuthRoleDto> updatePermissions(@PathVariable Long id, @RequestBody UpdateRolePermissionsRequest request) {
        return ResponseEntity.ok(roleService.updatePermissions(id, request));
    }
}
