package com.artfetch.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.artfetch.auth.dto.ChangePasswordRequest;
import com.artfetch.auth.dto.CurrentUserDto;
import com.artfetch.auth.dto.LoginRequest;
import com.artfetch.auth.dto.LoginResponse;
import com.artfetch.auth.entity.AuthUser;
import com.artfetch.auth.entity.UserStatus;
import com.artfetch.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserRepository userRepository;
    private final PasswordService passwordService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        AuthUser user = userRepository.findWithRolesByUsername(username).orElse(null);
        if (user == null || !passwordService.matches(request.password(), user.getPasswordHash())) {
            auditLogService.recordLoginFailure(username, "用户名或密码错误");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != UserStatus.ENABLED) {
            auditLogService.recordLoginFailure(username, "账号已停用");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号已停用");
        }

        StpUtil.login(user.getId());
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        auditLogService.recordSuccess("auth.login.success", "AUTH", String.valueOf(user.getId()), "登录成功");

        return new LoginResponse(
                "Authorization",
                StpUtil.getTokenValue(),
                "Bearer",
                StpUtil.getTokenTimeout(),
                CurrentUserDto.from(user)
        );
    }

    public void logout() {
        auditLogService.recordSuccess("auth.logout", "AUTH", String.valueOf(currentUserService.currentUserId()), "退出登录");
        StpUtil.logout();
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        AuthUser user = userRepository.findById(currentUserService.currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在"));
        if (!passwordService.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("原密码不正确");
        }
        passwordService.validatePasswordStrength(request.newPassword(), user.getUsername());
        user.setPasswordHash(passwordService.hashPassword(request.newPassword()));
        userRepository.save(user);
        auditLogService.recordSuccess("auth.change-password", "AUTH", String.valueOf(user.getId()), "修改自己的密码");
        StpUtil.logout(user.getId());
    }
}
