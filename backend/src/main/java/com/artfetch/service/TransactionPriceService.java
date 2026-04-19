package com.artfetch.service;

import com.artfetch.config.AppProperties;
import com.artfetch.dto.ArtworkDto;
import com.artfetch.entity.Artwork;
import com.artfetch.entity.SearchTask;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.repository.SearchTaskRepository;
import com.artfetch.service.extractor.ArtworkData;
import com.artfetch.service.extractor.InitialStateExtractor;
import com.artfetch.util.TextSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionPriceService {

    private final ArtworkRepository artworkRepository;
    private final SearchTaskRepository taskRepository;
    private final AppProperties appProperties;
    private final ArtronRequestSupport artronRequestSupport;
    private final InitialStateExtractor initialStateExtractor = new InitialStateExtractor();

    @Transactional
    public ArtworkDto supplementSingleArtwork(Long artworkId) {
        supplementTransactionPrice(artworkId);
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));
        return ArtworkDto.from(artwork);
    }

    public TransactionPriceTaskResult runTask(SearchTask task) throws InterruptedException {
        if (task.getTargetTaskId() == null) {
            throw new IllegalStateException("补充成交价任务缺少目标检索任务");
        }

        List<Long> pendingArtworkIds = artworkRepository.findMissingTransactionPriceIdsByTaskIdOrderByIdAsc(task.getTargetTaskId());
        int totalCount = pendingArtworkIds.size();
        int fetchConcurrency = Math.max(1, appProperties.getPrice().getFetchConcurrency());
        int batchSize = Math.max(fetchConcurrency, appProperties.getPrice().getBatchSize());
        TaskPerformanceTracker performanceTracker = TaskPerformanceTracker.fromTask(task, fetchConcurrency);
        updateTaskProgressAndMetrics(task.getId(), 0, totalCount, 0, performanceTracker.snapshot());

        int processed = 0;
        int updated = 0;
        int loginRequired = 0;
        int missing = 0;
        int failed = 0;
        ExecutorService priceExecutor = createPriceExecutor(task.getId(), fetchConcurrency);

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
                        priceExecutor
                );

                processed += batchArtworks.size();
                updated += batchResult.updatedCount();
                loginRequired += batchResult.loginRequiredCount();
                missing += batchResult.missingCount();
                failed += batchResult.failedCount();

                TaskPerformanceSnapshot snapshot = performanceTracker.recordPage(
                        batchResult.pageMetrics(),
                        batchResult.updatedCount()
                );
                updateTaskProgressAndMetrics(task.getId(), processed, totalCount, updated, snapshot);

                log.info("成交价补充进度: taskId={}, batch={}/{}, processed={}, total={}, updated={}, loginRequired={}, missing={}, failed={}, concurrency={}, batchDuration={}ms",
                        task.getId(),
                        batchIndex,
                        totalBatches,
                        processed,
                        totalCount,
                        updated,
                        loginRequired,
                        missing,
                        failed,
                        fetchConcurrency,
                        batchResult.pageMetrics().getPageDurationMs());
            }
        } finally {
            priceExecutor.shutdownNow();
        }

        return new TransactionPriceTaskResult(totalCount, processed, updated, loginRequired, missing, failed);
    }

    private BatchRunResult supplementBatchConcurrently(List<Artwork> batchArtworks,
                                                       Long taskId,
                                                       int batchIndex,
                                                       int totalBatches,
                                                       int fetchConcurrency,
                                                       ExecutorService priceExecutor) throws InterruptedException {
        long batchStart = System.nanoTime();
        long staggerDelayMs = calculateStaggerDelayMs(fetchConcurrency);
        ExecutorCompletionService<TransactionPriceFetchResult> completionService =
                new ExecutorCompletionService<>(priceExecutor);
        List<Future<TransactionPriceFetchResult>> futures = new ArrayList<>();

        for (int i = 0; i < batchArtworks.size(); i++) {
            Artwork artwork = batchArtworks.get(i);
            if (Thread.currentThread().isInterrupted()) {
                cancelFutures(futures);
                throw new InterruptedException("Task interrupted");
            }

            futures.add(completionService.submit(() -> supplementTransactionPriceWithMetrics(artwork.getId())));
            if (staggerDelayMs > 0 && i < batchArtworks.size() - 1) {
                Thread.sleep(staggerDelayMs);
            }
        }

        int updatedCount = 0;
        int loginRequiredCount = 0;
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
                TransactionPriceFetchResult result = completionService.take().get();
                totalLatencyMs += result.durationMs();
                maxLatencyMs = Math.max(maxLatencyMs, result.durationMs());
                latencies.add(result.durationMs());

                switch (result.outcome()) {
                    case UPDATED -> {
                        updatedCount++;
                        successCount++;
                    }
                    case LOGIN_REQUIRED -> {
                        loginRequiredCount++;
                        successCount++;
                    }
                    case MISSING -> {
                        missingCount++;
                        successCount++;
                    }
                    case FAILED -> failedCount++;
                }
            } catch (ExecutionException e) {
                log.warn("成交价补充批次异常: taskId={}, batch={}/{}, message={}",
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
        return new BatchRunResult(updatedCount, loginRequiredCount, missingCount, failedCount, pageMetrics);
    }

    private TransactionPriceFetchResult supplementTransactionPriceWithMetrics(Long artworkId) {
        long start = System.nanoTime();
        UpdateOutcome outcome = supplementTransactionPrice(artworkId);
        return new TransactionPriceFetchResult(outcome, nanosToMillis(System.nanoTime() - start));
    }

    @Transactional
    protected UpdateOutcome supplementTransactionPrice(Long artworkId) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));

        if (artwork.getTransactionPrice() != null && !artwork.getTransactionPrice().isBlank()) {
            if (artwork.getTransactionPriceNote() != null && !artwork.getTransactionPriceNote().isBlank()) {
                artwork.setTransactionPriceNote(null);
                artworkRepository.save(artwork);
            }
            return UpdateOutcome.UPDATED;
        }
        if (artwork.getSourceUrl() == null || artwork.getSourceUrl().isBlank()) {
            artwork.setTransactionPriceNote("缺少详情页地址");
            artworkRepository.save(artwork);
            log.warn("成交价补充失败: artworkId={}, externalId={}, reason=缺少详情页地址",
                    artwork.getId(), artwork.getExternalId());
            return UpdateOutcome.FAILED;
        }

        try {
            Document doc = artronRequestSupport.configure(
                            Jsoup.connect(artwork.getSourceUrl()),
                            "https://artso.artron.net/",
                            appProperties.getPrice().getFetchTimeoutMs()
                    )
                    .get();

            ArtworkData data = new ArtworkData();
            initialStateExtractor.extract(doc, data);

            if (data.transactionPrice != null && !data.transactionPrice.isBlank()) {
                artwork.setTransactionPrice(sanitizeText("transactionPrice", data.transactionPrice, artwork));
                artwork.setTransactionPriceNote(null);
                artworkRepository.save(artwork);
                log.info("成交价补充成功: artworkId={}, externalId={}, transactionPrice={}",
                        artwork.getId(), artwork.getExternalId(), artwork.getTransactionPrice());
                return UpdateOutcome.UPDATED;
            }

            if (data.transactionPriceLoginRequired) {
                String reason = artronRequestSupport.hasAuthCookie()
                        ? "需要登录（已配置Cookie，可能已失效）"
                        : "需要登录（未配置Cookie）";
                artwork.setTransactionPriceNote(sanitizeText("transactionPriceNote", "需要登录", artwork));
                artworkRepository.save(artwork);
                log.warn("成交价补充未完成: artworkId={}, externalId={}, reason={}, sourceUrl={}",
                        artwork.getId(), artwork.getExternalId(), reason, artwork.getSourceUrl());
                return UpdateOutcome.LOGIN_REQUIRED;
            }

            String note = TransactionPriceNoteHelper.normalize(data.transactionPriceMessage);
            artwork.setTransactionPriceNote(sanitizeText("transactionPriceNote", note != null ? note : "页面未提供", artwork));
            artworkRepository.save(artwork);
            log.warn("成交价补充未完成: artworkId={}, externalId={}, reason={}, sourceUrl={}",
                    artwork.getId(),
                    artwork.getExternalId(),
                    data.transactionPriceMessage != null ? data.transactionPriceMessage : "详情页未返回成交价",
                    artwork.getSourceUrl());
            return UpdateOutcome.MISSING;
        } catch (Exception e) {
            artwork.setTransactionPriceNote(sanitizeText("transactionPriceNote", "抓取失败", artwork));
            artworkRepository.save(artwork);
            log.warn("成交价补充失败: artworkId={}, externalId={}, sourceUrl={}, message={}",
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

    private ExecutorService createPriceExecutor(Long taskId, int fetchConcurrency) {
        AtomicInteger threadSeq = new AtomicInteger(1);
        return Executors.newFixedThreadPool(fetchConcurrency, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("artfetch-price-" + taskId + "-" + threadSeq.getAndIncrement());
            return thread;
        });
    }

    private void cancelFutures(List<Future<TransactionPriceFetchResult>> futures) {
        for (Future<TransactionPriceFetchResult> future : futures) {
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
            log.warn("成交价补充字段已清洗: artworkId={}, externalId={}, field={}, removedIllegalChars={}",
                    artwork.getId(),
                    artwork.getExternalId(),
                    fieldName,
                    sanitized.removedIllegalChars());
        }
        return sanitized.value();
    }

    private enum UpdateOutcome {
        UPDATED,
        LOGIN_REQUIRED,
        MISSING,
        FAILED
    }

    private record TransactionPriceFetchResult(UpdateOutcome outcome, long durationMs) {
    }

    private record BatchRunResult(int updatedCount,
                                  int loginRequiredCount,
                                  int missingCount,
                                  int failedCount,
                                  PagePerformanceMetrics pageMetrics) {
    }
}
