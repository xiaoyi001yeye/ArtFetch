package com.artfetch.service;

import com.artfetch.auth.service.AuditLogService;
import com.artfetch.config.AppProperties;
import com.artfetch.dto.ArtworkDto;
import com.artfetch.entity.Artwork;
import com.artfetch.entity.SearchTask;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.repository.SearchTaskRepository;
import com.volcengine.tos.TosClientException;
import com.volcengine.tos.TosServerException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

    private static final String CANONICAL_SOURCE_PROVIDER = "artron";
    private static final String VIEWER_URL_TEMPLATE = "https://tulu.artron.net/wap/NewHdImage/bigpic/%s";
    private static final String IMAGE_SERVER = "https://hdimages.artron.net";
    private static final Pattern JSONP_PATTERN = Pattern.compile("^[^(]+\\((.*)\\)\\s*;?\\s*$", Pattern.DOTALL);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ArtworkRepository artworkRepository;
    private final SearchTaskRepository taskRepository;
    private final AppProperties appProperties;
    private final ArtronRequestSupport artronRequestSupport;
    private final HdImageObjectStorageService objectStorageService;
    private final AuditLogService auditLogService;
    private ExecutorService tileExecutor;

    @PostConstruct
    void initTileExecutor() {
        // Hundreds of tile decodes per artwork are much faster without ImageIO's temp-file cache.
        ImageIO.setUseCache(false);
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
                Artwork.HdImageStatus.DOWNLOADED,
                resolveHdWriteMode() == AppProperties.HdWriteMode.LEGACY_LOCAL
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

                BatchTiming batchTiming = batchResult.batchTiming();
                log.info("超清无损图补充进度: taskId={}, batch={}/{}, processed={}, total={}, downloaded={}, skipped={}, failed={}, artworkConcurrency={}, tileConcurrency={}, batchDuration={}ms, avgArtwork={}ms, slowestArtworkId={}, slowestArtwork={}ms, dominantStage={}, avgMetadata={}ms, avgStitch={}ms, avgPngWrite={}ms, avgDbSave={}ms, tiles={}/{}, tileBytes={}MiB, output={}MiB, avgTileRequest={}ms, maxTileRequest={}ms, avgTileDecode={}ms, maxTileDecode={}ms, avgTileDraw={}ms, maxTileDraw={}ms",
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
                        batchResult.pageMetrics().getPageDurationMs(),
                        batchTiming.avgArtworkMs(),
                        batchTiming.slowestArtworkId(),
                        batchTiming.maxArtworkMs(),
                        batchTiming.dominantStage(),
                        batchTiming.avgMetadataMs(),
                        batchTiming.avgStitchMs(),
                        batchTiming.avgFileWriteMs(),
                        batchTiming.avgDbSaveMs(),
                        batchTiming.completedTiles(),
                        batchTiming.totalTiles(),
                        batchTiming.totalTileMiB(),
                        batchTiming.totalOutputMiB(),
                        batchTiming.avgTileRequestMs(),
                        batchTiming.maxTileRequestMs(),
                        batchTiming.avgTileDecodeMs(),
                        batchTiming.maxTileDecodeMs(),
                        batchTiming.avgTileDrawMs(),
                        batchTiming.maxTileDrawMs());
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
        AppProperties.HdDisplayMode displayMode = resolveHdDisplayMode();
        if (displayMode != AppProperties.HdDisplayMode.LEGACY) {
            try {
                return loadCanonicalHdImage(artworkId);
            } catch (Exception e) {
                if (displayMode == AppProperties.HdDisplayMode.TOS_CANONICAL) {
                    throw e;
                }
                log.warn("V2 canonical 高清图读取失败，按 DUAL_READ 回退旧逻辑: artworkId={}, message={}",
                        artworkId, e.getMessage(), e);
            }
        }

        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));
        Artwork.HdImageStorageType storageType = artwork.getHdImageStorageType() == null
                ? Artwork.HdImageStorageType.LOCAL
                : artwork.getHdImageStorageType();
        Path root = storageRoot();
        if ((storageType == Artwork.HdImageStorageType.OBJECT || storageType == Artwork.HdImageStorageType.LOCAL_OBJECT)
                && artwork.getHdImageObjectKey() != null
                && !artwork.getHdImageObjectKey().isBlank()) {
            try {
                Long configId = artwork.getHdImageObjectConfigId();
                if (configId == null) {
                    throw new IllegalStateException("高清图缺少对象存储配置 ID");
                }
                var config = objectStorageService.loadConfig(configId);
                var object = objectStorageService.loadObject(config, artwork.getHdImageObjectKey());
                return new InputStreamResource(object.inputStream());
            } catch (Exception e) {
                logHdImageAccessFailure("对象存储读取失败", artwork, storageType, root, null, e);
                if (storageType == Artwork.HdImageStorageType.OBJECT) {
                    throw new IllegalStateException("火山 TOS 高清图读取失败，请检查对象存储配置、Bucket、Object Key 与服务端日志 artworkId=" + artworkId + ": " + e.getMessage(), e);
                }
                log.warn("火山 TOS 高清图读取失败，尝试回退本地文件: artworkId={}, externalId={}, objectConfigId={}, bucket={}, objectKey={}, message={}",
                        artworkId,
                        artwork.getExternalId(),
                        artwork.getHdImageObjectConfigId(),
                        artwork.getHdImageObjectBucket(),
                        artwork.getHdImageObjectKey(),
                        e.getMessage());
            }
        }

        if (artwork.getHdImagePath() == null || artwork.getHdImagePath().isBlank()) {
            logHdImageAccessFailure("缺少本地文件路径", artwork, storageType, root, null, null);
            throw new IllegalStateException("超清无损图尚未下载或未记录本地路径，请先创建并运行补充任务；服务端日志可搜索 artworkId=" + artworkId);
        }

        Path path = root.resolve(artwork.getHdImagePath()).normalize();
        if (!path.startsWith(root)) {
            logHdImageAccessFailure("本地文件路径越界", artwork, storageType, root, path, null);
            throw new IllegalStateException("超清无损图本地路径异常，请检查数据库中的 hd_image_path；服务端日志可搜索 artworkId=" + artworkId);
        }
        if (!Files.exists(path)) {
            logHdImageAccessFailure("本地文件不存在", artwork, storageType, root, path, null);
            throw new IllegalStateException("超清无损图文件不存在，请检查生产容器存储挂载、artfetch.image.storage-path 和数据库 hd_image_path，或重新执行补充任务；服务端日志可搜索 artworkId=" + artworkId);
        }
        return new FileSystemResource(path);
    }

    public Resource loadCanonicalHdImage(Long artworkId) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));
        String artCode = resolveArtCode(artwork);
        if (artCode == null || artCode.isBlank()) {
            log.warn("V2 高清大图访问失败: reason=无法解析 artCode, artworkId={}, externalId={}, sourceUrl={}",
                    artworkId, artwork.getExternalId(), artwork.getSourceUrl());
            recordHdViewFailure(artwork, "MISSING_ART_CODE", "无法从 externalId 或 sourceUrl 解析高清大图 artCode", null);
            throw new IllegalStateException("无法从 externalId 或 sourceUrl 解析高清大图 artCode；服务端日志可搜索 artworkId=" + artworkId);
        }

        String canonicalKey = objectStorageService.buildCanonicalObjectKey(CANONICAL_SOURCE_PROVIDER, artCode);
        try {
            var config = objectStorageService.activeConfigForRead();
            var object = objectStorageService.loadObject(config, canonicalKey);
            auditLogService.recordSuccess(
                    "artwork.image.hd.view",
                    "ARTWORK",
                    String.valueOf(artworkId),
                    "查看高清大图成功，imageVersion=hd-v2，title=" + nullToEmpty(artwork.getTitle())
            );
            return new InputStreamResource(object.inputStream());
        } catch (Exception e) {
            String reasonCode = resolveCanonicalHdFailureReason(e);
            log.warn("V2 高清大图 TOS 读取失败: artworkId={}, externalId={}, sourceUrl={}, artCode={}, canonicalKey={}, message={}",
                    artworkId,
                    artwork.getExternalId(),
                    artwork.getSourceUrl(),
                    artCode,
                    canonicalKey,
                    e.getMessage(),
                    e);
            recordHdViewFailure(artwork, reasonCode, "V2 高清大图不存在或读取失败", e);
            throw new IllegalStateException("V2 高清大图不存在或读取失败，请确认 TOS canonical 对象已升级完成；canonicalKey="
                    + canonicalKey + "；" + objectStorageService.describeTosError(e), e);
        }
    }

    private void recordHdViewFailure(Artwork artwork, String reasonCode, String description, Exception e) {
        auditLogService.recordFailure(
                "artwork.image.hd.view",
                "ARTWORK",
                String.valueOf(artwork.getId()),
                "查看高清大图失败，imageVersion=hd-v2，reasonCode=" + reasonCode
                        + "，title=" + nullToEmpty(artwork.getTitle())
                        + "，description=" + description,
                e == null ? new IllegalStateException(reasonCode) : e
        );
    }

    private String resolveCanonicalHdFailureReason(Exception e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof TosServerException serverException) {
                return serverException.getStatusCode() == 404 ? "TOS_OBJECT_NOT_FOUND" : "TOS_READ_FAILED";
            }
            if (current instanceof TosClientException) {
                String message = current.getMessage();
                return message != null && message.toLowerCase().contains("timeout") ? "TIMEOUT" : "TOS_READ_FAILED";
            }
            current = current.getCause();
        }
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (message.contains("不存在") || message.contains("not found") || message.contains("404")) {
            return "TOS_OBJECT_NOT_FOUND";
        }
        if (message.contains("timeout") || message.contains("timed out") || message.contains("超时")) {
            return "TIMEOUT";
        }
        return "UNKNOWN_ERROR";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void logHdImageAccessFailure(String reason,
                                         Artwork artwork,
                                         Artwork.HdImageStorageType storageType,
                                         Path storageRoot,
                                         Path resolvedPath,
                                         Exception exception) {
        log.warn("超清无损图访问失败: reason={}, artworkId={}, externalId={}, taskId={}, hdImageStatus={}, storageType={}, storageRoot={}, hdImagePath={}, resolvedPath={}, resolvedExists={}, objectConfigId={}, objectBucket={}, objectKey={}, migrationStatus={}, migrationLastError={}, lastError={}",
                reason,
                artwork.getId(),
                artwork.getExternalId(),
                artwork.getTask() == null ? null : artwork.getTask().getId(),
                artwork.getHdImageStatus(),
                storageType,
                storageRoot,
                artwork.getHdImagePath(),
                resolvedPath,
                resolvedPath == null ? null : Files.exists(resolvedPath),
                artwork.getHdImageObjectConfigId(),
                artwork.getHdImageObjectBucket(),
                artwork.getHdImageObjectKey(),
                artwork.getHdImageMigrationStatus(),
                artwork.getHdImageMigrationLastError(),
                artwork.getHdImageLastError(),
                exception);
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

    public MediaType resolveCanonicalMediaType(Long artworkId) {
        return MediaType.IMAGE_PNG;
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
        return ensureHdImageStoredWithMetrics(artworkId, force).outcome();
    }

    private ArtworkDownloadResult ensureHdImageStoredWithMetrics(Long artworkId, boolean force) {
        long totalStart = System.nanoTime();
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));
        Long taskId = taskIdOf(artwork);

        Path root = storageRoot();
        Path existingPath = resolveStoredPath(root, artwork.getHdImagePath());
        if (!force
                && artwork.getHdImageStatus() == Artwork.HdImageStatus.DOWNLOADED
                && existingPath != null
                && Files.exists(existingPath)) {
            return new ArtworkDownloadResult(
                    DownloadOutcome.SKIPPED,
                    nanosToMillis(System.nanoTime() - totalStart),
                    ArtworkProcessMetrics.skipped(taskId, artworkId, resolveArtCode(artwork),
                            nanosToMillis(System.nanoTime() - totalStart))
            );
        }

        String artCode = resolveArtCode(artwork);
        if (artCode == null || artCode.isBlank()) {
            artwork.setHdImageStatus(Artwork.HdImageStatus.FAILED);
            artwork.setHdImageLastError("无法解析超清无损图拍品编号");
            artworkRepository.save(artwork);
            long totalMs = nanosToMillis(System.nanoTime() - totalStart);
            return new ArtworkDownloadResult(
                    DownloadOutcome.FAILED,
                    totalMs,
                    ArtworkProcessMetrics.failed(taskId, artworkId, null, 0, 0, 0L, 0L,
                            StitchMetrics.empty(), 0L, 0L, totalMs, "resolve-art-code")
            );
        }

        String viewerUrl = VIEWER_URL_TEMPLATE.formatted(artCode);
        artwork.setHdImageSourceUrl(viewerUrl);
        String stage = "prepare";
        long metadataMs = 0L;
        long fileWriteMs = 0L;
        long dbSaveMs = 0L;
        long outputBytes = 0L;
        int width = 0;
        int height = 0;
        StitchMetrics stitchMetrics = StitchMetrics.empty();

        try {
            stage = "fetch-option";
            long metadataStart = System.nanoTime();
            HdImageOption option = fetchHdImageOption(artCode, viewerUrl);
            metadataMs = nanosToMillis(System.nanoTime() - metadataStart);
            width = option.width();
            height = option.height();

            stage = "stitch-tiles";
            StitchResult stitchResult = stitchTiles(artCode, viewerUrl, option);
            BufferedImage stitchedImage = stitchResult.image();
            stitchMetrics = stitchResult.metrics();
            AppProperties.HdWriteMode writeMode = resolveHdWriteMode();
            boolean retainLocalFile = writeMode != AppProperties.HdWriteMode.TOS_ONLY;
            String relativePath = retainLocalFile ? buildRelativePath(artwork) : null;
            Path targetPath = relativePath == null ? null : root.resolve(relativePath).normalize();

            stage = retainLocalFile ? "write-png" : "encode-png";
            long fileWriteStart = System.nanoTime();
            byte[] pngBytes;
            if (retainLocalFile) {
                Files.createDirectories(root);
                Files.createDirectories(targetPath.getParent());
                Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
                writePng(tempPath, stitchedImage);
                try {
                    Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicMoveError) {
                    Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                outputBytes = Files.size(targetPath);
                pngBytes = null;
            } else {
                pngBytes = encodePng(stitchedImage);
                outputBytes = pngBytes.length;
            }
            fileWriteMs = nanosToMillis(System.nanoTime() - fileWriteStart);

            stage = "save-db";
            long dbSaveStart = System.nanoTime();
            ObjectUploadMetadata objectUpload = uploadCanonicalToObjectStorageIfNeeded(
                    artwork,
                    targetPath,
                    pngBytes,
                    outputBytes,
                    writeMode
            );
            artwork.setHdImagePath(retainLocalFile ? relativePath : null);
            artwork.setHdImageContentType(MediaType.IMAGE_PNG_VALUE);
            artwork.setHdImageSize(outputBytes);
            artwork.setHdImageDownloadedAt(LocalDateTime.now());
            artwork.setHdImageStatus(Artwork.HdImageStatus.DOWNLOADED);
            artwork.setHdImageLastError(null);
            applyObjectUpload(artwork, objectUpload);
            artworkRepository.save(artwork);
            dbSaveMs = nanosToMillis(System.nanoTime() - dbSaveStart);

            boolean existedBefore = existingPath != null && Files.exists(existingPath);
            DownloadOutcome outcome = (force || !existedBefore) ? DownloadOutcome.DOWNLOADED : DownloadOutcome.SKIPPED;
            long totalMs = nanosToMillis(System.nanoTime() - totalStart);
            ArtworkProcessMetrics metrics = ArtworkProcessMetrics.finished(
                    taskId,
                    artworkId,
                    artCode,
                    width,
                    height,
                    stitchMetrics,
                    outputBytes,
                    metadataMs,
                    fileWriteMs,
                    dbSaveMs,
                    totalMs,
                    dominantStage(metadataMs, stitchMetrics.wallMs(), fileWriteMs, dbSaveMs)
            );
            if (outcome == DownloadOutcome.DOWNLOADED) {
                log.info("超清无损图完成: taskId={}, artworkId={}, artCode={}, size={}x{}, tiles={}/{}x{}, output={}MiB, total={}ms, metadata={}ms, stitch={}ms, pngWrite={}ms, dbSave={}ms, tileBytes={}MiB, tileRequestAvg={}ms, tileRequestMax={}ms, tileDecodeAvg={}ms, tileDecodeMax={}ms, tileDrawAvg={}ms, tileDrawMax={}ms, dominantStage={}",
                        taskId,
                        artworkId,
                        artCode,
                        width,
                        height,
                        stitchMetrics.totalTiles(),
                        stitchMetrics.rows(),
                        stitchMetrics.columns(),
                        metrics.outputMiB(),
                        totalMs,
                        metadataMs,
                        stitchMetrics.wallMs(),
                        fileWriteMs,
                        dbSaveMs,
                        stitchMetrics.totalTileMiB(),
                        stitchMetrics.avgRequestMs(),
                        stitchMetrics.maxRequestMs(),
                        stitchMetrics.avgDecodeMs(),
                        stitchMetrics.maxDecodeMs(),
                        stitchMetrics.avgDrawMs(),
                        stitchMetrics.maxDrawMs(),
                        metrics.dominantStage());
            }
            return new ArtworkDownloadResult(outcome, totalMs, metrics);
        } catch (Exception e) {
            if (e instanceof HdImageStitchException stitchException) {
                stitchMetrics = stitchException.metrics();
            }
            long totalMs = nanosToMillis(System.nanoTime() - totalStart);
            ArtworkProcessMetrics metrics = ArtworkProcessMetrics.failed(
                    taskId,
                    artworkId,
                    artCode,
                    width,
                    height,
                    metadataMs,
                    fileWriteMs,
                    stitchMetrics,
                    dbSaveMs,
                    outputBytes,
                    totalMs,
                    stage
            );
            log.warn("超清无损图下载失败: taskId={}, artworkId={}, artCode={}, stage={}, size={}x{}, tiles={}/{}, total={}ms, metadata={}ms, stitch={}ms, pngWrite={}ms, dbSave={}ms, tileBytes={}MiB, tileRequestAvg={}ms, tileRequestMax={}ms, tileDecodeAvg={}ms, tileDecodeMax={}ms, tileDrawAvg={}ms, tileDrawMax={}ms, dominantStage={}, message={}",
                    taskId,
                    artworkId,
                    artCode,
                    stage,
                    width,
                    height,
                    stitchMetrics.completedTiles(),
                    stitchMetrics.totalTiles(),
                    totalMs,
                    metadataMs,
                    stitchMetrics.wallMs(),
                    fileWriteMs,
                    dbSaveMs,
                    stitchMetrics.totalTileMiB(),
                    stitchMetrics.avgRequestMs(),
                    stitchMetrics.maxRequestMs(),
                    stitchMetrics.avgDecodeMs(),
                    stitchMetrics.maxDecodeMs(),
                    stitchMetrics.avgDrawMs(),
                    stitchMetrics.maxDrawMs(),
                    metrics.dominantStage(),
                    e.getMessage(),
                    e);
            artwork.setHdImageStatus(Artwork.HdImageStatus.FAILED);
            artwork.setHdImageLastError(e.getMessage());
            artworkRepository.save(artwork);
            return new ArtworkDownloadResult(DownloadOutcome.FAILED, totalMs, metrics);
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

    private ObjectUploadMetadata uploadCanonicalToObjectStorageIfNeeded(Artwork artwork,
                                                                        Path targetPath,
                                                                        byte[] pngBytes,
                                                                        long outputBytes,
                                                                        AppProperties.HdWriteMode mode) throws Exception {
        if (mode == AppProperties.HdWriteMode.LEGACY_LOCAL) {
            return ObjectUploadMetadata.localOnly();
        }
        var config = objectStorageService.activeConfigForUpload();
        String artCode = resolveArtCode(artwork);
        if (artCode == null || artCode.isBlank()) {
            throw new IllegalStateException("无法解析高清大图 canonical artCode");
        }
        String objectKey = objectStorageService.buildCanonicalObjectKey(CANONICAL_SOURCE_PROVIDER, artCode);
        try {
            HdImageObjectStorageService.UploadResult result;
            if (targetPath == null) {
                result = objectStorageService.uploadBytes(config, pngBytes, objectKey);
            } else {
                result = objectStorageService.uploadFile(config, targetPath, objectKey);
            }
            objectStorageService.head(config, objectKey);
            return new ObjectUploadMetadata(
                    mode == AppProperties.HdWriteMode.TOS_ONLY
                            ? Artwork.HdImageStorageType.OBJECT
                            : Artwork.HdImageStorageType.LOCAL_OBJECT,
                    config.getId(),
                    config.getBucket(),
                    result.objectKey(),
                    result.etag(),
                    result.size(),
                    null
            );
        } catch (Exception e) {
            log.warn("高清图 canonical TOS 上传失败: artworkId={}, artCode={}, objectKey={}, mode={}, outputBytes={}, message={}",
                    artwork.getId(), artCode, objectKey, mode, outputBytes, e.getMessage(), e);
            throw e;
        }
    }

    private AppProperties.HdWriteMode resolveHdWriteMode() {
        AppProperties.HdWriteMode mode = appProperties.getImage().getHdWriteMode();
        return mode == null ? AppProperties.HdWriteMode.LEGACY_LOCAL : mode;
    }

    private AppProperties.HdDisplayMode resolveHdDisplayMode() {
        AppProperties.HdDisplayMode mode = appProperties.getImage().getHdDisplayMode();
        return mode == null ? AppProperties.HdDisplayMode.TOS_CANONICAL : mode;
    }

    private void applyObjectUpload(Artwork artwork, ObjectUploadMetadata metadata) {
        artwork.setHdImageStorageType(metadata.storageType());
        if (metadata.objectKey() != null && !metadata.objectKey().isBlank()) {
            artwork.setHdImageObjectConfigId(metadata.configId());
            artwork.setHdImageObjectBucket(metadata.bucket());
            artwork.setHdImageObjectKey(metadata.objectKey());
            artwork.setHdImageObjectEtag(metadata.etag());
            artwork.setHdImageObjectSize(metadata.size());
            artwork.setHdImageObjectUploadedAt(LocalDateTime.now());
            artwork.setHdImageMigrationStatus(Artwork.HdImageMigrationStatus.MIGRATED);
            artwork.setHdImageMigrationLastError(null);
            artwork.setHdImageMigrationUpdatedAt(LocalDateTime.now());
        } else if (metadata.errorMessage() != null) {
            artwork.setHdImageMigrationStatus(Artwork.HdImageMigrationStatus.FAILED);
            artwork.setHdImageMigrationLastError(metadata.errorMessage());
            artwork.setHdImageMigrationUpdatedAt(LocalDateTime.now());
        } else if (artwork.getHdImageMigrationStatus() == null) {
            artwork.setHdImageMigrationStatus(Artwork.HdImageMigrationStatus.NOT_MIGRATED);
        }
    }

    private StitchResult stitchTiles(String artCode, String viewerUrl, HdImageOption option) throws IOException {
        int width = option.width();
        int height = option.height();
        int tileSize = 256;
        int overlap = 1;
        int maxLevel = maxLevel(width, height);
        int columns = (int) Math.ceil(width / (double) tileSize);
        int rows = (int) Math.ceil(height / (double) tileSize);
        int totalTiles = rows * columns;
        int fetchConcurrency = Math.max(1, appProperties.getImage().getFetchConcurrency());
        long stitchStart = System.nanoTime();
        long totalTileBytes = 0L;
        long totalRequestMs = 0L;
        long maxRequestMs = 0L;
        long totalDecodeMs = 0L;
        long maxDecodeMs = 0L;
        long totalDrawMs = 0L;
        long maxDrawMs = 0L;
        int completedTiles = 0;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        List<Future<TileFetchResult>> futures = new ArrayList<>(totalTiles);
        try {
            if (fetchConcurrency <= 1 || totalTiles <= 1) {
                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < columns; col++) {
                        TileFetchResult tile = fetchTile(artCode, viewerUrl, option.sessionCookieHeader(), maxLevel, col, row);
                        completedTiles++;
                        totalTileBytes += tile.metrics().bytes();
                        totalRequestMs += tile.metrics().requestMs();
                        maxRequestMs = Math.max(maxRequestMs, tile.metrics().requestMs());
                        totalDecodeMs += tile.metrics().decodeMs();
                        maxDecodeMs = Math.max(maxDecodeMs, tile.metrics().decodeMs());
                        long drawStart = System.nanoTime();
                        drawTile(graphics, tile.image(), col, row, width, height, tileSize, overlap);
                        long drawMs = nanosToMillis(System.nanoTime() - drawStart);
                        totalDrawMs += drawMs;
                        maxDrawMs = Math.max(maxDrawMs, drawMs);
                    }
                }
                return new StitchResult(
                        image,
                        buildStitchMetrics(columns, rows, totalTiles, completedTiles, totalTileBytes,
                                totalRequestMs, maxRequestMs, totalDecodeMs, maxDecodeMs,
                                totalDrawMs, maxDrawMs, stitchStart)
                );
            }

            ExecutorCompletionService<TileFetchResult> completionService = new ExecutorCompletionService<>(tileExecutor);
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < columns; col++) {
                    final int tileRow = row;
                    final int tileCol = col;
                    futures.add(completionService.submit(
                            () -> fetchTile(artCode, viewerUrl, option.sessionCookieHeader(), maxLevel, tileCol, tileRow)
                    ));
                }
            }

            for (int i = 0; i < totalTiles; i++) {
                TileFetchResult tile = completionService.take().get();
                completedTiles++;
                totalTileBytes += tile.metrics().bytes();
                totalRequestMs += tile.metrics().requestMs();
                maxRequestMs = Math.max(maxRequestMs, tile.metrics().requestMs());
                totalDecodeMs += tile.metrics().decodeMs();
                maxDecodeMs = Math.max(maxDecodeMs, tile.metrics().decodeMs());
                long drawStart = System.nanoTime();
                drawTile(graphics, tile.image(), tile.col(), tile.row(), width, height, tileSize, overlap);
                long drawMs = nanosToMillis(System.nanoTime() - drawStart);
                totalDrawMs += drawMs;
                maxDrawMs = Math.max(maxDrawMs, drawMs);
            }
        } catch (InterruptedException e) {
            cancelFutures(futures);
            Thread.currentThread().interrupt();
            throw new HdImageStitchException(
                    "超清无损图下载已中断",
                    e,
                    buildStitchMetrics(columns, rows, totalTiles, completedTiles, totalTileBytes,
                            totalRequestMs, maxRequestMs, totalDecodeMs, maxDecodeMs,
                            totalDrawMs, maxDrawMs, stitchStart)
            );
        } catch (ExecutionException e) {
            cancelFutures(futures);
            Throwable cause = e.getCause();
            IOException ioException = cause instanceof IOException exception
                    ? exception
                    : new IOException("超清无损图瓦片下载失败: " + (cause == null ? "" : cause.getMessage()), cause);
            throw new HdImageStitchException(
                    ioException.getMessage(),
                    ioException,
                    buildStitchMetrics(columns, rows, totalTiles, completedTiles, totalTileBytes,
                            totalRequestMs, maxRequestMs, totalDecodeMs, maxDecodeMs,
                            totalDrawMs, maxDrawMs, stitchStart)
            );
        } catch (IOException | RuntimeException e) {
            throw new HdImageStitchException(
                    e.getMessage(),
                    e,
                    buildStitchMetrics(columns, rows, totalTiles, completedTiles, totalTileBytes,
                            totalRequestMs, maxRequestMs, totalDecodeMs, maxDecodeMs,
                            totalDrawMs, maxDrawMs, stitchStart)
            );
        } finally {
            graphics.dispose();
        }
        return new StitchResult(
                image,
                buildStitchMetrics(columns, rows, totalTiles, completedTiles, totalTileBytes,
                        totalRequestMs, maxRequestMs, totalDecodeMs, maxDecodeMs,
                        totalDrawMs, maxDrawMs, stitchStart)
        );
    }

    private TileFetchResult fetchTile(String artCode,
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

        long requestStart = System.nanoTime();
        Connection.Response response = connection.execute();
        long requestMs = nanosToMillis(System.nanoTime() - requestStart);
        String contentType = response.contentType();
        if (contentType != null && (contentType.contains("json") || contentType.contains("text"))) {
            String body = response.body();
            throw new IllegalStateException("超清无损图瓦片请求被拒绝: " + (body == null ? "" : body));
        }

        byte[] bytes = response.bodyAsBytes();
        long decodeStart = System.nanoTime();
        BufferedImage tile = ImageIO.read(new ByteArrayInputStream(bytes));
        long decodeMs = nanosToMillis(System.nanoTime() - decodeStart);
        if (tile == null) {
            throw new IllegalStateException("超清无损图瓦片无法解析");
        }
        return new TileFetchResult(col, row, tile, new TileMetrics(bytes.length, requestMs, decodeMs));
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
        int measuredArtworkCount = 0;
        long totalLatencyMs = 0;
        long maxLatencyMs = 0;
        long totalMeasuredArtworkMs = 0L;
        long maxArtworkMs = 0L;
        Long slowestArtworkId = null;
        long totalMetadataMs = 0L;
        long totalStitchMs = 0L;
        long totalFileWriteMs = 0L;
        long totalDbSaveMs = 0L;
        long totalTileBytes = 0L;
        long totalTileRequestMs = 0L;
        long maxTileRequestMs = 0L;
        long totalTileDecodeMs = 0L;
        long maxTileDecodeMs = 0L;
        long totalTileDrawMs = 0L;
        long maxTileDrawMs = 0L;
        long totalOutputBytes = 0L;
        long totalTiles = 0L;
        long completedTiles = 0L;
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
                ArtworkProcessMetrics metrics = result.metrics();
                if (metrics != null && result.outcome() != DownloadOutcome.SKIPPED) {
                    measuredArtworkCount++;
                    totalMeasuredArtworkMs += metrics.totalMs();
                    maxArtworkMs = Math.max(maxArtworkMs, metrics.totalMs());
                    totalMetadataMs += metrics.metadataMs();
                    totalStitchMs += metrics.stitchMs();
                    totalFileWriteMs += metrics.fileWriteMs();
                    totalDbSaveMs += metrics.dbSaveMs();
                    totalTileBytes += metrics.tileBytes();
                    totalTileRequestMs += metrics.tileRequestMs();
                    maxTileRequestMs = Math.max(maxTileRequestMs, metrics.maxTileRequestMs());
                    totalTileDecodeMs += metrics.tileDecodeMs();
                    maxTileDecodeMs = Math.max(maxTileDecodeMs, metrics.maxTileDecodeMs());
                    totalTileDrawMs += metrics.tileDrawMs();
                    maxTileDrawMs = Math.max(maxTileDrawMs, metrics.maxTileDrawMs());
                    totalOutputBytes += metrics.outputBytes();
                    totalTiles += metrics.totalTiles();
                    completedTiles += metrics.completedTiles();
                    if (metrics.totalMs() >= maxArtworkMs) {
                        slowestArtworkId = metrics.artworkId();
                    }
                }

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
        BatchTiming batchTiming = new BatchTiming(
                measuredArtworkCount,
                totalMeasuredArtworkMs,
                maxArtworkMs,
                slowestArtworkId,
                totalMetadataMs,
                totalStitchMs,
                totalFileWriteMs,
                totalDbSaveMs,
                totalTiles,
                completedTiles,
                totalTileBytes,
                totalTileRequestMs,
                maxTileRequestMs,
                totalTileDecodeMs,
                maxTileDecodeMs,
                totalTileDrawMs,
                maxTileDrawMs,
                totalOutputBytes,
                dominantStage(totalMetadataMs, totalStitchMs, totalFileWriteMs, totalDbSaveMs)
        );
        return new BatchRunResult(downloadedCount, skippedCount, failedCount, pageMetrics, batchTiming);
    }

    private ArtworkDownloadResult ensureHdImageStoredWithMetrics(Long artworkId) {
        return ensureHdImageStoredWithMetrics(artworkId, false);
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

    private void writePng(Path path, BufferedImage image) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("未找到 PNG 编码器");
        }
    }

    private byte[] encodePng(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("未找到 PNG 编码器");
            }
            return output.toByteArray();
        }
    }

    private StitchMetrics buildStitchMetrics(int columns,
                                             int rows,
                                             int totalTiles,
                                             int completedTiles,
                                             long totalTileBytes,
                                             long totalRequestMs,
                                             long maxRequestMs,
                                             long totalDecodeMs,
                                             long maxDecodeMs,
                                             long totalDrawMs,
                                             long maxDrawMs,
                                             long stitchStart) {
        return new StitchMetrics(
                columns,
                rows,
                totalTiles,
                completedTiles,
                totalTileBytes,
                totalRequestMs,
                maxRequestMs,
                totalDecodeMs,
                maxDecodeMs,
                totalDrawMs,
                maxDrawMs,
                nanosToMillis(System.nanoTime() - stitchStart)
        );
    }

    private Long taskIdOf(Artwork artwork) {
        return artwork.getTask() == null ? null : artwork.getTask().getId();
    }

    private String dominantStage(long metadataMs, long stitchMs, long fileWriteMs, long dbSaveMs) {
        String dominantStage = "metadata";
        long dominantDuration = metadataMs;
        if (stitchMs >= dominantDuration) {
            dominantStage = "stitch";
            dominantDuration = stitchMs;
        }
        if (fileWriteMs >= dominantDuration) {
            dominantStage = "png-write";
            dominantDuration = fileWriteMs;
        }
        if (dbSaveMs >= dominantDuration) {
            dominantStage = "db-save";
        }
        return dominantStage;
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

    private record TileMetrics(long bytes, long requestMs, long decodeMs) {
    }

    private record TileFetchResult(int col, int row, BufferedImage image, TileMetrics metrics) {
    }

    private record StitchResult(BufferedImage image, StitchMetrics metrics) {
    }

    private record StitchMetrics(int columns,
                                 int rows,
                                 int totalTiles,
                                 int completedTiles,
                                 long totalTileBytes,
                                 long totalRequestMs,
                                 long maxRequestMs,
                                 long totalDecodeMs,
                                 long maxDecodeMs,
                                 long totalDrawMs,
                                 long maxDrawMs,
                                 long wallMs) {

        private static StitchMetrics empty() {
            return new StitchMetrics(0, 0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        private long avgRequestMs() {
            return completedTiles <= 0 ? 0L : Math.round((double) totalRequestMs / completedTiles);
        }

        private long avgDecodeMs() {
            return completedTiles <= 0 ? 0L : Math.round((double) totalDecodeMs / completedTiles);
        }

        private long avgDrawMs() {
            return completedTiles <= 0 ? 0L : Math.round((double) totalDrawMs / completedTiles);
        }

        private double totalTileMiB() {
            return totalTileBytes <= 0 ? 0D : Math.round(totalTileBytes / 104857.6D) / 10D;
        }
    }

    private record ArtworkProcessMetrics(Long taskId,
                                         Long artworkId,
                                         String artCode,
                                         int width,
                                         int height,
                                         int columns,
                                         int rows,
                                         int totalTiles,
                                         int completedTiles,
                                         long tileBytes,
                                         long tileRequestMs,
                                         long maxTileRequestMs,
                                         long tileDecodeMs,
                                         long maxTileDecodeMs,
                                         long tileDrawMs,
                                         long maxTileDrawMs,
                                         long outputBytes,
                                         long metadataMs,
                                         long stitchMs,
                                         long fileWriteMs,
                                         long dbSaveMs,
                                         long totalMs,
                                         String dominantStage) {

        private static ArtworkProcessMetrics finished(Long taskId,
                                                      Long artworkId,
                                                      String artCode,
                                                      int width,
                                                      int height,
                                                      StitchMetrics stitchMetrics,
                                                      long outputBytes,
                                                      long metadataMs,
                                                      long fileWriteMs,
                                                      long dbSaveMs,
                                                      long totalMs,
                                                      String dominantStage) {
            return new ArtworkProcessMetrics(
                    taskId,
                    artworkId,
                    artCode,
                    width,
                    height,
                    stitchMetrics.columns(),
                    stitchMetrics.rows(),
                    stitchMetrics.totalTiles(),
                    stitchMetrics.completedTiles(),
                    stitchMetrics.totalTileBytes(),
                    stitchMetrics.totalRequestMs(),
                    stitchMetrics.maxRequestMs(),
                    stitchMetrics.totalDecodeMs(),
                    stitchMetrics.maxDecodeMs(),
                    stitchMetrics.totalDrawMs(),
                    stitchMetrics.maxDrawMs(),
                    outputBytes,
                    metadataMs,
                    stitchMetrics.wallMs(),
                    fileWriteMs,
                    dbSaveMs,
                    totalMs,
                    dominantStage
            );
        }

        private static ArtworkProcessMetrics failed(Long taskId,
                                                    Long artworkId,
                                                    String artCode,
                                                    int width,
                                                    int height,
                                                    long metadataMs,
                                                    long fileWriteMs,
                                                    StitchMetrics stitchMetrics,
                                                    long dbSaveMs,
                                                    long outputBytes,
                                                    long totalMs,
                                                    String stage) {
            return new ArtworkProcessMetrics(
                    taskId,
                    artworkId,
                    artCode,
                    width,
                    height,
                    stitchMetrics.columns(),
                    stitchMetrics.rows(),
                    stitchMetrics.totalTiles(),
                    stitchMetrics.completedTiles(),
                    stitchMetrics.totalTileBytes(),
                    stitchMetrics.totalRequestMs(),
                    stitchMetrics.maxRequestMs(),
                    stitchMetrics.totalDecodeMs(),
                    stitchMetrics.maxDecodeMs(),
                    stitchMetrics.totalDrawMs(),
                    stitchMetrics.maxDrawMs(),
                    outputBytes,
                    metadataMs,
                    stitchMetrics.wallMs(),
                    fileWriteMs,
                    dbSaveMs,
                    totalMs,
                    stage
            );
        }

        private static ArtworkProcessMetrics skipped(Long taskId,
                                                     Long artworkId,
                                                     String artCode,
                                                     long totalMs) {
            return new ArtworkProcessMetrics(
                    taskId,
                    artworkId,
                    artCode,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    totalMs,
                    "skipped"
            );
        }

        private double outputMiB() {
            return outputBytes <= 0 ? 0D : Math.round(outputBytes / 104857.6D) / 10D;
        }
    }

    private record ArtworkDownloadResult(DownloadOutcome outcome,
                                         long durationMs,
                                         ArtworkProcessMetrics metrics) {
    }

    private record BatchRunResult(int downloadedCount,
                                  int skippedCount,
                                  int failedCount,
                                  PagePerformanceMetrics pageMetrics,
                                  BatchTiming batchTiming) {
    }

    private record BatchTiming(int measuredArtworkCount,
                               long totalArtworkMs,
                               long maxArtworkMs,
                               Long slowestArtworkId,
                               long totalMetadataMs,
                               long totalStitchMs,
                               long totalFileWriteMs,
                               long totalDbSaveMs,
                               long totalTiles,
                               long completedTiles,
                               long totalTileBytes,
                               long totalTileRequestMs,
                               long maxTileRequestMs,
                               long totalTileDecodeMs,
                               long maxTileDecodeMs,
                               long totalTileDrawMs,
                               long maxTileDrawMs,
                               long totalOutputBytes,
                               String dominantStage) {

        private long avgArtworkMs() {
            return measuredArtworkCount <= 0 ? 0L : Math.round((double) totalArtworkMs / measuredArtworkCount);
        }

        private long avgMetadataMs() {
            return measuredArtworkCount <= 0 ? 0L : Math.round((double) totalMetadataMs / measuredArtworkCount);
        }

        private long avgStitchMs() {
            return measuredArtworkCount <= 0 ? 0L : Math.round((double) totalStitchMs / measuredArtworkCount);
        }

        private long avgFileWriteMs() {
            return measuredArtworkCount <= 0 ? 0L : Math.round((double) totalFileWriteMs / measuredArtworkCount);
        }

        private long avgDbSaveMs() {
            return measuredArtworkCount <= 0 ? 0L : Math.round((double) totalDbSaveMs / measuredArtworkCount);
        }

        private long avgTileRequestMs() {
            return completedTiles <= 0 ? 0L : Math.round((double) totalTileRequestMs / completedTiles);
        }

        private long avgTileDecodeMs() {
            return completedTiles <= 0 ? 0L : Math.round((double) totalTileDecodeMs / completedTiles);
        }

        private long avgTileDrawMs() {
            return completedTiles <= 0 ? 0L : Math.round((double) totalTileDrawMs / completedTiles);
        }

        private double totalTileMiB() {
            return totalTileBytes <= 0 ? 0D : Math.round(totalTileBytes / 104857.6D) / 10D;
        }

        private double totalOutputMiB() {
            return totalOutputBytes <= 0 ? 0D : Math.round(totalOutputBytes / 104857.6D) / 10D;
        }
    }

    private record HdImageOption(String token, int width, int height, String sessionCookieHeader) {
    }

    private record ObjectUploadMetadata(Artwork.HdImageStorageType storageType,
                                        Long configId,
                                        String bucket,
                                        String objectKey,
                                        String etag,
                                        Long size,
                                        String errorMessage) {
        static ObjectUploadMetadata localOnly() {
            return new ObjectUploadMetadata(Artwork.HdImageStorageType.LOCAL, null, null, null, null, null, null);
        }
    }

    private static class HdImageStitchException extends IOException {
        private final StitchMetrics metrics;

        private HdImageStitchException(String message, Throwable cause, StitchMetrics metrics) {
            super(message, cause);
            this.metrics = metrics;
        }

        private StitchMetrics metrics() {
            return metrics;
        }
    }
}
