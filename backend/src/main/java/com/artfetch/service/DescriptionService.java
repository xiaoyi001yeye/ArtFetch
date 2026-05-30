package com.artfetch.service;

import com.artfetch.config.AppProperties;
import com.artfetch.entity.Artwork;
import com.artfetch.entity.SearchTask;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.repository.SearchTaskRepository;
import com.artfetch.service.extractor.ArtworkData;
import com.artfetch.service.extractor.FieldExtractorChain;
import com.artfetch.util.TextSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class DescriptionService {

    private final ArtworkRepository artworkRepository;
    private final SearchTaskRepository taskRepository;
    private final AppProperties appProperties;
    private final ArtronRequestSupport artronRequestSupport;
    private final FieldExtractorChain extractorChain = new FieldExtractorChain();

    public DescriptionTaskResult runTask(SearchTask task) throws InterruptedException {
        if (task.getTargetTaskId() == null) {
            throw new IllegalStateException("补充拍品描述任务缺少目标检索任务");
        }

        List<Long> pendingArtworkIds = artworkRepository.findMissingDescriptionIdsByTaskIdOrderByIdAsc(task.getTargetTaskId());
        int totalCount = pendingArtworkIds.size();
        int fetchConcurrency = Math.max(1, appProperties.getDescription().getFetchConcurrency());
        int batchSize = Math.max(fetchConcurrency, appProperties.getDescription().getBatchSize());
        TaskPerformanceTracker performanceTracker = TaskPerformanceTracker.fromTask(task, fetchConcurrency);
        updateTaskProgressAndMetrics(task.getId(), 0, totalCount, 0, performanceTracker.snapshot());

        int processed = 0;
        int updated = 0;
        int missing = 0;
        int failed = 0;
        ExecutorService descriptionExecutor = createDescriptionExecutor(task.getId(), fetchConcurrency);

        try {
            for (int start = 0; start < pendingArtworkIds.size(); start += batchSize) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Task interrupted");
                }

                int batchIndex = start / batchSize + 1;
                int totalBatches = (int) Math.ceil((double) pendingArtworkIds.size() / batchSize);
                List<Long> batchIds = pendingArtworkIds.subList(start, Math.min(start + batchSize, pendingArtworkIds.size()));
                List<Artwork> batchArtworks = artworkRepository.findByIdInOrderByIdAsc(batchIds);
                BatchRunResult batchResult = supplementBatchConcurrently(
                        batchArtworks,
                        task.getId(),
                        batchIndex,
                        totalBatches,
                        fetchConcurrency,
                        descriptionExecutor
                );

                processed += batchArtworks.size();
                updated += batchResult.updatedCount();
                missing += batchResult.missingCount();
                failed += batchResult.failedCount();

                TaskPerformanceSnapshot snapshot = performanceTracker.recordPage(
                        batchResult.pageMetrics(),
                        batchResult.updatedCount()
                );
                updateTaskProgressAndMetrics(task.getId(), processed, totalCount, updated, snapshot);

                log.info("拍品描述补充进度: taskId={}, batch={}/{}, processed={}, total={}, updated={}, missing={}, failed={}, concurrency={}, batchDuration={}ms",
                        task.getId(),
                        batchIndex,
                        totalBatches,
                        processed,
                        totalCount,
                        updated,
                        missing,
                        failed,
                        fetchConcurrency,
                        batchResult.pageMetrics().getPageDurationMs());
            }
        } finally {
            descriptionExecutor.shutdownNow();
        }

        return new DescriptionTaskResult(totalCount, processed, updated, missing, failed);
    }

    private BatchRunResult supplementBatchConcurrently(List<Artwork> batchArtworks,
                                                       Long taskId,
                                                       int batchIndex,
                                                       int totalBatches,
                                                       int fetchConcurrency,
                                                       ExecutorService descriptionExecutor) throws InterruptedException {
        long batchStart = System.nanoTime();
        long staggerDelayMs = calculateStaggerDelayMs(fetchConcurrency);
        ExecutorCompletionService<DescriptionFetchResult> completionService =
                new ExecutorCompletionService<>(descriptionExecutor);
        List<Future<DescriptionFetchResult>> futures = new ArrayList<>();

        for (int i = 0; i < batchArtworks.size(); i++) {
            Artwork artwork = batchArtworks.get(i);
            if (Thread.currentThread().isInterrupted()) {
                cancelFutures(futures);
                throw new InterruptedException("Task interrupted");
            }

            futures.add(completionService.submit(() -> supplementDescriptionWithMetrics(artwork.getId())));
            if (staggerDelayMs > 0 && i < batchArtworks.size() - 1) {
                Thread.sleep(staggerDelayMs);
            }
        }

        int updatedCount = 0;
        int missingCount = 0;
        int failedCount = 0;
        int successCount = 0;
        long totalLatencyMs = 0;
        long maxLatencyMs = 0;
        List<Long> latencies = new ArrayList<>(batchArtworks.size());

        for (int i = 0; i < batchArtworks.size(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                cancelFutures(futures);
                throw new InterruptedException("Task interrupted");
            }

            try {
                DescriptionFetchResult result = completionService.take().get();
                totalLatencyMs += result.durationMs();
                maxLatencyMs = Math.max(maxLatencyMs, result.durationMs());
                latencies.add(result.durationMs());

                switch (result.outcome()) {
                    case UPDATED -> {
                        updatedCount++;
                        successCount++;
                    }
                    case MISSING -> {
                        missingCount++;
                        successCount++;
                    }
                    case FAILED -> failedCount++;
                }
            } catch (ExecutionException e) {
                log.warn("拍品描述补充批次异常: taskId={}, batch={}/{}, message={}",
                        taskId,
                        batchIndex,
                        totalBatches,
                        e.getCause() != null ? e.getCause().getMessage() : e.getMessage(),
                        e);
                failedCount++;
            }
        }

        PagePerformanceMetrics pageMetrics = new PagePerformanceMetrics(
                batchArtworks.size(),
                successCount,
                failedCount,
                nanosToMillis(System.nanoTime() - batchStart),
                totalLatencyMs,
                maxLatencyMs,
                latencies
        );
        return new BatchRunResult(updatedCount, missingCount, failedCount, pageMetrics);
    }

    private DescriptionFetchResult supplementDescriptionWithMetrics(Long artworkId) {
        long start = System.nanoTime();
        UpdateOutcome outcome = supplementDescription(artworkId);
        return new DescriptionFetchResult(outcome, nanosToMillis(System.nanoTime() - start));
    }

    @Transactional
    protected UpdateOutcome supplementDescription(Long artworkId) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));

        if (artwork.getDescription() != null && !artwork.getDescription().isBlank()) {
            return UpdateOutcome.UPDATED;
        }
        if (artwork.getSourceUrl() == null || artwork.getSourceUrl().isBlank()) {
            log.warn("拍品描述补充失败: artworkId={}, externalId={}, reason=缺少详情页地址",
                    artwork.getId(), artwork.getExternalId());
            return UpdateOutcome.FAILED;
        }

        try {
            Document doc = artronRequestSupport.configure(
                            Jsoup.connect(artwork.getSourceUrl()),
                            "https://artso.artron.net/",
                            appProperties.getDescription().getFetchTimeoutMs()
                    )
                    .get();

            ArtworkData data = new ArtworkData();
            extractorChain.extractAll(doc, data);

            if (data.description != null && !data.description.isBlank()) {
                artwork.setDescription(sanitizeText("description", data.description, artwork));
                artworkRepository.save(artwork);
                log.info("拍品描述补充成功: artworkId={}, externalId={}",
                        artwork.getId(), artwork.getExternalId());
                return UpdateOutcome.UPDATED;
            }

            log.warn("拍品描述补充未完成: artworkId={}, externalId={}, reason=详情页未返回描述, sourceUrl={}",
                    artwork.getId(), artwork.getExternalId(), artwork.getSourceUrl());
            return UpdateOutcome.MISSING;
        } catch (Exception e) {
            log.warn("拍品描述补充失败: artworkId={}, externalId={}, sourceUrl={}, message={}",
                    artwork.getId(), artwork.getExternalId(), artwork.getSourceUrl(), e.getMessage(), e);
            return UpdateOutcome.FAILED;
        }
    }

    @Transactional
    protected void updateTaskProgressAndMetrics(Long taskId,
                                                int processedCount,
                                                int totalCount,
                                                int updatedCount,
                                                TaskPerformanceSnapshot snapshot) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setCurrentPage(processedCount);
            task.setTotalPages(totalCount);
            task.setTotalFetched(updatedCount);
            applyMetrics(task, snapshot);
            taskRepository.save(task);
        });
    }

    private void applyMetrics(SearchTask task, TaskPerformanceSnapshot snapshot) {
        task.setDetailFetchConcurrency(snapshot.getDetailFetchConcurrency());
        task.setDetailRequestCount(snapshot.getDetailRequestCount());
        task.setDetailSuccessCount(snapshot.getDetailSuccessCount());
        task.setDetailFailureCount(snapshot.getDetailFailureCount());
        task.setAvgDetailLatencyMs(snapshot.getAvgDetailLatencyMs());
        task.setP95DetailLatencyMs(snapshot.getP95DetailLatencyMs());
        task.setMaxDetailLatencyMs(snapshot.getMaxDetailLatencyMs());
        task.setLastPageDurationMs(snapshot.getLastPageDurationMs());
        task.setLastPageItemsPerMinute(snapshot.getLastPageItemsPerMinute());
        task.setDetailFailureRate(snapshot.getDetailFailureRate());
        task.setConcurrencyAdvice(snapshot.getConcurrencyAdvice());
    }

    private ExecutorService createDescriptionExecutor(Long taskId, int fetchConcurrency) {
        AtomicInteger threadSeq = new AtomicInteger(1);
        return Executors.newFixedThreadPool(fetchConcurrency, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("artfetch-description-" + taskId + "-" + threadSeq.getAndIncrement());
            return thread;
        });
    }

    private void cancelFutures(List<Future<DescriptionFetchResult>> futures) {
        for (Future<DescriptionFetchResult> future : futures) {
            future.cancel(true);
        }
    }

    private long calculateStaggerDelayMs(int fetchConcurrency) {
        return Math.max(0, appProperties.getSource().getRequestDelayMs() / Math.max(1, fetchConcurrency));
    }

    private long nanosToMillis(long nanos) {
        return Math.max(1L, nanos / 1_000_000L);
    }

    private String sanitizeText(String fieldName, String value, Artwork artwork) {
        TextSanitizer.SanitizedText sanitized = TextSanitizer.sanitize(value);
        if (sanitized.removedIllegalChars() > 0) {
            log.warn("拍品描述补充字段已清洗: artworkId={}, externalId={}, field={}, removedIllegalChars={}",
                    artwork.getId(),
                    artwork.getExternalId(),
                    fieldName,
                    sanitized.removedIllegalChars());
        }
        return sanitized.value();
    }

    private enum UpdateOutcome {
        UPDATED,
        MISSING,
        FAILED
    }

    private record DescriptionFetchResult(UpdateOutcome outcome, long durationMs) {
    }

    private record BatchRunResult(int updatedCount,
                                  int missingCount,
                                  int failedCount,
                                  PagePerformanceMetrics pageMetrics) {
    }
}
