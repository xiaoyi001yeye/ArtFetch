package com.artfetch.auth.support;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.artfetch.auth.service.AuditLogService;
import com.artfetch.auth.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
@RequiredArgsConstructor
public class AuthExceptionHandler {

    private static final Pattern ARTWORK_HD_V2_PATTERN = Pattern.compile("/api/artworks/(\\d+)/hd-image-v2");

    private final AuditLogService auditLogService;

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ErrorResponse> handleNotLogin(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHORIZED", "请先登录"));
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(Exception e, HttpServletRequest request) {
        recordHdV2PermissionFailureIfNeeded(e, request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("FORBIDDEN", "没有权限执行该操作"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.name(), e.getReason() == null ? status.getReasonPhrase() : e.getReason()));
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("参数错误");
        return ResponseEntity.badRequest().body(ErrorResponse.of("BAD_REQUEST", message));
    }

    private void recordHdV2PermissionFailureIfNeeded(Exception e, HttpServletRequest request) {
        if (request == null || request.getRequestURI() == null) {
            return;
        }
        Matcher matcher = ARTWORK_HD_V2_PATTERN.matcher(request.getRequestURI());
        if (!matcher.find()) {
            return;
        }
        auditLogService.recordFailure(
                "artwork.image.hd.view",
                "ARTWORK",
                matcher.group(1),
                "查看高清大图失败，imageVersion=hd-v2，reasonCode=NO_PERMISSION",
                e
        );
    }
}
