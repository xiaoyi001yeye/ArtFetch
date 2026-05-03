package com.artfetch.auth.service;

import com.artfetch.auth.dto.*;
import com.artfetch.auth.entity.AuthPermission;
import com.artfetch.auth.entity.AuthRole;
import com.artfetch.auth.repository.AuthPermissionRepository;
import com.artfetch.auth.repository.AuthRoleRepository;
import com.artfetch.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final AuthRoleRepository roleRepository;
    private final AuthPermissionRepository permissionRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResult<AuthRoleDto> listRoles(int page, int size) {
        return PageResult.of(
                roleRepository.findAll(PageRequest.of(page, size, Sort.by("code"))),
                role -> AuthRoleDto.from(roleRepository.findWithPermissionsById(role.getId()).orElse(role))
        );
    }

    @Transactional(readOnly = true)
    public AuthRoleDto getRole(Long id) {
        return AuthRoleDto.from(requireRoleWithPermissions(id));
    }

    @Transactional
    public AuthRoleDto createRole(CreateRoleRequest request) {
        String code = normalizeCode(request.code());
        if (roleRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        AuthRole role = new AuthRole();
        role.setCode(code);
        role.setName(request.name().trim());
        role.setDescription(blankToNull(request.description()));
        role.setEnabled(true);
        role.setBuiltIn(false);
        role.setPermissions(resolvePermissions(request.permissions()));
        AuthRole saved = roleRepository.save(role);
        auditLogService.recordSuccess("role.create", "ROLE", String.valueOf(saved.getId()), "创建角色 " + code);
        return AuthRoleDto.from(saved);
    }

    @Transactional
    public AuthRoleDto updateRole(Long id, UpdateRoleRequest request) {
        AuthRole role = requireRoleWithPermissions(id);
        role.setName(request.name().trim());
        role.setDescription(blankToNull(request.description()));
        AuthRole saved = roleRepository.save(role);
        auditLogService.recordSuccess("role.update", "ROLE", String.valueOf(id), "编辑角色 " + role.getCode());
        return AuthRoleDto.from(saved);
    }

    @Transactional
    public AuthRoleDto updateStatus(Long id, UpdateRoleStatusRequest request) {
        AuthRole role = requireRoleWithPermissions(id);
        role.setEnabled(request.enabled());
        AuthRole saved = roleRepository.save(role);
        auditLogService.recordSuccess(
                request.enabled() ? "role.enable" : "role.disable",
                "ROLE",
                String.valueOf(id),
                "更新角色状态 " + role.getCode()
        );
        return AuthRoleDto.from(saved);
    }

    @Transactional
    public AuthRoleDto updatePermissions(Long id, UpdateRolePermissionsRequest request) {
        AuthRole role = requireRoleWithPermissions(id);
        role.setPermissions(resolvePermissions(request.permissions()));
        AuthRole saved = roleRepository.save(role);
        auditLogService.recordSuccess("role.update-permissions", "ROLE", String.valueOf(id), "修改角色权限 " + role.getCode());
        return AuthRoleDto.from(saved);
    }

    private AuthRole requireRoleWithPermissions(Long id) {
        return roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
    }

    private Set<AuthPermission> resolvePermissions(Set<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<AuthPermission> permissions = new LinkedHashSet<>(permissionRepository.findByCodeIn(permissionCodes));
        if (permissions.size() != permissionCodes.size()) {
            throw new IllegalArgumentException("权限不存在或已被删除");
        }
        return permissions;
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
