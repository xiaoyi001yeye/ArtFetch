package com.artfetch.auth.controller;

import com.artfetch.auth.dto.ChangePasswordRequest;
import com.artfetch.auth.dto.CurrentUserDto;
import com.artfetch.auth.dto.LoginRequest;
import com.artfetch.auth.dto.LoginResponse;
import com.artfetch.auth.service.AuthService;
import com.artfetch.auth.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        authService.logout();
        return ResponseEntity.ok(Map.of("message", "已退出登录"));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserDto> me() {
        return ResponseEntity.ok(currentUserService.currentUser());
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(Map.of("message", "密码已修改，请重新登录"));
    }
}
