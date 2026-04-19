package com.artfetch.service;

import com.artfetch.config.AppProperties;
import com.artfetch.dto.ArtworkDto;
import com.artfetch.entity.Artwork;
import com.artfetch.entity.SearchTask;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.repository.SearchTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class HdImageService {

    private static final String VIEWER_URL_TEMPLATE = "https://tulu.artron.net/wap/NewHdImage/bigpic/%s";
    private static final String IMAGE_SERVER = "https://hdimages.artron.net";
    private static final Pattern JSONP_PATTERN = Pattern.compile("^[^(]+\\((.*)\\)\\s*;?\\s*$", Pattern.DOTALL);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ArtworkRepository artworkRepository;
    private final SearchTaskRepository taskRepository;
    private final AppProperties appProperties;
    private final ArtronRequestSupport artronRequestSupport;
    private ExecutorService tileExecutor;

    @PostConstruct
    void initTileExecutor() {
        int fetchConcurrency = Math.max(1, appProperties.getImage().getFetchConcurrency());
        AtomicInteger threadSeq = new AtomicInteger(1);
        tileExecutor = Executors.newFixedThreadPool(fetchConcurrency, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("artfetch-hd-tile-" + threadSeq.getAndIncrement());
            return thread;
        });
    }

    @PreDestroy
    void shutdownTileExecutor() {
        if (tileExecutor != null) {
            tileExecutor.shutdownNow();
        }
    }

    public HdImageTaskResult runTask(SearchTask task) throws InterruptedException {
        if (task.getTargetTaskId() == null) {
            throw new IllegalStateException("补充超清无损图任务缺少目标检索任务");
        }
        if (!artronRequestSupport.hasAuthCookie()) {
            throw new IllegalStateException("下载超清无损图需要配置雅昌登录 Cookie 或账号密码");
        }

        List<Long> pendingArtworkIds = artworkRepository.findPendingHdImageIdsByTaskIdOrderByIdAsc(
                task.getTargetTaskId(),
                Artwork.HdImageStatus.DOWNLOADED
        );
        int totalCount = pendingArtworkIds.size();
        int artworkConcurrency = resolveArtworkConcurrency(totalCount);
        int batchSize = Math.max(artworkConcurrency, appProperties.getImage().getBatchSize());
        TaskPerformanceTracker performanceTracker = TaskPerformanceTracker.fromTask(new SearchTask(), artworkConcurrency);
        updateTaskProgressAndMetrics(task.getId(), 0, totalCount, 0, performanceTracker.snapshot());

        if (pendingArtworkIds.isEmpty()) {
            updateTaskError(task.getId(), null);
            return new HdImageTaskResult(0, 0, 0, 0, 0);
        }

        ExecutorService artworkExecutor = createArtworkExecutor(task.getId(), artworkConcurrency);
        int processed = 0;
        int downloaded = 0;
        int skipped = 0;
        int failed = 0;

        try {
            for (int start = 0; start < pendingArtworkIds.size(); start += batchSize) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Task interrupted");
                }

                int batchIndex = start / batchSize + 1;
                int totalBatches = (int) Math.ceil((double) pendingArtworkIds.size() / batchSize);
                List<Long> batchIds = pendingArtworkIds.subList(start, Math.min(start + batchSize, pendingArtworkIds.size()));
                List<Artwork> batchArtworks = artworkRepository.findByIdInOrderByIdAsc(batchIds);
                BatchRunResult batchResult = downloadBatchConcurrently(
                        batchArtworks,
                        task.getId(),
                        batchIndex,
                        totalBatches,
                        artworkConcurrency,
                        artworkExecutor
                );

                processed += batchArtworks.size();
                downloaded += batchResult.downloadedCount();
                skipped += batchResult.skippedCount();
                failed += batchResult.failedCount();

                TaskPerformanceSnapshot snapshot = performanceTracker.recordPage(
                        batchResult.pageMetrics(),
                        batchArtworks.size()
                );
                updateTaskProgressAndMetrics(task.getId(), processed, totalCount, downloaded, snapshot);

                log.info("超清无损图补充进度: taskId={}, batch={}/{}, processed={}, total={}, downloaded={}, skipped={}, failed={}, artworkConcurrency={}, tileConcurrency={}, batchDuration={}ms",
                        task.getId(),
                        batchIndex,
                        totalBatches,
                        processed,
                        totalCount,
                        downloaded,
                        skipped,
                        failed,
                        artworkConcurrency,
                        Math.max(1, appProperties.getImage().getFetchConcurrency()),
                        batchResult.pageMetrics().getPageDurationMs());
            }
        } finally {
            artworkExecutor.shutdownNow();
        }

        updateTaskError(task.getId(), failed > 0
                ? "超清无损图补充已完成，但有 " + failed + " 条下载失败"
                : null);
        return new HdImageTaskResult(totalCount, processed, downloaded, skipped, failed);
    }

    public ArtworkDto redownloadHdImage(Long artworkId) {
        ensureHdImageStored(artworkId, true);
        return artworkRepository.findById(artworkId)
                .map(ArtworkDto::from)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));
    }

    public Resource loadHdImage(Long artworkId) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));
        if (artwork.getHdImagePath() == null || artwork.getHdImagePath().isBlank()) {
            throw new IllegalStateException("超清无损图尚未下载，请先创建并运行补充任务");
        }

        Path path = storageRoot().resolve(artwork.getHdImagePath()).normalize();
        if (!path.startsWith(storageRoot()) || !Files.exists(path)) {
            throw new IllegalStateException("超清无损图文件不存在，请重新执行补充任务");
        }
        return new FileSystemResource(path);
    }

    public MediaType resolveMediaType(Long artworkId) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));
        if (artwork.getHdImageContentType() == null || artwork.getHdImageContentType().isBlank()) {
            return MediaType.IMAGE_PNG;
        }
        try {
            return MediaType.parseMediaType(artwork.getHdImageContentType());
        } catch (Exception e) {
            return MediaType.IMAGE_PNG;
        }
    }

    public String hdFilename(Long artworkId) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));
        String base = artwork.getExternalId() != null && !artwork.getExternalId().isBlank()
                ? artwork.getExternalId()
                : "artwork-" + artworkId;
        return base + "-hd.png";
    }

    @Transactional
    protected DownloadOutcome ensureHdImageStored(Long artworkId, boolean force) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));

        Path root = storageRoot();
        Path existingPath = resolveStoredPath(root, artwork.getHdImagePath());
        if (!force
                && artwork.getHdImageStatus() == Artwork.HdImageStatus.DOWNLOADED
                && existingPath != null
                && Files.exists(existingPath)) {
            return DownloadOutcome.SKIPPED;
        }

        String artCode = resolveArtCode(artwork);
        if (artCode == null || artCode.isBlank()) {
            artwork.setHdImageStatus(Artwork.HdImageStatus.FAILED);
            artwork.setHdImageLastError("无法解析超清无损图拍品编号");
            artworkRepository.save(artwork);
            return DownloadOutcome.FAILED;
        }

        String viewerUrl = VIEWER_URL_TEMPLATE.formatted(artCode);
        artwork.setHdImageSourceUrl(viewerUrl);

        try {
            Files.createDirectories(root);

            HdImageOption option = fetchHdImageOption(artCode, viewerUrl);
            BufferedImage stitchedImage = stitchTiles(artCode, viewerUrl, option);

            String relativePath = buildRelativePath(artwork);
            Path targetPath = root.resolve(relativePath).normalize();
            Files.createDirectories(targetPath.getParent());

            Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
            ImageIO.write(stitchedImage, "png", tempPath.toFile());
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveError) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            artwork.setHdImagePath(relativePath);
            artwork.setHdImageContentType(MediaType.IMAGE_PNG_VALUE);
            artwork.setHdImageSize(Files.size(targetPath));
            artwork.setHdImageDownloadedAt(LocalDateTime.now());
            artwork.setHdImageStatus(Artwork.HdImageStatus.DOWNLOADED);
            artwork.setHdImageLastError(null);
            artworkRepository.save(artwork);

            boolean existedBefore = existingPath != null && Files.exists(existingPath);
            return (force || !existedBefore) ? DownloadOutcome.DOWNLOADED : DownloadOutcome.SKIPPED;
        } catch (Exception e) {
            log.warn("超清无损图下载失败: artworkId={}, artCode={}, message={}",
                    artworkId, artCode, e.getMessage(), e);
            artwork.setHdImageStatus(Artwork.HdImageStatus.FAILED);
            artwork.setHdImageLastError(e.getMessage());
            artworkRepository.save(artwork);
            return DownloadOutcome.FAILED;
        }
    }

    private HdImageOption fetchHdImageOption(String artCode, String viewerUrl) throws IOException {
        String endpoint = IMAGE_SERVER + "/auction/getImageOption?callback=hdImageCallback&artCode=" + artCode;
        Connection.Response response = artronRequestSupport.configure(
                        Jsoup.connect(endpoint),
                        viewerUrl,
                        appProperties.getImage().getDownloadTimeoutMs()
                )
                .ignoreContentType(true)
                .execute();
        JsonNode root = parseJsonp(response.body());
        if (root.path("code").asInt(-1) != 0) {
            String message = root.path("msg").asText("未获取到超清无损图信息");
            throw new IllegalStateException(message);
        }

        JsonNode data = root.path("data");
        int type = data.path("type").asInt(-1);
        if (type != 1) {
            throw new IllegalStateException(switch (type) {
                case 0 -> "超清无损图仍在处理，请稍后再试";
                case 2, 10 -> "超清无损图暂不可用";
                default -> "超清无损图未准备好";
            });
        }

        String token = data.path("aesStr").asText("");
        int width = data.path("w").asInt(0);
        int height = data.path("h").asInt(0);
        if (token.isBlank() || width <= 0 || height <= 0) {
            throw new IllegalStateException("超清无损图元数据不完整");
        }
        String sessionCookieHeader = buildSessionCookieHeader(
                artronRequestSupport.resolveCookieHeader(),
                response.cookies(),
                token
        );
        return new HdImageOption(token, width, height, sessionCookieHeader);
    }

    private BufferedImage stitchTiles(String artCode, String viewerUrl, HdImageOption option) throws IOException {
        int width = option.width();
        int height = option.height();
        int tileSize = 256;
        int overlap = 1;
        int maxLevel = maxLevel(width, height);
        int columns = (int) Math.ceil(width / (double) tileSize);
        int rows = (int) Math.ceil(height / (double) tileSize);
        int totalTiles = rows * columns;
        int fetchConcurrency = Math.max(1, appProperties.getImage().getFetchConcurrency());

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        List<Future<TileFetchResult>> futures = new ArrayList<>(totalTiles);
        try {
            if (fetchConcurrency <= 1 || totalTiles <= 1) {
                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < columns; col++) {
                        BufferedImage tile = fetchTile(artCode, viewerUrl, option.sessionCookieHeader(), maxLevel, col, row);
                        drawTile(graphics, tile, col, row, width, height, tileSize, overlap);
                    }
                }
                return image;
            }

            ExecutorCompletionService<TileFetchResult> completionService = new ExecutorCompletionService<>(tileExecutor);
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < columns; col++) {
                    final int tileRow = row;
                    final int tileCol = col;
                    futures.add(completionService.submit(() -> new TileFetchResult(
                            tileCol,
                            tileRow,
                            fetchTile(artCode, viewerUrl, option.sessionCookieHeader(), maxLevel, tileCol, tileRow)
                    )));
                }
            }

            for (int i = 0; i < totalTiles; i++) {
                TileFetchResult tile = completionService.take().get();
                drawTile(graphics, tile.image(), tile.col(), tile.row(), width, height, tileSize, overlap);
            }
        } catch (InterruptedException e) {
            cancelFutures(futures);
            Thread.currentThread().interrupt();
            throw new IOException("超清无损图下载已中断", e);
        } catch (ExecutionException e) {
            cancelFutures(futures);
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("超清无损图瓦片下载失败: " + (cause == null ? "" : cause.getMessage()), cause);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage fetchTile(String artCode,
                                    String viewerUrl,
                                    String sessionCookieHeader,
                                    int level,
                                    int col,
                                    int row) throws IOException {
        String tileUrl = IMAGE_SERVER + "/auction/images/" + artCode + "/" + level + "/" + col + "_" + row + ".jpg";
        Connection connection = Jsoup.connect(tileUrl)
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", viewerUrl)
                .timeout(appProperties.getImage().getDownloadTimeoutMs());

        if (sessionCookieHeader != null && !sessionCookieHeader.isBlank()) {
            connection.header("Cookie", sessionCookieHeader);
        }

        Connection.Response response = connection.execute();
        String contentType = response.contentType();
        if (contentType != null && (contentType.contains("json") || contentType.contains("text"))) {
            String body = response.body();
            throw new IllegalStateException("超清无损图瓦片请求被拒绝: " + (body == null ? "" : body));
        }

        byte[] bytes = response.bodyAsBytes();
        BufferedImage tile = ImageIO.read(new ByteArrayInputStream(bytes));
        if (tile == null) {
            throw new IllegalStateException("超清无损图瓦片无法解析");
        }
        return tile;
    }

    private JsonNode parseJsonp(String body) throws IOException {
        Matcher matcher = JSONP_PATTERN.matcher(body == null ? "" : body.trim());
        if (!matcher.matches()) {
            throw new IllegalStateException("超清无损图接口返回格式异常");
        }
        return OBJECT_MAPPER.readTree(matcher.group(1));
    }

    private int maxLevel(int width, int height) {
        int maxDimension = Math.max(width, height);
        int level = 0;
        while ((1 << level) < maxDimension && level < 30) {
            level++;
        }
        return level;
    }

    private String resolveArtCode(Artwork artwork) {
        if (artwork.getExternalId() != null && !artwork.getExternalId().isBlank()) {
            return artwork.getExternalId().trim();
        }
        if (artwork.getSourceUrl() == null || artwork.getSourceUrl().isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("/paimai-([^/?#]+)", Pattern.CASE_INSENSITIVE)
                .matcher(artwork.getSourceUrl());
        return matcher.find() ? matcher.group(1) : null;
    }

    private String buildRelativePath(Artwork artwork) {
        String externalId = artwork.getExternalId() != null && !artwork.getExternalId().isBlank()
                ? artwork.getExternalId().replaceAll("[^A-Za-z0-9_-]", "_")
                : "artwork_" + artwork.getId();
        return "task-" + artwork.getTask().getId() + "/" + externalId + "/hd-lossless.png";
    }

    @Transactional
    protected void updateTaskProgressAndMetrics(Long taskId,
                                                int processedCount,
                                                int totalCount,
                                                int downloadedCount,
                                                TaskPerformanceSnapshot snapshot) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setCurrentPage(processedCount);
            task.setTotalPages(totalCount);
            task.setTotalFetched(downloadedCount);
            applyMetrics(task, snapshot);
            taskRepository.save(task);
        });
    }

    @Transactional
    protected void updateTaskError(Long taskId, String message) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setErrorMessage(message);
            taskRepository.save(task);
        });
    }

    private Path storageRoot() {
        return Path.of(appProperties.getImage().getStoragePath()).toAbsolutePath().normalize();
    }

    private Path resolveStoredPath(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return root.resolve(relativePath).normalize();
    }

    private void drawTile(Graphics2D graphics,
                          BufferedImage tile,
                          int col,
                          int row,
                          int width,
                          int height,
                          int tileSize,
                          int overlap) {
        int destX = col * tileSize;
        int destY = row * tileSize;
        int destWidth = Math.min(tileSize, width - destX);
        int destHeight = Math.min(tileSize, height - destY);
        int srcX = col == 0 ? 0 : overlap;
        int srcY = row == 0 ? 0 : overlap;

        graphics.drawImage(
                tile,
                destX,
                destY,
                destX + destWidth,
                destY + destHeight,
                srcX,
                srcY,
                srcX + destWidth,
                srcY + destHeight,
                null
        );
    }

    private void cancelFutures(List<Future<TileFetchResult>> futures) {
        for (Future<TileFetchResult> future : futures) {
            future.cancel(true);
        }
    }

    private BatchRunResult downloadBatchConcurrently(List<Artwork> batchArtworks,
                                                     Long taskId,
                                                     int batchIndex,
                                                     int totalBatches,
                                                     int artworkConcurrency,
                                                     ExecutorService artworkExecutor) throws InterruptedException {
        long batchStart = System.nanoTime();
        long staggerDelayMs = calculateArtworkStaggerDelayMs(artworkConcurrency);
        ExecutorCompletionService<ArtworkDownloadResult> completionService =
                new ExecutorCompletionService<>(artworkExecutor);
        List<Future<ArtworkDownloadResult>> futures = new ArrayList<>();

        for (int i = 0; i < batchArtworks.size(); i++) {
            Artwork artwork = batchArtworks.get(i);
            if (Thread.currentThread().isInterrupted()) {
                cancelArtworkFutures(futures);
                throw new InterruptedException("Task interrupted");
            }

            futures.add(completionService.submit(() -> ensureHdImageStoredWithMetrics(artwork.getId())));
            if (staggerDelayMs > 0 && i < batchArtworks.size() - 1) {
                Thread.sleep(staggerDelayMs);
            }
        }

        int downloadedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        int successCount = 0;
        long totalLatencyMs = 0;
        long maxLatencyMs = 0;
        List<Long> latencies = new ArrayList<>(batchArtworks.size());

        for (int i = 0; i < batchArtworks.size(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                cancelArtworkFutures(futures);
                throw new InterruptedException("Task interrupted");
            }

            try {
                ArtworkDownloadResult result = completionService.take().get();
                totalLatencyMs += result.durationMs();
                maxLatencyMs = Math.max(maxLatencyMs, result.durationMs());
                latencies.add(result.durationMs());

                switch (result.outcome()) {
                    case DOWNLOADED -> {
                        downloadedCount++;
                        successCount++;
                    }
                    case SKIPPED -> {
                        skippedCount++;
                        successCount++;
                    }
                    case FAILED -> failedCount++;
                }
            } catch (ExecutionException e) {
                log.warn("超清无损图批次异常: taskId={}, batch={}/{}, message={}",
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
        return new BatchRunResult(downloadedCount, skippedCount, failedCount, pageMetrics);
    }

    private ArtworkDownloadResult ensureHdImageStoredWithMetrics(Long artworkId) {
        long start = System.nanoTime();
        DownloadOutcome outcome = ensureHdImageStored(artworkId, false);
        return new ArtworkDownloadResult(outcome, nanosToMillis(System.nanoTime() - start));
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

    private ExecutorService createArtworkExecutor(Long taskId, int artworkConcurrency) {
        AtomicInteger threadSeq = new AtomicInteger(1);
        return Executors.newFixedThreadPool(artworkConcurrency, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("artfetch-hd-artwork-" + taskId + "-" + threadSeq.getAndIncrement());
            return thread;
        });
    }

    private int resolveArtworkConcurrency(int totalCount) {
        int configured = Math.max(1, appProperties.getImage().getArtworkConcurrency());
        return Math.max(1, Math.min(configured, Math.max(1, totalCount)));
    }

    private long calculateArtworkStaggerDelayMs(int artworkConcurrency) {
        return Math.max(0L, appProperties.getSource().getRequestDelayMs() / Math.max(1, artworkConcurrency));
    }

    private long nanosToMillis(long nanos) {
        return Math.max(1L, nanos / 1_000_000L);
    }

    private void cancelArtworkFutures(List<Future<ArtworkDownloadResult>> futures) {
        for (Future<ArtworkDownloadResult> future : futures) {
            future.cancel(true);
        }
    }

    private String buildSessionCookieHeader(String baseCookieHeader,
                                            Map<String, String> responseCookies,
                                            String token) {
        Map<String, String> cookies = new LinkedHashMap<>();
        mergeCookieHeader(cookies, baseCookieHeader);
        if (responseCookies != null) {
            responseCookies.forEach((name, value) -> {
                if (name != null && !name.isBlank() && value != null) {
                    cookies.put(name.trim(), value);
                }
            });
        }
        if (token != null && !token.isBlank()) {
            cookies.put("token", java.net.URLEncoder.encode(token, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "; " + right)
                .orElse(null);
    }

    private void mergeCookieHeader(Map<String, String> target, String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return;
        }
        for (String part : cookieHeader.split(";")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int equalsIndex = entry.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }
            String name = entry.substring(0, equalsIndex).trim();
            String value = entry.substring(equalsIndex + 1).trim();
            if (!name.isEmpty()) {
                target.put(name, value);
            }
        }
    }

    private enum DownloadOutcome {
        DOWNLOADED,
        SKIPPED,
        FAILED
    }

    private record TileFetchResult(int col, int row, BufferedImage image) {
    }

    private record ArtworkDownloadResult(DownloadOutcome outcome, long durationMs) {
    }

    private record BatchRunResult(int downloadedCount,
                                  int skippedCount,
                                  int failedCount,
                                  PagePerformanceMetrics pageMetrics) {
    }

    private record HdImageOption(String token, int width, int height, String sessionCookieHeader) {
    }
}
