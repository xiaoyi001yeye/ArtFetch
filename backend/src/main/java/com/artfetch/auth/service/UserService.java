package com.artfetch.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.artfetch.auth.dto.*;
import com.artfetch.auth.entity.AuthRole;
import com.artfetch.auth.entity.AuthUser;
import com.artfetch.auth.entity.UserStatus;
import com.artfetch.auth.repository.AuthRoleRepository;
import com.artfetch.auth.repository.AuthUserRepository;
import com.artfetch.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AuthUserRepository userRepository;
    private final AuthRoleRepository roleRepository;
    private final PasswordService passwordService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResult<AuthUserDto> listUsers(int page, int size) {
        return PageResult.of(
                userRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))),
                user -> AuthUserDto.from(userRepository.findWithRolesById(user.getId()).orElse(user))
        );
    }

    @Transactional(readOnly = true)
    public AuthUserDto getUser(Long id) {
        return AuthUserDto.from(requireUserWithRoles(id));
    }

    @Transactional
    public AuthUserDto createUser(CreateUserRequest request) {
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        passwordService.validatePasswordStrength(request.password(), username);
        AuthUser user = new AuthUser();
        user.setUsername(username);
        user.setPasswordHash(passwordService.hashPassword(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setEmail(blankToNull(request.email()));
        user.setPhone(blankToNull(request.phone()));
        user.setStatus(UserStatus.ENABLED);
        user.setRoles(resolveRoles(request.roles()));
        AuthUser saved = userRepository.save(user);
        auditLogService.recordSuccess("user.create", "USER", String.valueOf(saved.getId()), "创建用户 " + username);
        return AuthUserDto.from(saved);
    }

    @Transactional
    public AuthUserDto updateUser(Long id, UpdateUserRequest request) {
        AuthUser user = requireUserWithRoles(id);
        user.setDisplayName(request.displayName().trim());
        user.setEmail(blankToNull(request.email()));
        user.setPhone(blankToNull(request.phone()));
        AuthUser saved = userRepository.save(user);
        auditLogService.recordSuccess("user.update", "USER", String.valueOf(id), "编辑用户 " + user.getUsername());
        return AuthUserDto.from(saved);
    }

    @Transactional
    public AuthUserDto updateStatus(Long id, UpdateUserStatusRequest request) {
        AuthUser user = requireUserWithRoles(id);
        user.setStatus(request.status());
        AuthUser saved = userRepository.save(user);
        if (request.status() == UserStatus.DISABLED) {
            StpUtil.logout(id);
        }
        auditLogService.recordSuccess(
                request.status() == UserStatus.DISABLED ? "user.disable" : "user.enable",
                "USER",
                String.valueOf(id),
                "更新用户状态 " + user.getUsername() + " -> " + request.status()
        );
        return AuthUserDto.from(saved);
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        AuthUser user = requireUserWithRoles(id);
        passwordService.validatePasswordStrength(request.newPassword(), user.getUsername());
        user.setPasswordHash(passwordService.hashPassword(request.newPassword()));
        userRepository.save(user);
        StpUtil.logout(id);
        auditLogService.recordSuccess("user.reset-password", "USER", String.valueOf(id), "重置用户密码 " + user.getUsername());
    }

    @Transactional
    public AuthUserDto updateRoles(Long id, UpdateUserRolesRequest request) {
        AuthUser user = requireUserWithRoles(id);
        user.setRoles(resolveRoles(request.roles()));
        AuthUser saved = userRepository.save(user);
        StpUtil.logout(id);
        auditLogService.recordSuccess("user.update-roles", "USER", String.valueOf(id), "分配用户角色 " + user.getUsername());
        return AuthUserDto.from(saved);
    }

    private AuthUser requireUserWithRoles(Long id) {
        return userRepository.findWithRolesById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private Set<AuthRole> resolveRoles(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个角色");
        }
        Set<AuthRole> roles = new LinkedHashSet<>(roleRepository.findByCodeIn(roleCodes));
        if (roles.size() != roleCodes.size()) {
            throw new IllegalArgumentException("角色不存在或已被删除");
        }
        return roles;
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
