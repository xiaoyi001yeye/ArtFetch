package com.artfetch.auth.repository;

import com.artfetch.auth.entity.AuthUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AuthUser, Long> {
    boolean existsByUsername(String username);
    Optional<AuthUser> findByUsername(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select user from AuthUser user where user.id = :id")
    Optional<AuthUser> findWithRolesById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select user from AuthUser user where user.username = :username")
    Optional<AuthUser> findWithRolesByUsername(@Param("username") String username);
}
