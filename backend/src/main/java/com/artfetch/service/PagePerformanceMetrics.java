package com.artfetch.service;

import lombok.Getter;

import java.util.List;

@Getter
public class PagePerformanceMetrics {

    private final int requestCount;
    private final int successCount;
    private final int failureCount;
    private final long pageDurationMs;
    private final long totalLatencyMs;
    private final long maxLatencyMs;
    private final List<Long> latenciesMs;

    public PagePerformanceMetrics(int requestCount,
                                  int successCount,
                                  int failureCount,
                                  long pageDurationMs,
                                  long totalLatencyMs,
                                  long maxLatencyMs,
                                  List<Long> latenciesMs) {
        this.requestCount = requestCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.pageDurationMs = pageDurationMs;
        this.totalLatencyMs = totalLatencyMs;
        this.maxLatencyMs = maxLatencyMs;
        this.latenciesMs = latenciesMs;
    }
}
