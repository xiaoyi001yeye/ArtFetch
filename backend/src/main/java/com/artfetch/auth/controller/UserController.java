package com.artfetch.auth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.artfetch.auth.dto.*;
import com.artfetch.auth.service.UserService;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.dto.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @SaCheckPermission(PermissionCodes.USER_VIEW)
    public ResponseEntity<PageResult<AuthUserDto>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.listUsers(page, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(PermissionCodes.USER_VIEW)
    public ResponseEntity<AuthUserDto> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.USER_CREATE)
    public ResponseEntity<AuthUserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createUser(request));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.USER_UPDATE)
    public ResponseEntity<AuthUserDto> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @PutMapping("/{id}/status")
    @SaCheckPermission(PermissionCodes.USER_DISABLE)
    public ResponseEntity<AuthUserDto> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(userService.updateStatus(id, request));
    }

    @PostMapping("/{id}/reset-password")
    @SaCheckPermission(PermissionCodes.USER_UPDATE)
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ResponseEntity.ok(Map.of("message", "密码已重置"));
    }

    @PutMapping("/{id}/roles")
    @SaCheckPermission(PermissionCodes.USER_UPDATE)
    public ResponseEntity<AuthUserDto> updateRoles(@PathVariable Long id, @Valid @RequestBody UpdateUserRolesRequest request) {
        return ResponseEntity.ok(userService.updateRoles(id, request));
    }
}
