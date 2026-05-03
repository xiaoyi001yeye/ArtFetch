package com.artfetch.auth.repository;

import com.artfetch.auth.entity.AuthRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuthRoleRepository extends JpaRepository<AuthRole, Long> {
    Optional<AuthRole> findByCode(String code);
    List<AuthRole> findByCodeIn(Collection<String> codes);

    @EntityGraph(attributePaths = "permissions")
    @Query("select role from AuthRole role where role.id = :id")
    Optional<AuthRole> findWithPermissionsById(@Param("id") Long id);

    @EntityGraph(attributePaths = "permissions")
    @Query("select role from AuthRole role where role.code = :code")
    Optional<AuthRole> findWithPermissionsByCode(@Param("code") String code);
}
