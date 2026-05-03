package com.artfetch.auth.repository;

import com.artfetch.auth.entity.AuthPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuthPermissionRepository extends JpaRepository<AuthPermission, Long> {
    Optional<AuthPermission> findByCode(String code);
    List<AuthPermission> findByCodeIn(Collection<String> codes);
}
