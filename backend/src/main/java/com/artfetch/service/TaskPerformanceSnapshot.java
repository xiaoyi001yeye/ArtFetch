package com.artfetch.service;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TaskPerformanceSnapshot {

    private final int detailFetchConcurrency;
    private final long detailRequestCount;
    private final long detailSuccessCount;
    private final long detailFailureCount;
    private final long avgDetailLatencyMs;
    private final long p95DetailLatencyMs;
    private final long maxDetailLatencyMs;
    private final long lastPageDurationMs;
    private final double lastPageItemsPerMinute;
    private final double detailFailureRate;
    private final String concurrencyAdvice;
}
