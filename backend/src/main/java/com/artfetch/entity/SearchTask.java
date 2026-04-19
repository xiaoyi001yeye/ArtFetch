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
@Table(name = "search_tasks")
public class SearchTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type")
    private TaskType taskType = TaskType.SEARCH;

    @Column(name = "target_task_id")
    private Long targetTaskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "current_page")
    private int currentPage = 0;

    @Column(name = "total_pages")
    private int totalPages = 0;

    @Column(name = "total_fetched")
    private int totalFetched = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "detail_fetch_concurrency")
    private int detailFetchConcurrency = 1;

    @Column(name = "detail_request_count")
    private long detailRequestCount = 0;

    @Column(name = "detail_success_count")
    private long detailSuccessCount = 0;

    @Column(name = "detail_failure_count")
    private long detailFailureCount = 0;

    @Column(name = "avg_detail_latency_ms")
    private long avgDetailLatencyMs = 0;

    @Column(name = "p95_detail_latency_ms")
    private long p95DetailLatencyMs = 0;

    @Column(name = "max_detail_latency_ms")
    private long maxDetailLatencyMs = 0;

    @Column(name = "last_page_duration_ms")
    private long lastPageDurationMs = 0;

    @Column(name = "last_page_items_per_minute")
    private double lastPageItemsPerMinute = 0D;

    @Column(name = "detail_failure_rate")
    private double detailFailureRate = 0D;

    @Column(name = "concurrency_advice", columnDefinition = "TEXT")
    private String concurrencyAdvice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum TaskStatus {
        PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
    }

    public enum TaskType {
        SEARCH,
        ORIGINAL_IMAGE,
        HD_IMAGE,
        TRANSACTION_PRICE
    }
}
