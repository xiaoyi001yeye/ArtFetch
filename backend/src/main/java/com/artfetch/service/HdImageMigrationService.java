package com.artfetch.service;

import cn.dev33.satoken.stp.StpUtil;
import com.artfetch.config.AppProperties;
import com.artfetch.dto.CreateHdImageMigrationTaskRequest;
import com.artfetch.dto.HdImageMigrationItemDto;
import com.artfetch.dto.HdImageMigrationTaskDto;
import com.artfetch.dto.PageResult;
import com.artfetch.entity.Artwork;
import com.artfetch.entity.HdImageMigrationItem;
import com.artfetch.entity.HdImageMigrationTask;
import com.artfetch.entity.ObjectStorageConfig;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.repository.HdImageMigrationItemRepository;
import com.artfetch.repository.HdImageMigrationTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class HdImageMigrationService {

    private static final int MAX_UPLOAD_CONCURRENCY = 32;

    private final HdImageMigrationTaskRepository taskRepository;
    private final HdImageMigrationItemRepository itemRepository;
    private final ArtworkRepository artworkRepository;
    private final HdImageObjectStorageService objectStorageService;
    private final AppProperties appProperties;
    private final ExecutorService taskExecutor;

    public PageResult<HdImageMigrationTaskDto> listTasks(int page, int size) {
        return PageResult.of(taskRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)),
                HdImageMigrationTaskDto::from);
    }

    public HdImageMigrationTaskDto getTask(Long id) {
        return taskRepository.findById(id)
                .map(HdImageMigrationTaskDto::from)
                .orElseThrow(() -> new IllegalArgumentException("迁移任务不存在: " + id));
    }

    public PageResult<HdImageMigrationItemDto> listItems(Long taskId, String status, int page, int size) {
        var pageable = PageRequest.of(page, size);
        if (status != null && !status.isBlank()) {
            return PageResult.of(itemRepository.findByMigrationTaskIdAndStatusOrderByIdAsc(
                    taskId,
                    HdImageMigrationItem.Status.valueOf(status.trim().toUpperCase()),
                    pageable
            ), HdImageMigrationItemDto::from);
        }
        return PageResult.of(itemRepository.findByMigrationTaskIdOrderByIdAsc(taskId, pageable),
                HdImageMigrationItemDto::from);
    }

    @Transactional
    public HdImageMigrationTaskDto createTask(CreateHdImageMigrationTaskRequest request) {
        ObjectStorageConfig config = objectStorageService.loadConfig(request.getConfigId());
        if (!config.isEnabled() || !config.isMigrateEnabled()) {
            throw new IllegalStateException("请选择已启用且允许迁移的火山 TOS 配置");
        }

        HdImageMigrationTask task = new HdImageMigrationTask();
        task.setName(request.getName().trim());
        task.setConfigId(request.getConfigId());
        task.setMode(HdImageMigrationTask.Mode.valueOf(request.getMode().trim().toUpperCase()));
        task.setScopeType(HdImageMigrationTask.ScopeType.valueOf(request.getScopeType().trim().toUpperCase()));
        task.setTargetTaskId(task.getScopeType() == HdImageMigrationTask.ScopeType.SEARCH_TASK
                ? request.getTargetTaskId()
                : null);
        task.setUploadConcurrency(request.getUploadConcurrency() == null
                ? appProperties.getImage().getMigration().getUploadConcurrency()
                : Math.min(MAX_UPLOAD_CONCURRENCY, Math.max(1, request.getUploadConcurrency())));
        task.setCreatedBy(currentUserId());
        task = taskRepository.save(task);

        List<Artwork> candidates = findCandidates(task);
        for (Artwork artwork : candidates) {
            HdImageMigrationItem item = new HdImageMigrationItem();
            item.setMigrationTaskId(task.getId());
            item.setArtworkId(artwork.getId());
            item.setLocalPath(artwork.getHdImagePath());
            item.setObjectKey(objectStorageService.buildObjectKey(config, artwork));
            itemRepository.save(item);

            artwork.setHdImageMigrationStatus(Artwork.HdImageMigrationStatus.PENDING);
            artwork.setHdImageMigrationLastError(null);
            artwork.setHdImageMigrationUpdatedAt(LocalDateTime.now());
            artworkRepository.save(artwork);
        }
        task.setTotalCount(candidates.size());
        return HdImageMigrationTaskDto.from(taskRepository.save(task));
    }

    @Transactional
    public HdImageMigrationTaskDto start(Long id) {
        HdImageMigrationTask task = loadTask(id);
        if (task.getStatus() != HdImageMigrationTask.Status.PENDING
                && task.getStatus() != HdImageMigrationTask.Status.PAUSED) {
            throw new IllegalStateException("当前状态不能启动迁移任务");
        }
        ensureNoOtherRunning(task.getId());
        task.setStatus(HdImageMigrationTask.Status.RUNNING);
        task.setStartedAt(task.getStartedAt() == null ? LocalDateTime.now() : task.getStartedAt());
        task.setCompletedAt(null);
        task.setErrorMessage(null);
        taskRepository.save(task);
        taskExecutor.execute(() -> runTask(task.getId()));
        return HdImageMigrationTaskDto.from(task);
    }

    @Transactional
    public HdImageMigrationTaskDto pause(Long id) {
        HdImageMigrationTask task = loadTask(id);
        if (task.getStatus() == HdImageMigrationTask.Status.RUNNING) {
            task.setStatus(HdImageMigrationTask.Status.PAUSED);
        }
        return HdImageMigrationTaskDto.from(taskRepository.save(task));
    }

    @Transactional
    public HdImageMigrationTaskDto cancel(Long id) {
        HdImageMigrationTask task = loadTask(id);
        if (task.getStatus() == HdImageMigrationTask.Status.RUNNING
                || task.getStatus() == HdImageMigrationTask.Status.PENDING
                || task.getStatus() == HdImageMigrationTask.Status.PAUSED) {
            task.setStatus(HdImageMigrationTask.Status.CANCELLED);
            task.setCompletedAt(LocalDateTime.now());
        }
        return HdImageMigrationTaskDto.from(taskRepository.save(task));
    }

    @Transactional
    public HdImageMigrationTaskDto retryFailed(Long id) {
        HdImageMigrationTask task = loadTask(id);
        List<HdImageMigrationItem> failed = itemRepository.findByMigrationTaskIdAndStatusInOrderByIdAsc(
                id,
                Set.of(HdImageMigrationItem.Status.FAILED)
        );
        for (HdImageMigrationItem item : failed) {
            item.setStatus(HdImageMigrationItem.Status.PENDING);
            item.setErrorMessage(null);
            item.setStartedAt(null);
            item.setCompletedAt(null);
            itemRepository.save(item);
        }
        task.setStatus(HdImageMigrationTask.Status.PENDING);
        task.setProcessedCount(Math.max(0, task.getProcessedCount() - failed.size()));
        task.setFailedCount(Math.max(0, task.getFailedCount() - failed.size()));
        task.setErrorMessage(null);
        return HdImageMigrationTaskDto.from(taskRepository.save(task));
    }

    private void runTask(Long taskId) {
        ExecutorService uploadExecutor = null;
        try {
            HdImageMigrationTask task = loadTask(taskId);
            ObjectStorageConfig config = objectStorageService.loadConfig(task.getConfigId());
            List<HdImageMigrationItem> items = itemRepository.findRunnableItems(
                    taskId,
                    Set.of(HdImageMigrationItem.Status.PENDING, HdImageMigrationItem.Status.FAILED)
            );
            int uploadConcurrency = Math.max(1, task.getUploadConcurrency());
            uploadExecutor = createUploadExecutor(taskId, uploadConcurrency);
            CompletionService<MigrationOutcome> completionService = new ExecutorCompletionService<>(uploadExecutor);
            int failFastThreshold = Math.max(1, appProperties.getImage().getMigration().getFailFastThreshold());
            int consecutiveFailures = 0;
            int nextIndex = 0;
            int inFlight = 0;
            boolean stopScheduling = false;

            while (nextIndex < items.size() || inFlight > 0) {
                while (!stopScheduling && inFlight < uploadConcurrency && nextIndex < items.size()) {
                    if (!isTaskRunning(taskId)) {
                        stopScheduling = true;
                        break;
                    }
                    HdImageMigrationItem item = items.get(nextIndex++);
                    completionService.submit(() -> migrateOneSafely(taskId, item, config));
                    inFlight++;
                }

                if (inFlight == 0) {
                    break;
                }

                MigrationOutcome outcome = takeOutcome(completionService);
                inFlight--;
                if (outcome.failed()) {
                    consecutiveFailures++;
                    if (consecutiveFailures >= failFastThreshold) {
                        failTask(taskId, "连续失败超过阈值，请检查火山 TOS 配置或网络");
                        stopScheduling = true;
                    }
                } else {
                    consecutiveFailures = 0;
                }
            }

            if (!stopScheduling) {
                completeTask(taskId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("高清大图迁移任务被中断: migrationTaskId={}", taskId);
        } catch (Exception e) {
            log.warn("高清大图迁移任务失败: migrationTaskId={}, message={}", taskId, e.getMessage(), e);
            failTask(taskId, e.getMessage());
        } finally {
            if (uploadExecutor != null) {
                uploadExecutor.shutdownNow();
            }
        }
    }

    private MigrationOutcome migrateOneSafely(Long taskId, HdImageMigrationItem item, ObjectStorageConfig config) {
        try {
            migrateOne(taskId, item, config);
            return MigrationOutcome.completed(item.getId(), item.getArtworkId());
        } catch (Exception e) {
            String message = objectStorageService.describeTosError(e);
            log.warn("高清大图迁移失败: migrationTaskId={}, itemId={}, artworkId={}, message={}",
                    taskId, item.getId(), item.getArtworkId(), message, e);
            markItemFailed(taskId, item, message);
            return MigrationOutcome.failed(item.getId(), item.getArtworkId());
        }
    }

    private MigrationOutcome takeOutcome(CompletionService<MigrationOutcome> completionService)
            throws InterruptedException, ExecutionException {
        Future<MigrationOutcome> future = completionService.take();
        return future.get();
    }

    private void migrateOne(Long taskId, HdImageMigrationItem item, ObjectStorageConfig config) throws Exception {
        item.setStatus(HdImageMigrationItem.Status.UPLOADING);
        item.setStartedAt(LocalDateTime.now());
        item.setAttemptCount(item.getAttemptCount() + 1);
        itemRepository.save(item);

        Artwork artwork = artworkRepository.findById(item.getArtworkId())
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在: " + item.getArtworkId()));
        artwork.setHdImageMigrationStatus(Artwork.HdImageMigrationStatus.MIGRATING);
        artwork.setHdImageMigrationUpdatedAt(LocalDateTime.now());
        artworkRepository.save(artwork);

        Path localPath = storageRoot().resolve(artwork.getHdImagePath()).normalize();
        if (!localPath.startsWith(storageRoot()) || !Files.exists(localPath)) {
            markSkipped(taskId, item, artwork, "本地高清图文件不存在");
            return;
        }

        long fileSize = Files.size(localPath);
        item.setFileSize(fileSize);
        String objectKey = item.getObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            objectKey = objectStorageService.buildObjectKey(config, artwork);
            item.setObjectKey(objectKey);
        }

        if (objectStorageService.existsWithSize(config, objectKey, fileSize)) {
            HdImageObjectStorageService.ObjectMetadata metadata = objectStorageService.head(config, objectKey);
            markMigrated(taskId, item, artwork, config, metadata.etag(), fileSize);
            return;
        }

        HdImageObjectStorageService.UploadResult result = objectStorageService.uploadFile(config, localPath, objectKey);
        markMigrated(taskId, item, artwork, config, result.etag(), result.size());
    }

    private void markMigrated(Long taskId,
                              HdImageMigrationItem item,
                              Artwork artwork,
                              ObjectStorageConfig config,
                              String etag,
                              long size) {
        item.setStatus(HdImageMigrationItem.Status.MIGRATED);
        item.setUploadedSize(size);
        item.setEtag(etag);
        item.setErrorMessage(null);
        item.setCompletedAt(LocalDateTime.now());
        itemRepository.save(item);

        artwork.setHdImageStorageType(Artwork.HdImageStorageType.LOCAL_OBJECT);
        artwork.setHdImageObjectConfigId(config.getId());
        artwork.setHdImageObjectBucket(config.getBucket());
        artwork.setHdImageObjectKey(item.getObjectKey());
        artwork.setHdImageObjectEtag(etag);
        artwork.setHdImageObjectSize(size);
        artwork.setHdImageObjectUploadedAt(LocalDateTime.now());
        artwork.setHdImageMigrationStatus(Artwork.HdImageMigrationStatus.MIGRATED);
        artwork.setHdImageMigrationLastError(null);
        artwork.setHdImageMigrationUpdatedAt(LocalDateTime.now());
        artworkRepository.save(artwork);

        increment(taskId, item.getArtworkId(), 1, 1, 0, 0);
    }

    private void markSkipped(Long taskId, HdImageMigrationItem item, Artwork artwork, String reason) {
        item.setStatus(HdImageMigrationItem.Status.SKIPPED);
        item.setErrorMessage(reason);
        item.setCompletedAt(LocalDateTime.now());
        itemRepository.save(item);

        artwork.setHdImageMigrationStatus(Artwork.HdImageMigrationStatus.SKIPPED);
        artwork.setHdImageMigrationLastError(reason);
        artwork.setHdImageMigrationUpdatedAt(LocalDateTime.now());
        artworkRepository.save(artwork);

        increment(taskId, item.getArtworkId(), 1, 0, 1, 0);
    }

    private void markItemFailed(Long taskId, HdImageMigrationItem item, String reason) {
        item.setStatus(HdImageMigrationItem.Status.FAILED);
        item.setErrorMessage(reason);
        item.setCompletedAt(LocalDateTime.now());
        itemRepository.save(item);

        artworkRepository.findById(item.getArtworkId()).ifPresent(artwork -> {
            artwork.setHdImageMigrationStatus(Artwork.HdImageMigrationStatus.FAILED);
            artwork.setHdImageMigrationLastError(reason);
            artwork.setHdImageMigrationUpdatedAt(LocalDateTime.now());
            artworkRepository.save(artwork);
        });

        increment(taskId, item.getArtworkId(), 1, 0, 0, 1);
    }

    private void increment(Long taskId, Long currentArtworkId, int processed, int success, int skipped, int failed) {
        taskRepository.incrementProgress(taskId, currentArtworkId, processed, success, skipped, failed, LocalDateTime.now());
    }

    private void completeTask(Long taskId) {
        HdImageMigrationTask task = loadTask(taskId);
        if (task.getStatus() == HdImageMigrationTask.Status.RUNNING) {
            task.setStatus(task.getFailedCount() > 0 ? HdImageMigrationTask.Status.FAILED : HdImageMigrationTask.Status.COMPLETED);
            task.setErrorMessage(task.getFailedCount() > 0 ? "迁移完成，但有 " + task.getFailedCount() + " 条失败" : null);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        }
    }

    private void failTask(Long taskId, String message) {
        HdImageMigrationTask task = loadTask(taskId);
        task.setStatus(HdImageMigrationTask.Status.FAILED);
        task.setErrorMessage(message);
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    private List<Artwork> findCandidates(HdImageMigrationTask task) {
        Long targetTaskId = task.getTargetTaskId();
        return switch (task.getMode()) {
            case FULL -> artworkRepository.findAllDownloadedHdImages(targetTaskId);
            case INCREMENTAL -> artworkRepository.findIncrementalHdImagesForMigration(targetTaskId);
            case RETRY_FAILED -> artworkRepository.findFailedHdImagesForMigration(targetTaskId);
        };
    }

    private void ensureNoOtherRunning(Long currentTaskId) {
        long running = taskRepository.findByStatus(HdImageMigrationTask.Status.RUNNING).stream()
                .filter(task -> !task.getId().equals(currentTaskId))
                .count();
        if (running > 0) {
            throw new IllegalStateException("已有高清图迁移任务正在运行");
        }
    }

    private HdImageMigrationTask loadTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("迁移任务不存在: " + id));
    }

    private boolean isTaskRunning(Long taskId) {
        return loadTask(taskId).getStatus() == HdImageMigrationTask.Status.RUNNING;
    }

    private ExecutorService createUploadExecutor(Long taskId, int uploadConcurrency) {
        AtomicInteger threadSeq = new AtomicInteger(1);
        return Executors.newFixedThreadPool(uploadConcurrency, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("artfetch-hd-migration-" + taskId + "-" + threadSeq.getAndIncrement());
            return thread;
        });
    }

    private Path storageRoot() {
        return Path.of(appProperties.getImage().getStoragePath()).toAbsolutePath().normalize();
    }

    private Long currentUserId() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private record MigrationOutcome(Long itemId, Long artworkId, boolean failed) {
        private static MigrationOutcome completed(Long itemId, Long artworkId) {
            return new MigrationOutcome(itemId, artworkId, false);
        }

        private static MigrationOutcome failed(Long itemId, Long artworkId) {
            return new MigrationOutcome(itemId, artworkId, true);
        }
    }
}
