package com.artfetch.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fetch_failures", indexes = {
        @Index(name = "idx_fetch_failure_task_id", columnList = "task_id"),
        @Index(name = "idx_fetch_failure_resolved", columnList = "resolved"),
        @Index(name = "idx_fetch_failure_failure_key", columnList = "failure_key", unique = true)
})
public class FetchFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private SearchTask task;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", nullable = false)
    private FailureType failureType;

    @Column(name = "failure_key", nullable = false, unique = true)
    private String failureKey;

    @Column(name = "page_number", nullable = false)
    private int pageNumber = 0;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "request_url", columnDefinition = "TEXT")
    private String requestUrl;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "error_type")
    private String errorType;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "failure_count", nullable = false)
    private int failureCount = 0;

    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "first_occurred_at", nullable = false)
    private LocalDateTime firstOccurredAt = LocalDateTime.now();

    @Column(name = "last_occurred_at", nullable = false)
    private LocalDateTime lastOccurredAt = LocalDateTime.now();

    @Column(name = "last_retried_at")
    private LocalDateTime lastRetriedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public enum FailureType {
        LIST_PAGE,
        DETAIL_PAGE
    }
}
