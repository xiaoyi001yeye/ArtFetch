package com.artfetch.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.artfetch.auth.dto.CurrentUserDto;
import com.artfetch.auth.entity.AuthUser;
import com.artfetch.auth.entity.UserStatus;
import com.artfetch.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final AuthUserRepository userRepository;

    public Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    @Transactional(readOnly = true)
    public AuthUser currentUserEntity() {
        Long userId = currentUserId();
        return userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在"));
    }

    @Transactional(readOnly = true)
    public CurrentUserDto currentUser() {
        AuthUser user = currentUserEntity();
        if (user.getStatus() != UserStatus.ENABLED) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号已停用");
        }
        return CurrentUserDto.from(user);
    }

    @Transactional(readOnly = true)
    public void requireEnabledCurrentUser() {
        AuthUser user = userRepository.findById(currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在"));
        if (user.getStatus() != UserStatus.ENABLED) {
            StpUtil.logout();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号已停用");
        }
    }
}
