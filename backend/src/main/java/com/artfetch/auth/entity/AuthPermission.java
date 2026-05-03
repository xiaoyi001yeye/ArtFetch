package com.artfetch.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "auth_permissions", indexes = {
        @Index(name = "idx_auth_permissions_module", columnList = "module"),
        @Index(name = "idx_auth_permissions_enabled", columnList = "enabled")
})
public class AuthPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 80)
    private String module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PermissionResourceType resourceType = PermissionResourceType.API;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean builtIn = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
