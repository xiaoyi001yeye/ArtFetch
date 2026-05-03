package com.artfetch.auth.service;

import com.artfetch.auth.dto.AuthPermissionDto;
import com.artfetch.auth.repository.AuthPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final AuthPermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public List<AuthPermissionDto> listPermissions() {
        return permissionRepository.findAll(Sort.by("module", "code")).stream()
                .map(AuthPermissionDto::from)
                .toList();
    }
}
