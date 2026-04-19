package com.artfetch.service;

import com.artfetch.config.AppProperties;
import com.artfetch.dto.ArtworkDto;
import com.artfetch.entity.Artwork;
import com.artfetch.entity.SearchTask;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.repository.SearchTaskRepository;
import com.artfetch.service.extractor.InitialStateExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class OriginalImageService {

    private final ArtworkRepository artworkRepository;
    private final SearchTaskRepository taskRepository;
    private final AppProperties appProperties;
    private final ArtronRequestSupport artronRequestSupport;

    public OriginalImageTaskResult runTask(SearchTask task) throws InterruptedException {
        if (task.getTargetTaskId() == null) {
            throw new IllegalStateException("补充原始图片任务缺少目标检索任务");
        }

        int totalCount = Math.toIntExact(artworkRepository.countByTaskId(task.getTargetTaskId()));
        updateTaskProgress(task.getId(), 0, totalCount, 0, 0, null);

        int batchSize = Math.max(1, appProperties.getImage().getBatchSize());
        int processed = 0;
        int downloaded = 0;
        int skipped = 0;
        int failed = 0;

        int page = 0;
        while (!Thread.currentThread().isInterrupted()) {
            long batchStart = System.nanoTime();
            int batchDownloaded = 0;
            Page<Artwork> artworkPage = artworkRepository.findByTaskIdOrderByIdAsc(
                    task.getTargetTaskId(),
                    PageRequest.of(page, batchSize)
            );
            if (artworkPage.isEmpty()) {
                break;
            }

            for (Artwork artwork : artworkPage.getContent()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Task interrupted");
                }

                DownloadOutcome outcome = ensureOriginalImageStored(artwork.getId(), false);
                processed++;
                switch (outcome) {
                    case DOWNLOADED -> {
                        downloaded++;
                        batchDownloaded++;
                    }
                    case SKIPPED -> skipped++;
                    case FAILED -> failed++;
                }

                long delayMs = appProperties.getSource().getRequestDelayMs();
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            }

            long batchDurationMs = Math.max(1L, (System.nanoTime() - batchStart) / 1_000_000L);
            updateTaskProgress(task.getId(), processed, totalCount, downloaded, batchDownloaded, batchDurationMs);
            page++;
        }

        updateTaskError(task.getId(), failed > 0
                ? "原图补充已完成，但有 " + failed + " 条下载失败，可在详情页重试"
                : null);
        return new OriginalImageTaskResult(totalCount, processed, downloaded, skipped, failed);
    }

    public ArtworkDto redownloadOriginalImage(Long artworkId) {
        ensureOriginalImageStored(artworkId, true);
        return artworkRepository.findById(artworkId)
                .map(ArtworkDto::from)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));
    }

    public Resource loadOriginalImage(Long artworkId) {
        ensureOriginalImageStored(artworkId, false);
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));
        if (artwork.getOriginalImagePath() == null || artwork.getOriginalImagePath().isBlank()) {
            throw new IllegalStateException("原始图片尚未准备好");
        }

        Path path = storageRoot().resolve(artwork.getOriginalImagePath()).normalize();
        if (!path.startsWith(storageRoot()) || !Files.exists(path)) {
            throw new IllegalStateException("原始图片文件不存在");
        }
        return new FileSystemResource(path);
    }

    public MediaType resolveMediaType(Long artworkId) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));
        if (artwork.getOriginalImageContentType() == null || artwork.getOriginalImageContentType().isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(artwork.getOriginalImageContentType());
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public String originalFilename(Long artworkId) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));
        String ext = extensionFromPath(artwork.getOriginalImagePath());
        String base = artwork.getExternalId() != null && !artwork.getExternalId().isBlank()
                ? artwork.getExternalId()
                : "artwork-" + artworkId;
        return base + ext;
    }

    @Transactional
    protected DownloadOutcome ensureOriginalImageStored(Long artworkId, boolean force) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + artworkId));

        Path root = storageRoot();
        Path existingPath = resolveStoredPath(root, artwork.getOriginalImagePath());
        if (!force
                && artwork.getOriginalImageStatus() == Artwork.OriginalImageStatus.DOWNLOADED
                && existingPath != null
                && Files.exists(existingPath)) {
            return DownloadOutcome.SKIPPED;
        }

        String sourceUrl = resolveOriginalImageSourceUrl(artwork);
        if (sourceUrl == null || sourceUrl.isBlank()) {
            artwork.setOriginalImageStatus(Artwork.OriginalImageStatus.FAILED);
            artwork.setOriginalImageLastError("无法解析原始图片地址");
            artworkRepository.save(artwork);
            return DownloadOutcome.FAILED;
        }

        try {
            Files.createDirectories(root);
            org.jsoup.Connection.Response response = artronRequestSupport.configure(
                            Jsoup.connect(sourceUrl),
                            artwork.getSourceUrl(),
                            appProperties.getImage().getDownloadTimeoutMs()
                    )
                    .ignoreContentType(true)
                    .execute();

            byte[] bytes = response.bodyAsBytes();
            if (bytes.length == 0) {
                throw new IllegalStateException("原始图片响应为空");
            }

            String contentType = response.contentType();
            String relativePath = buildRelativePath(artwork, sourceUrl, contentType);
            Path targetPath = root.resolve(relativePath).normalize();
            Files.createDirectories(targetPath.getParent());

            Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
            Files.write(tempPath, bytes);
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveError) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            artwork.setOriginalImageSourceUrl(sourceUrl);
            artwork.setOriginalImagePath(relativePath);
            artwork.setOriginalImageContentType(normalizeContentType(contentType, targetPath));
            artwork.setOriginalImageSize((long) bytes.length);
            artwork.setOriginalImageDownloadedAt(LocalDateTime.now());
            artwork.setOriginalImageStatus(Artwork.OriginalImageStatus.DOWNLOADED);
            artwork.setOriginalImageLastError(null);
            artworkRepository.save(artwork);

            boolean existedBefore = existingPath != null && Files.exists(existingPath);
            return (force || !existedBefore) ? DownloadOutcome.DOWNLOADED : DownloadOutcome.SKIPPED;
        } catch (Exception e) {
            log.warn("原始图片下载失败: artworkId={}, sourceUrl={}, message={}", artworkId, sourceUrl, e.getMessage(), e);
            artwork.setOriginalImageSourceUrl(sourceUrl);
            artwork.setOriginalImageStatus(Artwork.OriginalImageStatus.FAILED);
            artwork.setOriginalImageLastError(e.getMessage());
            artworkRepository.save(artwork);
            return DownloadOutcome.FAILED;
        }
    }

    private String resolveOriginalImageSourceUrl(Artwork artwork) {
        if (artwork.getOriginalImageSourceUrl() != null && !artwork.getOriginalImageSourceUrl().isBlank()) {
            return artwork.getOriginalImageSourceUrl().trim();
        }

        String derivedFromThumb = InitialStateExtractor.unwrapThumbUrl(artwork.getImageUrl());
        if (derivedFromThumb != null && !derivedFromThumb.isBlank()) {
            artwork.setOriginalImageSourceUrl(derivedFromThumb);
            artworkRepository.save(artwork);
            return derivedFromThumb;
        }

        if (artwork.getSourceUrl() == null || artwork.getSourceUrl().isBlank()) {
            return null;
        }

        try {
            Document doc = artronRequestSupport.configure(
                            Jsoup.connect(artwork.getSourceUrl()),
                            "https://artso.artron.net/",
                            appProperties.getImage().getDownloadTimeoutMs()
                    )
                    .get();
            String originalUrl = InitialStateExtractor.extractPrimaryOriginalImageUrl(doc);
            if (originalUrl != null && !originalUrl.isBlank()) {
                artwork.setOriginalImageSourceUrl(originalUrl);
                artworkRepository.save(artwork);
                return originalUrl;
            }
        } catch (Exception e) {
            log.warn("解析原始图片地址失败: artworkId={}, sourceUrl={}, message={}",
                    artwork.getId(), artwork.getSourceUrl(), e.getMessage());
        }
        return null;
    }

    @Transactional
    protected void updateTaskProgress(Long taskId,
                                      int processedCount,
                                      int totalCount,
                                      int downloadedCount,
                                      int batchDownloaded,
                                      Long batchDurationMs) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setCurrentPage(processedCount);
            task.setTotalPages(totalCount);
            task.setTotalFetched(downloadedCount);
            if (batchDurationMs != null) {
                task.setLastPageDurationMs(batchDurationMs);
                task.setLastPageItemsPerMinute(batchDownloaded > 0
                        ? batchDownloaded * 60_000D / Math.max(1L, batchDurationMs)
                        : 0D);
            }
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

    private String buildRelativePath(Artwork artwork, String sourceUrl, String contentType) {
        String ext = detectExtension(sourceUrl, contentType);
        String externalId = artwork.getExternalId() != null && !artwork.getExternalId().isBlank()
                ? artwork.getExternalId().replaceAll("[^A-Za-z0-9_-]", "_")
                : "artwork_" + artwork.getId();
        return "task-" + artwork.getTask().getId() + "/" + externalId + "/original" + ext;
    }

    private String detectExtension(String sourceUrl, String contentType) {
        if (contentType != null) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            if (lower.contains("png")) return ".png";
            if (lower.contains("webp")) return ".webp";
            if (lower.contains("gif")) return ".gif";
            if (lower.contains("bmp")) return ".bmp";
        }

        if (sourceUrl != null) {
            String decoded = URLDecoder.decode(sourceUrl, StandardCharsets.UTF_8);
            int q = decoded.indexOf('?');
            String clean = q >= 0 ? decoded.substring(0, q) : decoded;
            int dot = clean.lastIndexOf('.');
            if (dot >= 0 && dot < clean.length() - 1) {
                String ext = clean.substring(dot).toLowerCase(Locale.ROOT);
                if (ext.matches("\\.(jpg|jpeg|png|webp|gif|bmp)")) {
                    return ext.equals(".jpeg") ? ".jpg" : ext;
                }
            }
        }
        return ".jpg";
    }

    private String normalizeContentType(String contentType, Path path) throws IOException {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        String probed = Files.probeContentType(path);
        return probed == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : probed;
    }

    private String extensionFromPath(String path) {
        if (path == null || path.isBlank()) {
            return ".jpg";
        }
        int dot = path.lastIndexOf('.');
        return dot < 0 ? ".jpg" : path.substring(dot);
    }

    private enum DownloadOutcome {
        DOWNLOADED,
        SKIPPED,
        FAILED
    }
}
