package com.artfetch.service;

import com.artfetch.entity.SearchTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class TaskPerformanceTracker {

    private static final int RECENT_LATENCY_WINDOW = 240;

    private final int detailFetchConcurrency;
    private final Deque<Long> recentLatencyMs = new ArrayDeque<>();

    private long detailRequestCount;
    private long detailSuccessCount;
    private long detailFailureCount;
    private long detailLatencyTotalMs;
    private long maxDetailLatencyMs;
    private long lastPageDurationMs;
    private double lastPageItemsPerMinute;
    private long lastKnownP95LatencyMs;

    private TaskPerformanceTracker(SearchTask task, int detailFetchConcurrency) {
        this.detailFetchConcurrency = detailFetchConcurrency;
        this.detailRequestCount = task.getDetailRequestCount();
        this.detailSuccessCount = task.getDetailSuccessCount();
        this.detailFailureCount = task.getDetailFailureCount();
        this.detailLatencyTotalMs = task.getAvgDetailLatencyMs() * task.getDetailRequestCount();
        this.maxDetailLatencyMs = task.getMaxDetailLatencyMs();
        this.lastPageDurationMs = task.getLastPageDurationMs();
        this.lastPageItemsPerMinute = task.getLastPageItemsPerMinute();
        this.lastKnownP95LatencyMs = task.getP95DetailLatencyMs();
    }

    public static TaskPerformanceTracker fromTask(SearchTask task, int detailFetchConcurrency) {
        return new TaskPerformanceTracker(task, detailFetchConcurrency);
    }

    public TaskPerformanceSnapshot recordPage(PagePerformanceMetrics metrics, int savedCount) {
        detailRequestCount += metrics.getRequestCount();
        detailSuccessCount += metrics.getSuccessCount();
        detailFailureCount += metrics.getFailureCount();
        detailLatencyTotalMs += metrics.getTotalLatencyMs();
        maxDetailLatencyMs = Math.max(maxDetailLatencyMs, metrics.getMaxLatencyMs());
        lastPageDurationMs = metrics.getPageDurationMs();
        if (savedCount > 0 && lastPageDurationMs > 0) {
            lastPageItemsPerMinute = savedCount * 60_000.0 / lastPageDurationMs;
        }

        for (Long latency : metrics.getLatenciesMs()) {
            if (latency == null) {
                continue;
            }
            recentLatencyMs.addLast(latency);
            while (recentLatencyMs.size() > RECENT_LATENCY_WINDOW) {
                recentLatencyMs.removeFirst();
            }
        }

        return snapshot();
    }

    public TaskPerformanceSnapshot snapshot() {
        long avgDetailLatencyMs = detailRequestCount > 0
                ? Math.round((double) detailLatencyTotalMs / detailRequestCount)
                : 0L;
        long p95LatencyMs = recentLatencyMs.isEmpty()
                ? lastKnownP95LatencyMs
                : percentile95(recentLatencyMs);
        lastKnownP95LatencyMs = p95LatencyMs;
        double failureRate = detailRequestCount > 0
                ? (double) detailFailureCount / detailRequestCount
                : 0D;

        return TaskPerformanceSnapshot.builder()
                .detailFetchConcurrency(detailFetchConcurrency)
                .detailRequestCount(detailRequestCount)
                .detailSuccessCount(detailSuccessCount)
                .detailFailureCount(detailFailureCount)
                .avgDetailLatencyMs(avgDetailLatencyMs)
                .p95DetailLatencyMs(p95LatencyMs)
                .maxDetailLatencyMs(maxDetailLatencyMs)
                .lastPageDurationMs(lastPageDurationMs)
                .lastPageItemsPerMinute(lastPageItemsPerMinute)
                .detailFailureRate(failureRate)
                .concurrencyAdvice(buildAdvice(detailFetchConcurrency, detailRequestCount, failureRate,
                        avgDetailLatencyMs, p95LatencyMs, lastPageItemsPerMinute))
                .build();
    }

    private long percentile95(Deque<Long> samples) {
        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private String buildAdvice(int concurrency,
                               long requestCount,
                               double failureRate,
                               long avgLatencyMs,
                               long p95LatencyMs,
                               double itemsPerMinute) {
        if (requestCount < Math.max(20, concurrency * 10L)) {
            return "样本不足，先保持当前并发";
        }
        if (failureRate >= 0.12 || p95LatencyMs >= 15_000) {
            return "失败率或尾延迟偏高，不建议继续加并发";
        }
        if (failureRate <= 0.03 && avgLatencyMs <= 4_000 && p95LatencyMs <= 8_000 && itemsPerMinute > 0) {
            return "可以尝试将详情并发 +1";
        }
        return "建议维持当前并发";
    }
}
