package com.artfetch.service;

import com.artfetch.config.AppProperties;
import com.artfetch.entity.Artwork;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.service.extractor.ArtworkData;
import com.artfetch.service.extractor.InitialStateExtractor;
import com.artfetch.util.TextSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 手动全量重采估价和成交价。
 *
 * 默认跳过，避免普通测试误触发全库外网请求和数据库覆盖。
 * 启用方式：
 * ARTFETCH_RECOLLECT_PRICES=true mvn test -Dtest=ArtworkPriceRecollectionTest
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ARTFETCH_RECOLLECT_PRICES", matches = "true")
class ArtworkPriceRecollectionTest {

    private static final Logger log = LoggerFactory.getLogger(ArtworkPriceRecollectionTest.class);

    @Autowired
    private ArtworkRepository artworkRepository;

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private ArtronRequestSupport artronRequestSupport;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final InitialStateExtractor initialStateExtractor = new InitialStateExtractor();

    @Test
    void recollectValuationAndTransactionPriceForAllArtworks() throws Exception {
        Long taskId = optionalLong("ARTFETCH_RECOLLECT_PRICES_TASK_ID");
        Long artworkId = optionalLong("ARTFETCH_RECOLLECT_PRICES_ARTWORK_ID");
        int limit = optionalInt("ARTFETCH_RECOLLECT_PRICES_LIMIT", 0);
        int maxFailures = optionalInt("ARTFETCH_RECOLLECT_PRICES_MAX_FAILURES", 0);
        int concurrency = Math.max(1, optionalInt("ARTFETCH_RECOLLECT_PRICES_CONCURRENCY", 8));
        int progressEvery = Math.max(1, optionalInt("ARTFETCH_RECOLLECT_PRICES_PROGRESS_EVERY", 100));
        int skipSuccessfulPrefixCount = Math.max(0, optionalInt("ARTFETCH_RECOLLECT_PRICES_SKIP_SUCCESSFUL_PREFIX_COUNT", 0));
        int batchPauseEvery = Math.max(0, optionalInt("ARTFETCH_RECOLLECT_PRICES_BATCH_PAUSE_EVERY", 0));
        int batchPauseMs = Math.max(0, optionalInt("ARTFETCH_RECOLLECT_PRICES_BATCH_PAUSE_MS", 0));
        int staggerDelayMs = Math.max(0, optionalInt(
                "ARTFETCH_RECOLLECT_PRICES_STAGGER_DELAY_MS",
                Math.toIntExact(appProperties.getSource().getRequestDelayMs() / concurrency)
        ));

        List<Long> artworkIds = artworkId == null
                ? taskId == null
                        ? artworkRepository.findAllIdsOrderByIdAsc()
                        : artworkRepository.findIdsByTaskIdOrderByIdAsc(taskId)
                : List.of(artworkId);
        if (skipSuccessfulPrefixCount > 0 && artworkId == null) {
            artworkIds = skipSuccessfulPrefix(artworkIds, skipSuccessfulPrefixCount);
        }
        if (limit > 0 && artworkIds.size() > limit) {
            artworkIds = artworkIds.subList(0, limit);
        }

        assertThat(artworkIds)
                .as("待重采艺术品数量")
                .isNotEmpty();

        log.info("开始重采估价和成交价: scope={}, total={}, concurrency={}, maxFailures={}, batchPauseEvery={}, batchPauseMs={}",
                artworkId != null ? "artworkId=" + artworkId : taskId == null ? "ALL" : "taskId=" + taskId,
                artworkIds.size(),
                concurrency,
                maxFailures,
                batchPauseEvery,
                batchPauseMs);

        RecollectionStats stats = runRecollection(
                artworkIds,
                concurrency,
                progressEvery,
                staggerDelayMs,
                maxFailures,
                batchPauseEvery,
                batchPauseMs
        );

        log.info("重采完成: total={}, updated={}, unchanged={}, skipped={}, failed={}, valuationUpdated={}, transactionPriceUpdated={}, priceMissing={}, loginRequired={}",
                stats.total(),
                stats.updated(),
                stats.unchanged(),
                stats.skipped(),
                stats.failed(),
                stats.valuationUpdated(),
                stats.transactionPriceUpdated(),
                stats.priceMissing(),
                stats.loginRequired());

        assertThat(stats.failed())
                .as("失败数量超过 ARTFETCH_RECOLLECT_PRICES_MAX_FAILURES")
                .isLessThanOrEqualTo(maxFailures);
    }

    private RecollectionStats runRecollection(List<Long> artworkIds,
                                             int concurrency,
                                             int progressEvery,
                                             int staggerDelayMs,
                                             int maxFailures,
                                             int batchPauseEvery,
                                             int batchPauseMs) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        ExecutorCompletionService<RecollectionOutcome> completionService = new ExecutorCompletionService<>(executor);
        try {
            int submitted = 0;
            while (submitted < artworkIds.size() && submitted < concurrency) {
                submitted = submitNext(artworkIds, completionService, submitted, staggerDelayMs);
            }

            RecollectionStats stats = new RecollectionStats();
            for (int completed = 1; completed <= artworkIds.size(); completed++) {
                try {
                    stats.record(completionService.take().get());
                } catch (Exception e) {
                    stats.record(RecollectionOutcome.failed(null, "任务异常: " + e.getMessage()));
                }
                if (completed % progressEvery == 0 || completed == artworkIds.size()) {
                    log.info("重采进度: {}/{} updated={} unchanged={} skipped={} failed={}",
                            completed,
                            artworkIds.size(),
                            stats.updated(),
                            stats.unchanged(),
                            stats.skipped(),
                            stats.failed());
                }
                if (stats.failed() > maxFailures) {
                    log.error("重采失败数超过阈值，提前停止: completed={}/{} failed={} maxFailures={}",
                            completed,
                            artworkIds.size(),
                            stats.failed(),
                            maxFailures);
                    throw new AssertionError("失败数量超过 ARTFETCH_RECOLLECT_PRICES_MAX_FAILURES: "
                            + stats.failed() + " > " + maxFailures);
                }
                if (batchPauseEvery > 0 && batchPauseMs > 0
                        && completed % batchPauseEvery == 0
                        && completed < artworkIds.size()) {
                    log.info("重采批次暂停: completed={}/{} pauseMs={}", completed, artworkIds.size(), batchPauseMs);
                    Thread.sleep(batchPauseMs);
                }
                if (submitted < artworkIds.size()) {
                    submitted = submitNext(artworkIds, completionService, submitted, staggerDelayMs);
                }
            }
            return stats;
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Long> skipSuccessfulPrefix(List<Long> artworkIds, int prefixCount) {
        int safePrefixCount = Math.min(prefixCount, artworkIds.size());
        List<Long> filtered = new ArrayList<>(artworkIds.size() - safePrefixCount);
        int retainedFailures = 0;
        for (int i = 0; i < artworkIds.size(); i++) {
            Long artworkId = artworkIds.get(i);
            if (i < safePrefixCount && !hasFetchFailureNote(artworkId)) {
                continue;
            }
            if (i < safePrefixCount) {
                retainedFailures++;
            }
            filtered.add(artworkId);
        }
        log.info("跳过已成功前缀: prefixCount={}, retainedFailures={}, skippedSuccessful={}",
                safePrefixCount,
                retainedFailures,
                safePrefixCount - retainedFailures);
        return filtered;
    }

    private boolean hasFetchFailureNote(Long artworkId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                artworkRepository.findById(artworkId)
                        .map(artwork -> "抓取失败".equals(sanitize(artwork.getTransactionPriceNote())))
                        .orElse(false)
        ));
    }

    private int submitNext(List<Long> artworkIds,
                           ExecutorCompletionService<RecollectionOutcome> completionService,
                           int submitted,
                           int staggerDelayMs) throws InterruptedException {
        completionService.submit(() -> recollectArtwork(artworkIds.get(submitted)));
        int nextSubmitted = submitted + 1;
        if (staggerDelayMs > 0 && nextSubmitted < artworkIds.size()) {
            Thread.sleep(staggerDelayMs);
        }
        return nextSubmitted;
    }

    private RecollectionOutcome recollectArtwork(Long artworkId) {
        ArtworkSnapshot snapshot = transactionTemplate.execute(status ->
                artworkRepository.findById(artworkId)
                        .map(artwork -> new ArtworkSnapshot(
                                artwork.getId(),
                                artwork.getExternalId(),
                                artwork.getSourceUrl()
                        ))
                        .orElse(null)
        );
        if (snapshot == null) {
            return RecollectionOutcome.skipped(artworkId, "艺术品不存在");
        }
        if (snapshot.sourceUrl() == null || snapshot.sourceUrl().isBlank()) {
            updateTransactionPriceNote(artworkId, "缺少详情页地址");
            return RecollectionOutcome.failed(artworkId, "缺少详情页地址");
        }

        try {
            Document doc = artronRequestSupport.configure(
                            Jsoup.connect(snapshot.sourceUrl()),
                            "https://artso.artron.net/",
                            appProperties.getPrice().getFetchTimeoutMs()
                    )
                    .get();

            ArtworkData data = new ArtworkData();
            initialStateExtractor.extract(doc, data);

            return transactionTemplate.execute(status ->
                    artworkRepository.findById(artworkId)
                            .map(artwork -> applyExtractedPrices(artwork, data))
                            .orElseGet(() -> RecollectionOutcome.skipped(artworkId, "艺术品不存在"))
            );
        } catch (Exception e) {
            updateTransactionPriceNote(artworkId, "抓取失败");
            log.warn("重采估价/成交价失败: artworkId={}, externalId={}, sourceUrl={}, message={}",
                    snapshot.id(),
                    snapshot.externalId(),
                    snapshot.sourceUrl(),
                    e.getMessage());
            return RecollectionOutcome.failed(artworkId, e.getMessage());
        }
    }

    private RecollectionOutcome applyExtractedPrices(Artwork artwork, ArtworkData data) {
        boolean changed = false;
        boolean valuationUpdated = false;
        boolean transactionPriceUpdated = false;
        boolean priceMissing = false;
        boolean loginRequired = false;

        String valuation = sanitize(data.valuation);
        if (valuation != null && !Objects.equals(artwork.getValuation(), valuation)) {
            artwork.setValuation(valuation);
            changed = true;
            valuationUpdated = true;
        }

        String transactionPrice = sanitize(data.transactionPrice);
        if (transactionPrice != null) {
            if (!Objects.equals(artwork.getTransactionPrice(), transactionPrice)) {
                artwork.setTransactionPrice(transactionPrice);
                changed = true;
                transactionPriceUpdated = true;
            }
            if (artwork.getTransactionPriceNote() != null) {
                artwork.setTransactionPriceNote(null);
                changed = true;
            }
        } else {
            priceMissing = true;
            loginRequired = data.transactionPriceLoginRequired;
            String note = sanitize(TransactionPriceNoteHelper.noteForExtraction(data));
            if (note != null && !Objects.equals(artwork.getTransactionPriceNote(), note)) {
                artwork.setTransactionPriceNote(note);
                changed = true;
            }
        }

        if (changed) {
            artworkRepository.save(artwork);
            return RecollectionOutcome.updated(
                    artwork.getId(),
                    valuationUpdated,
                    transactionPriceUpdated,
                    priceMissing,
                    loginRequired
            );
        }
        return RecollectionOutcome.unchanged(artwork.getId(), priceMissing, loginRequired);
    }

    private void updateTransactionPriceNote(Long artworkId, String note) {
        transactionTemplate.executeWithoutResult(status ->
                artworkRepository.findById(artworkId).ifPresent(artwork -> {
                    artwork.setTransactionPriceNote(sanitize(note));
                    artworkRepository.save(artwork);
                })
        );
    }

    private String sanitize(String value) {
        return TextSanitizer.sanitize(value).value();
    }

    private static Long optionalLong(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    private static int optionalInt(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private record ArtworkSnapshot(Long id, String externalId, String sourceUrl) {
    }

    private record RecollectionOutcome(
            Long artworkId,
            Status status,
            boolean valuationUpdated,
            boolean transactionPriceUpdated,
            boolean priceMissing,
            boolean loginRequired,
            String message
    ) {
        static RecollectionOutcome updated(Long artworkId,
                                           boolean valuationUpdated,
                                           boolean transactionPriceUpdated,
                                           boolean priceMissing,
                                           boolean loginRequired) {
            return new RecollectionOutcome(
                    artworkId,
                    Status.UPDATED,
                    valuationUpdated,
                    transactionPriceUpdated,
                    priceMissing,
                    loginRequired,
                    null
            );
        }

        static RecollectionOutcome unchanged(Long artworkId, boolean priceMissing, boolean loginRequired) {
            return new RecollectionOutcome(
                    artworkId,
                    Status.UNCHANGED,
                    false,
                    false,
                    priceMissing,
                    loginRequired,
                    null
            );
        }

        static RecollectionOutcome skipped(Long artworkId, String message) {
            return new RecollectionOutcome(artworkId, Status.SKIPPED, false, false, false, false, message);
        }

        static RecollectionOutcome failed(Long artworkId, String message) {
            return new RecollectionOutcome(artworkId, Status.FAILED, false, false, false, false, message);
        }
    }

    private enum Status {
        UPDATED,
        UNCHANGED,
        SKIPPED,
        FAILED
    }

    private static final class RecollectionStats {
        private int total;
        private int updated;
        private int unchanged;
        private int skipped;
        private int failed;
        private int valuationUpdated;
        private int transactionPriceUpdated;
        private int priceMissing;
        private int loginRequired;

        void record(RecollectionOutcome outcome) {
            total++;
            if (outcome == null) {
                failed++;
                return;
            }
            switch (outcome.status()) {
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
            if (outcome.valuationUpdated()) {
                valuationUpdated++;
            }
            if (outcome.transactionPriceUpdated()) {
                transactionPriceUpdated++;
            }
            if (outcome.priceMissing()) {
                priceMissing++;
            }
            if (outcome.loginRequired()) {
                loginRequired++;
            }
        }

        int total() {
            return total;
        }

        int updated() {
            return updated;
        }

        int unchanged() {
            return unchanged;
        }

        int skipped() {
            return skipped;
        }

        int failed() {
            return failed;
        }

        int valuationUpdated() {
            return valuationUpdated;
        }

        int transactionPriceUpdated() {
            return transactionPriceUpdated;
        }

        int priceMissing() {
            return priceMissing;
        }

        int loginRequired() {
            return loginRequired;
        }
    }
}
