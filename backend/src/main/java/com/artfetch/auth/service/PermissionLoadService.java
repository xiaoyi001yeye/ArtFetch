package com.artfetch.auth.service;

import com.artfetch.auth.entity.UserStatus;
import com.artfetch.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionLoadService {

    private final AuthUserRepository userRepository;

    @Transactional(readOnly = true)
    public List<String> findPermissionCodesByUserId(Long userId) {
        return userRepository.findWithRolesById(userId)
                .filter(user -> user.getStatus() == UserStatus.ENABLED)
                .map(user -> user.getRoles().stream()
                        .filter(role -> role.isEnabled())
                        .flatMap(role -> role.getPermissions().stream())
                        .filter(permission -> permission.isEnabled())
                        .map(permission -> permission.getCode())
                        .distinct()
                        .sorted()
                        .toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<String> findRoleCodesByUserId(Long userId) {
        return userRepository.findWithRolesById(userId)
                .filter(user -> user.getStatus() == UserStatus.ENABLED)
                .map(user -> user.getRoles().stream()
                        .filter(role -> role.isEnabled())
                        .map(role -> role.getCode())
                        .sorted()
                        .toList())
                .orElse(List.of());
    }
}
