package com.artfetch.service;

import com.artfetch.config.AppProperties;
import com.artfetch.entity.Artwork;
import com.artfetch.entity.FetchFailure;
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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从雅昌艺搜（artso.artron.net）抓取拍卖艺术品数据。
 *
 * URL 格式：
 *   https://artso.artron.net/auction/search_auction.php?keyword=张大千&page=1
 *
 * 列表页解析逻辑：
 *   - 总页数：HTML 中 <b class="fmE">N</b>页
 *   - 每件拍品：.listImg ul li
 *     - 详情URL + 拍品ID：<a href="https://auction.artron.net/paimai-artXXX">
 *     - 标题（含拍品号）：<h3><a>[拍品号]艺术家 作品名</a></h3>
 *     - 估价/成交价：<i class="fmE fred fb">N万</i>
 *     - 拍卖行：<a href="...org_detail...">拍卖行名</a>
 *     - 拍卖日期：文本节点
 *     - 图片：JS 变量 get_arr([{url:...},...]) 中第一张
 *
 * 详情页解析逻辑：
 *   - 从 https://auction.artron.net/paimai-artXXX 获取
 *   - 使用 FieldExtractorChain 提取所有字段
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FetchService {

    private static final Pattern TOTAL_PAGES_PATTERN =
            Pattern.compile("共有<b[^>]*>(\\d+)</b>页");
    private static final Pattern IMG_URL_PATTERN =
            Pattern.compile("\"url\":\"(https://thumb\\.artron\\.net[^\"]+)\"");
    private static final Pattern LOT_TITLE_PATTERN =
            Pattern.compile("^\\[(\\d+)\\](.*)$");

    private final AppProperties appProperties;
    private final ArtworkRepository artworkRepository;
    private final SearchTaskRepository taskRepository;
    private final FetchFailureService fetchFailureService;
    private final ArtronRequestSupport artronRequestSupport;

    private final FieldExtractorChain extractorChain = new FieldExtractorChain();

    /**
     * 执行一轮完整抓取：从第1页到最后一页，逐页解析并保存。
     * 调用方负责捕获 InterruptedException 以支持暂停/取消。
     */
    public FetchRunResult fetchAll(SearchTask task) throws InterruptedException {
        AppProperties.Source cfg = appProperties.getSource();
        int detailConcurrency = Math.max(1, cfg.getDetailFetchConcurrency());
        int totalNewCount = 0;
        int totalPages = 0;
        int failedListPages = 0;
        int failedDetailItems = 0;
        TaskPerformanceTracker performanceTracker = TaskPerformanceTracker.fromTask(task, detailConcurrency);
        ExecutorService detailExecutor = createDetailExecutor(task.getId(), detailConcurrency);

        log.info("Task[{}] 开始抓取，关键词：{}", task.getId(), task.getKeyword());

        int startPage = Math.max(1, task.getCurrentPage() == 0 ? 1 : task.getCurrentPage());
        updateTaskMetrics(task.getId(), performanceTracker.snapshot());

        try {
            for (int page = startPage; ; page++) {
                if (totalPages > 0 && page > totalPages) {
                    break;
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Task interrupted");
                }

                String pageUrl = buildListPageUrl(task.getKeyword(), page, cfg);
                String html;
                try {
                    html = fetchPage(task.getId(), pageUrl);
                } catch (Exception e) {
                    failedListPages++;
                    fetchFailureService.recordListPageFailure(task.getId(), page, pageUrl, e);
                    log.warn("Task[{}] 第{}页请求失败，跳过并继续后续页面", task.getId(), page);
                    if (totalPages == 0) {
                        return FetchRunResult.incomplete(totalNewCount,
                                failedListPages,
                                failedDetailItems,
                                "列表页抓取失败，无法确定总页数: page=" + page + ", error=" + e.getMessage());
                    }
                    continue;
                }

                if (totalPages == 0) {
                    totalPages = parseTotalPages(html);
                    if (totalPages == 0) {
                        log.warn("Task[{}] 未能解析到总页数，可能关键词无结果或被反爬", task.getId());
                        return FetchRunResult.incomplete(totalNewCount,
                                failedListPages,
                                failedDetailItems,
                                "未能解析到总页数，抓取未完成");
                    }
                    log.info("Task[{}] 共 {} 页，详情并发 {}", task.getId(), totalPages, detailConcurrency);
                    updateTaskProgress(task, page, totalPages, task.getTotalFetched());
                }

                List<ArtworkData> artworks = parseArtworks(html);
                if (artworks.isEmpty()) {
                    IllegalStateException e = new IllegalStateException("列表页解析为空");
                    failedListPages++;
                    fetchFailureService.recordListPageFailure(task.getId(), page, pageUrl, e);
                    log.warn("Task[{}] 第{}页解析为空，跳过并继续后续页面", task.getId(), page);
                    continue;
                }

                PagePerformanceMetrics pageMetrics = enrichPageDetailsConcurrently(
                        artworks, task.getId(), page, cfg, detailExecutor);
                failedDetailItems += pageMetrics.getFailureCount();

                int saved = saveArtworks(task, artworks);
                totalNewCount += saved;
                int newTotal = task.getTotalFetched() + saved;
                TaskPerformanceSnapshot snapshot = performanceTracker.recordPage(pageMetrics, saved);
                updateTaskProgressAndMetrics(task, page, totalPages, newTotal, snapshot);

                log.info("Task[{}] 第{}/{}页，解析{}条，新增/更新{}条，累计{}条，详情并发={}，页面耗时={}ms，详情均值={}ms，P95={}ms，失败率={}%，吞吐={}条/分，建议={}",
                        task.getId(),
                        page,
                        totalPages,
                        artworks.size(),
                        saved,
                        newTotal,
                        snapshot.getDetailFetchConcurrency(),
                        snapshot.getLastPageDurationMs(),
                        snapshot.getAvgDetailLatencyMs(),
                        snapshot.getP95DetailLatencyMs(),
                        formatPercent(snapshot.getDetailFailureRate()),
                        formatRate(snapshot.getLastPageItemsPerMinute()),
                        snapshot.getConcurrencyAdvice());

                if (cfg.getRequestDelayMs() > 0) {
                    Thread.sleep(cfg.getRequestDelayMs());
                }
            }
        } finally {
            detailExecutor.shutdownNow();
        }

        log.info("Task[{}] 所有页面抓取完毕，本轮新增 {} 条，列表页失败 {} 条，详情页失败 {} 条",
                task.getId(), totalNewCount, failedListPages, failedDetailItems);
        return FetchRunResult.completed(totalNewCount, failedListPages, failedDetailItems);
    }

    // ---- 网络请求 -------------------------------------------------------

    private String buildListPageUrl(String keyword, int page, AppProperties.Source cfg) {
        return cfg.getBaseUrl() + "?keyword=" + encode(keyword) + "&page=" + page;
    }

    private String fetchPage(Long taskId, String url) throws Exception {
        log.debug("Task[{}] GET {}", taskId, url);

        org.jsoup.Connection.Response response = artronRequestSupport.configure(
                        Jsoup.connect(url),
                        "https://artso.artron.net/",
                        30_000
                )
                .ignoreContentType(true)
                .execute();

        return response.body();
    }

    // ---- HTML 解析（列表页）-----------------------------------------------

    private int parseTotalPages(String html) {
        Matcher m = TOTAL_PAGES_PATTERN.matcher(html);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    private List<ArtworkData> parseArtworks(String html) {
        List<ArtworkData> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        List<String> imgUrls = parseImgUrls(html);

        Elements lis = doc.select(".listImg ul li");
        for (int i = 0; i < lis.size(); i++) {
            Element li = lis.get(i);
            ArtworkData data = parseLi(li);
            if (data == null) continue;
            if (i < imgUrls.size()) {
                data.imageUrl = imgUrls.get(i);
            }
            list.add(data);
        }
        return list;
    }

    private ArtworkData parseLi(Element li) {
        Element linkEl = li.selectFirst("a[href*=paimai-art]");
        if (linkEl == null) return null;

        String sourceUrl = linkEl.absUrl("href");
        if (sourceUrl.isBlank()) sourceUrl = linkEl.attr("href");

        String externalId = sourceUrl.replaceAll(".*/paimai-", "");

        Element titleEl = li.selectFirst("h3 a");
        String rawTitle = titleEl != null ? titleEl.text().trim() : "";
        String artist = null;
        String artworkTitle = rawTitle;
        String lotNumber = null;

        Matcher m = LOT_TITLE_PATTERN.matcher(rawTitle);
        if (m.matches()) {
            lotNumber = m.group(1);          // 拍品编号（详情页会覆盖）
            String rest = m.group(2).trim();
            int spIdx = rest.indexOf(' ');
            if (spIdx > 0) {
                artist = rest.substring(0, spIdx).trim();
                artworkTitle = rest.substring(spIdx + 1).trim();
            } else {
                artworkTitle = rest;
            }
        }

        if (artworkTitle.isBlank()) return null;

        // 估价/成交价（列表页初始值，详情页会覆盖）
        Element priceEl = li.selectFirst("i.fmE.fred.fb");
        String valuation = priceEl != null ? priceEl.text().trim() : null;

        // 拍卖公司
        Element orgEl = li.selectFirst("a[href*=org_detail]");
        String auctionHouse = orgEl != null ? orgEl.text().trim() : null;

        // 拍卖日期
        String auctionDate = null;
        Elements pEls = li.select("p");
        for (Element p : pEls) {
            if (p.selectFirst("a[href*=org_detail]") != null) {
                String pText = p.text().replace(auctionHouse != null ? auctionHouse : "", "").trim();
                if (!pText.isBlank()) {
                    auctionDate = pText;
                }
                break;
            }
        }

        ArtworkData data = new ArtworkData();
        data.externalId = externalId;
        data.title = artworkTitle;
        data.artist = artist;
        data.lotNumber = lotNumber;
        data.valuation = valuation;
        data.auctionDate = auctionDate;
        data.auctionHouse = auctionHouse;
        data.sourceUrl = sourceUrl;
        return data;
    }

    private List<String> parseImgUrls(String html) {
        int start = html.indexOf("var arr=get_arr('");
        if (start == -1) return List.of();

        int jsonStart = html.indexOf('[', start);
        int jsonEnd = html.indexOf(']', jsonStart);
        if (jsonStart == -1 || jsonEnd == -1) return List.of();

        String jsonPart = html.substring(jsonStart, jsonEnd + 1);

        List<String> allUrls = new ArrayList<>();
        Matcher m = IMG_URL_PATTERN.matcher(jsonPart);
        while (m.find()) {
            allUrls.add(m.group(1).replace("\\/", "/"));
        }

        if (allUrls.isEmpty()) return List.of();

        List<String> firstPerArtwork = new ArrayList<>();
        Pattern artIdPattern = Pattern.compile("/art/(\\d+)/");
        String prevArtId = null;
        for (String url : allUrls) {
            Matcher am = artIdPattern.matcher(url);
            String artId = am.find() ? am.group(1) : null;
            if (artId == null || !artId.equals(prevArtId)) {
                firstPerArtwork.add(url);
                prevArtId = artId;
            }
        }
        return firstPerArtwork;
    }

    // ---- 详情页抓取与解析 -------------------------------------------------

    private PagePerformanceMetrics enrichPageDetailsConcurrently(List<ArtworkData> artworks,
                                                                Long taskId,
                                                                int pageNumber,
                                                                AppProperties.Source cfg,
                                                                ExecutorService detailExecutor) throws InterruptedException {
        long pageStart = System.nanoTime();
        long staggerDelayMs = calculateStaggerDelayMs(cfg);
        ExecutorCompletionService<DetailFetchResult> completionService = new ExecutorCompletionService<>(detailExecutor);
        List<Future<DetailFetchResult>> futures = new ArrayList<>();

        for (int i = 0; i < artworks.size(); i++) {
            ArtworkData data = artworks.get(i);
            if (Thread.currentThread().isInterrupted()) {
                cancelFutures(futures);
                throw new InterruptedException("Task interrupted");
            }

            futures.add(completionService.submit(() -> enrichFromDetail(data, taskId, pageNumber)));

            if (staggerDelayMs > 0 && i < artworks.size() - 1) {
                Thread.sleep(staggerDelayMs);
            }
        }

        int successCount = 0;
        int failureCount = 0;
        long totalLatencyMs = 0;
        long maxLatencyMs = 0;
        List<Long> latencies = new ArrayList<>(artworks.size());

        for (int i = 0; i < artworks.size(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                cancelFutures(futures);
                throw new InterruptedException("Task interrupted");
            }

            try {
                DetailFetchResult result = completionService.take().get();
                totalLatencyMs += result.getDurationMs();
                maxLatencyMs = Math.max(maxLatencyMs, result.getDurationMs());
                latencies.add(result.getDurationMs());
                if (result.isSuccess()) {
                    successCount++;
                } else {
                    failureCount++;
                }
            } catch (ExecutionException e) {
                log.warn("Task[{}] 详情并发任务异常: page={}, message={}",
                        taskId, pageNumber, e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
                failureCount++;
            }
        }

        long pageDurationMs = nanosToMillis(System.nanoTime() - pageStart);
        return new PagePerformanceMetrics(
                artworks.size(),
                successCount,
                failureCount,
                pageDurationMs,
                totalLatencyMs,
                maxLatencyMs,
                latencies
        );
    }

    private DetailFetchResult enrichFromDetail(ArtworkData data, Long taskId, int pageNumber) {
        long start = System.nanoTime();
        if (data.sourceUrl == null || data.sourceUrl.isBlank()) {
            fetchFailureService.recordDetailFailure(taskId,
                    pageNumber,
                    data.externalId,
                    data.sourceUrl,
                    new IllegalStateException("详情页 sourceUrl 为空"));
            return DetailFetchResult.failure(nanosToMillis(System.nanoTime() - start));
        }
        try {
            Document doc = fetchDetailDocument(taskId, data.sourceUrl);
            extractorChain.extractAll(doc, data);
            return DetailFetchResult.success(nanosToMillis(System.nanoTime() - start));
        } catch (Exception e) {
            log.warn("Task[{}] 请求详情页失败: externalId={}, url={}, errorType={}, message={}",
                    taskId, data.externalId, data.sourceUrl, e.getClass().getName(), e.getMessage(), e);
            fetchFailureService.recordDetailFailure(taskId, pageNumber, data.externalId, data.sourceUrl, e);
            return DetailFetchResult.failure(nanosToMillis(System.nanoTime() - start));
        }
    }

    private Document fetchDetailDocument(Long taskId, String sourceUrl) throws Exception {
        log.debug("Task[{}] 抓取详情页：{}", taskId, sourceUrl);
        return artronRequestSupport.configure(
                        Jsoup.connect(sourceUrl),
                        "https://artso.artron.net/",
                        30_000
                )
                .get();
    }

    public boolean retryFailure(SearchTask task, FetchFailure failure) {
        AppProperties.Source cfg = appProperties.getSource();
        return switch (failure.getFailureType()) {
            case LIST_PAGE -> retryListPageFailure(task, failure, cfg);
            case DETAIL_PAGE -> retryDetailPageFailure(task, failure, cfg);
        };
    }

    private boolean retryListPageFailure(SearchTask task, FetchFailure failure, AppProperties.Source cfg) {
        TaskPerformanceTracker performanceTracker = TaskPerformanceTracker.fromTask(task, Math.max(1, cfg.getDetailFetchConcurrency()));
        String pageUrl = failure.getRequestUrl();
        if (pageUrl == null || pageUrl.isBlank()) {
            pageUrl = buildListPageUrl(task.getKeyword(), failure.getPageNumber(), cfg);
        }

        String html;
        try {
            html = fetchPage(task.getId(), pageUrl);
        } catch (Exception e) {
            log.warn("Task[{}] 重试列表页失败: page={}, url={}, message={}",
                    task.getId(), failure.getPageNumber(), pageUrl, e.getMessage(), e);
            fetchFailureService.recordListPageFailure(task.getId(), failure.getPageNumber(), pageUrl, e);
            return false;
        }

        int totalPages = task.getTotalPages();
        if (totalPages == 0) {
            totalPages = parseTotalPages(html);
        }

        List<ArtworkData> artworks = parseArtworks(html);
        if (artworks.isEmpty()) {
            IllegalStateException e = new IllegalStateException("列表页重试解析为空");
            fetchFailureService.recordListPageFailure(task.getId(), failure.getPageNumber(), pageUrl, e);
            return false;
        }

        ExecutorService detailExecutor = createDetailExecutor(task.getId(), Math.max(1, cfg.getDetailFetchConcurrency()));
        PagePerformanceMetrics pageMetrics;
        try {
            pageMetrics = enrichPageDetailsConcurrently(artworks, task.getId(), failure.getPageNumber(), cfg, detailExecutor);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            detailExecutor.shutdownNow();
        }

        int saved = saveArtworks(task, artworks);
        TaskPerformanceSnapshot snapshot = performanceTracker.recordPage(pageMetrics, saved);
        updateTaskProgressAndMetrics(task,
                Math.max(task.getCurrentPage(), failure.getPageNumber()),
                totalPages > 0 ? totalPages : task.getTotalPages(),
                task.getTotalFetched() + saved,
                snapshot);
        return true;
    }

    private boolean retryDetailPageFailure(SearchTask task, FetchFailure failure, AppProperties.Source cfg) {
        String sourceUrl = failure.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            fetchFailureService.recordDetailFailure(task.getId(),
                    failure.getPageNumber(),
                    failure.getExternalId(),
                    failure.getSourceUrl(),
                    new IllegalStateException("详情页失败记录缺少 sourceUrl"));
            return false;
        }

        ArtworkData data = new ArtworkData();
        data.externalId = failure.getExternalId();
        data.sourceUrl = sourceUrl;
        DetailFetchResult result = enrichFromDetail(data, task.getId(), failure.getPageNumber());
        if (!result.isSuccess()) {
            return false;
        }

        Artwork existingArtwork = artworkRepository.findByTaskIdAndExternalId(task.getId(), failure.getExternalId())
                .orElse(null);
        if (existingArtwork == null && (data.title == null || data.title.isBlank())) {
            fetchFailureService.recordDetailFailure(task.getId(),
                    failure.getPageNumber(),
                    failure.getExternalId(),
                    sourceUrl,
                    new IllegalStateException("详情页重试成功但缺少标题，无法新建拍品"));
            return false;
        }

        saveArtworks(task, List.of(data));
        TaskPerformanceTracker performanceTracker = TaskPerformanceTracker.fromTask(task, Math.max(1, cfg.getDetailFetchConcurrency()));
        TaskPerformanceSnapshot snapshot = performanceTracker.recordPage(
                new PagePerformanceMetrics(1, 1, 0, result.getDurationMs(), result.getDurationMs(),
                        result.getDurationMs(), List.of(result.getDurationMs())),
                0);
        updateTaskMetrics(task.getId(), snapshot);
        return true;
    }

    // ---- 数据库写入（upsert）----------------------------------------------

    protected int saveArtworks(SearchTask task, List<ArtworkData> items) {
        Map<String, ArtworkData> itemsByExternalId = new LinkedHashMap<>();
        for (ArtworkData item : items) {
            String externalId = sanitizeText("externalId", item.externalId, task.getId(), item.externalId);
            if (externalId == null) {
                log.warn("Task[{}] 跳过拍品保存: reason=externalId为空或非法, sourceUrl={}",
                        task.getId(),
                        sanitizeText("sourceUrl", item.sourceUrl, task.getId(), null));
                continue;
            }
            item.externalId = externalId;
            itemsByExternalId.put(externalId, item);
        }
        if (itemsByExternalId.isEmpty()) {
            return 0;
        }

        Map<String, Artwork> existingByExternalId = artworkRepository
                .findAllByTaskIdAndExternalIdIn(task.getId(), itemsByExternalId.keySet())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        Artwork::getExternalId,
                        artwork -> artwork,
                        this::preferLatestArtwork
                ));

        int saved = 0;
        for (ArtworkData item : itemsByExternalId.values()) {
            Artwork artwork = existingByExternalId.get(item.externalId);
            boolean isNew = (artwork == null);
            if (isNew) {
                artwork = new Artwork();
                artwork.setTask(task);
                artwork.setExternalId(item.externalId);
            }

            artwork.setTitle(mergeText("title", item.title, artwork.getTitle(), task.getId(), item.externalId));
            artwork.setLotNumber(mergeText("lotNumber", item.lotNumber, artwork.getLotNumber(), task.getId(), item.externalId));
            artwork.setArtist(mergeText("artist", item.artist, artwork.getArtist(), task.getId(), item.externalId));
            artwork.setMedium(mergeText("medium", item.medium, artwork.getMedium(), task.getId(), item.externalId));
            artwork.setFormat(mergeText("format", item.format, artwork.getFormat(), task.getId(), item.externalId));
            artwork.setDimensions(mergeText("dimensions", item.dimensions, artwork.getDimensions(), task.getId(), item.externalId));
            artwork.setDescription(mergeText("description", item.description, artwork.getDescription(), task.getId(), item.externalId));
            artwork.setValuation(mergeText("valuation", item.valuation, artwork.getValuation(), task.getId(), item.externalId));
            String mergedTransactionPrice = mergeText("transactionPrice", item.transactionPrice, artwork.getTransactionPrice(), task.getId(), item.externalId);
            artwork.setTransactionPrice(mergedTransactionPrice);
            artwork.setTransactionPriceNote(resolveTransactionPriceNote(item, artwork.getTransactionPriceNote(), mergedTransactionPrice));
            artwork.setAuctionDate(mergeText("auctionDate", item.auctionDate, artwork.getAuctionDate(), task.getId(), item.externalId));
            artwork.setAuctionHouse(mergeText("auctionHouse", item.auctionHouse, artwork.getAuctionHouse(), task.getId(), item.externalId));
            artwork.setAuctionName(mergeText("auctionName", item.auctionName, artwork.getAuctionName(), task.getId(), item.externalId));
            artwork.setAuctionSession(mergeText("auctionSession", item.auctionSession, artwork.getAuctionSession(), task.getId(), item.externalId));
            artwork.setAuctionLocation(mergeText("auctionLocation", item.auctionLocation, artwork.getAuctionLocation(), task.getId(), item.externalId));
            artwork.setPreviewTime(mergeText("previewTime", item.previewTime, artwork.getPreviewTime(), task.getId(), item.externalId));
            artwork.setPreviewLocation(mergeText("previewLocation", item.previewLocation, artwork.getPreviewLocation(), task.getId(), item.externalId));
            artwork.setImageUrl(mergeText("imageUrl", item.imageUrl, artwork.getImageUrl(), task.getId(), item.externalId));
            artwork.setOriginalImageSourceUrl(mergeText("originalImageSourceUrl", item.originalImageUrl, artwork.getOriginalImageSourceUrl(), task.getId(), item.externalId));
            artwork.setSourceUrl(mergeText("sourceUrl", item.sourceUrl, artwork.getSourceUrl(), task.getId(), item.externalId));
            artwork.setExtraData(mergeText("extraData", item.extraData, artwork.getExtraData(), task.getId(), item.externalId));

            if (artwork.getTitle() == null || artwork.getTitle().isBlank()) {
                log.warn("Task[{}] 跳过拍品保存: externalId={}, reason=标题为空(清洗后), sourceUrl={}",
                        task.getId(), item.externalId, artwork.getSourceUrl());
                continue;
            }

            try {
                artworkRepository.saveAndFlush(artwork);
                if (isNew) {
                    saved++;
                }
            } catch (Exception e) {
                log.warn("Task[{}] 拍品保存失败，已跳过并继续: externalId={}, sourceUrl={}, errorType={}, message={}",
                        task.getId(),
                        item.externalId,
                        artwork.getSourceUrl(),
                        e.getClass().getName(),
                        e.getMessage(),
                        e);
            }
        }
        return saved;
    }

    private String resolveTransactionPriceNote(ArtworkData item, String existingNote, String transactionPrice) {
        if (TransactionPriceNoteHelper.hasPrice(transactionPrice)) {
            return null;
        }
        return mergeText("transactionPriceNote",
                TransactionPriceNoteHelper.noteForExtraction(item),
                existingNote,
                null,
                item.externalId);
    }

    private Artwork preferLatestArtwork(Artwork left, Artwork right) {
        if (left.getId() == null) {
            return right;
        }
        if (right.getId() == null) {
            return left;
        }
        return left.getId() >= right.getId() ? left : right;
    }

    @Transactional
    protected void updateTaskProgress(SearchTask task, int currentPage, int totalPages, int totalFetched) {
        taskRepository.findById(task.getId()).ifPresent(t -> {
            t.setCurrentPage(currentPage);
            t.setTotalPages(totalPages);
            t.setTotalFetched(totalFetched);
            task.setCurrentPage(currentPage);
            task.setTotalPages(totalPages);
            task.setTotalFetched(totalFetched);
            taskRepository.save(t);
        });
    }

    @Transactional
    protected void updateTaskProgressAndMetrics(SearchTask task,
                                                int currentPage,
                                                int totalPages,
                                                int totalFetched,
                                                TaskPerformanceSnapshot snapshot) {
        taskRepository.findById(task.getId()).ifPresent(t -> {
            t.setCurrentPage(currentPage);
            t.setTotalPages(totalPages);
            t.setTotalFetched(totalFetched);
            applyMetrics(t, snapshot);
            task.setCurrentPage(currentPage);
            task.setTotalPages(totalPages);
            task.setTotalFetched(totalFetched);
            applyMetrics(task, snapshot);
            taskRepository.save(t);
        });
    }

    @Transactional
    protected void updateTaskMetrics(Long taskId, TaskPerformanceSnapshot snapshot) {
        taskRepository.findById(taskId).ifPresent(task -> {
            applyMetrics(task, snapshot);
            taskRepository.save(task);
        });
    }

    // ---- 工具 ------------------------------------------------------------

    private ExecutorService createDetailExecutor(Long taskId, int detailConcurrency) {
        AtomicInteger threadSeq = new AtomicInteger(1);
        return Executors.newFixedThreadPool(detailConcurrency, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("artfetch-detail-" + taskId + "-" + threadSeq.getAndIncrement());
            return thread;
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

    private void cancelFutures(List<Future<DetailFetchResult>> futures) {
        for (Future<DetailFetchResult> future : futures) {
            future.cancel(true);
        }
    }

    private long calculateStaggerDelayMs(AppProperties.Source cfg) {
        long detailConcurrency = Math.max(1, cfg.getDetailFetchConcurrency());
        return Math.max(0, cfg.getRequestDelayMs() / detailConcurrency);
    }

    private String encode(String keyword) {
        try {
            return java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return keyword;
        }
    }

    private String mergeText(String fieldName,
                             String latest,
                             String existing,
                             Long taskId,
                             String externalId) {
        String sanitizedLatest = sanitizeText(fieldName, latest, taskId, externalId);
        if (sanitizedLatest != null) {
            return sanitizedLatest;
        }
        return sanitizeText(fieldName, existing, taskId, externalId);
    }

    private String sanitizeText(String fieldName, String value, Long taskId, String externalId) {
        TextSanitizer.SanitizedText sanitized = TextSanitizer.sanitize(value);
        if (sanitized.removedIllegalChars() > 0) {
            log.warn("Task[{}] 清洗拍品字段特殊字符: externalId={}, field={}, removedIllegalChars={}",
                    taskId,
                    externalId != null ? externalId : "-",
                    fieldName,
                    sanitized.removedIllegalChars());
        }
        return sanitized.value();
    }

    private long nanosToMillis(long nanos) {
        return Math.max(1L, nanos / 1_000_000L);
    }

    private String formatPercent(double ratio) {
        return String.format("%.1f", ratio * 100);
    }

    private String formatRate(double value) {
        return String.format("%.1f", value);
    }
}
