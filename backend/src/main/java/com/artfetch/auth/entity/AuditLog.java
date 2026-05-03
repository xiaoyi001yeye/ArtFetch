package com.artfetch.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "auth_audit_logs", indexes = {
        @Index(name = "idx_auth_audit_logs_user_id", columnList = "userId"),
        @Index(name = "idx_auth_audit_logs_action", columnList = "action"),
        @Index(name = "idx_auth_audit_logs_created_at", columnList = "createdAt"),
        @Index(name = "idx_auth_audit_logs_resource", columnList = "resourceType,resourceId")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 100)
    private String username;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 80)
    private String resourceType;

    @Column(length = 100)
    private String resourceId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 80)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false)
    private boolean success = true;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
