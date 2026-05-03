package com.artfetch.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.artfetch.auth.entity.AuditLog;
import com.artfetch.auth.repository.AuditLogRepository;
import com.artfetch.auth.repository.AuthUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuthUserRepository userRepository;

    public void recordSuccess(String action, String resourceType, String resourceId, String description) {
        record(action, resourceType, resourceId, description, true, null, null);
    }

    public void recordFailure(String action, String resourceType, String resourceId, String description, Exception e) {
        record(action, resourceType, resourceId, description, false, e == null ? null : e.getMessage(), null);
    }

    public void recordLoginFailure(String username, String message) {
        record("auth.login.failure", "AUTH", username, "登录失败", false, message, username);
    }

    private void record(String action, String resourceType, String resourceId, String description,
                        boolean success, String errorMessage, String fallbackUsername) {
        try {
            AuditLog logEntry = new AuditLog();
            logEntry.setAction(action);
            logEntry.setResourceType(resourceType);
            logEntry.setResourceId(resourceId);
            logEntry.setDescription(description);
            logEntry.setSuccess(success);
            logEntry.setErrorMessage(errorMessage);
            fillCurrentUser(logEntry, fallbackUsername);
            fillRequestInfo(logEntry);
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("写入审计日志失败: {}", action, e);
        }
    }

    private void fillCurrentUser(AuditLog logEntry, String fallbackUsername) {
        try {
            if (StpUtil.isLogin()) {
                Long userId = StpUtil.getLoginIdAsLong();
                logEntry.setUserId(userId);
                userRepository.findById(userId).ifPresent(user -> logEntry.setUsername(user.getUsername()));
                return;
            }
        } catch (Exception ignored) {
        }
        logEntry.setUsername(fallbackUsername);
    }

    private void fillRequestInfo(AuditLog logEntry) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        logEntry.setIpAddress(ip);
        logEntry.setUserAgent(request.getHeader("User-Agent"));
    }
}
