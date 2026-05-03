package com.artfetch.service;

import com.artfetch.config.AppProperties;
import com.artfetch.dto.CreateTaskRequest;
import com.artfetch.dto.FetchFailureDto;
import com.artfetch.dto.PageResult;
import com.artfetch.dto.TaskDto;
import com.artfetch.entity.FetchFailure;
import com.artfetch.entity.SearchTask;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.repository.ArtworkSpec;
import com.artfetch.repository.SearchTaskRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
public class TaskService {

    private final SearchTaskRepository taskRepository;
    private final ArtworkRepository artworkRepository;
    private final FetchService fetchService;
    private final FetchFailureService fetchFailureService;
    private final OriginalImageService originalImageService;
    private final HdImageService hdImageService;
    private final TransactionPriceService transactionPriceService;
    private final AppProperties appProperties;
    private final ExecutorService taskExecutor;

    // taskId -> 运行中的线程
    private final Map<Long, Thread> runningThreads = new ConcurrentHashMap<>();

    public TaskService(SearchTaskRepository taskRepository,
                       ArtworkRepository artworkRepository,
                       FetchService fetchService,
                       FetchFailureService fetchFailureService,
                       OriginalImageService originalImageService,
                       HdImageService hdImageService,
                       TransactionPriceService transactionPriceService,
                       AppProperties appProperties,
                       ExecutorService taskExecutor) {
        this.taskRepository = taskRepository;
        this.artworkRepository = artworkRepository;
        this.fetchService = fetchService;
        this.fetchFailureService = fetchFailureService;
        this.originalImageService = originalImageService;
        this.hdImageService = hdImageService;
        this.transactionPriceService = transactionPriceService;
        this.appProperties = appProperties;
        this.taskExecutor = taskExecutor;
    }

    @Transactional
    public TaskDto createTask(CreateTaskRequest request) {
        SearchTask.TaskType requestedTaskType = parseTaskType(request.getTaskType());
        if (requestedTaskType == SearchTask.TaskType.SEARCH || requestedTaskType == SearchTask.TaskType.SEARCH_BATCH) {
            List<String> keywords = resolveSearchKeywords(request);
            if (requestedTaskType == SearchTask.TaskType.SEARCH_BATCH) {
                if (keywords.size() < 2) {
                    throw new IllegalArgumentException("批量检索任务至少需要 2 个检索目标");
                }
                return createBatchSearchTask(request, keywords);
            }
            if (keywords.size() > 1) {
                return createBatchSearchTask(request, keywords);
            }
            return createSingleSearchTask(request, keywords.get(0));
        }

        SearchTask task = new SearchTask();
        task.setTaskType(requestedTaskType);
        task.setName(resolveTaskName(request, requestedTaskType));
        task.setKeyword(resolveKeyword(request, requestedTaskType));
        task.setTargetTaskId(resolveTargetTaskId(request, requestedTaskType));
        task.setStatus(SearchTask.TaskStatus.PENDING);
        taskRepository.save(task);
        log.info("创建任务: id={}, type={}, keyword={}, targetTaskId={}",
                task.getId(), task.getTaskType(), task.getKeyword(), task.getTargetTaskId());
        return toDto(task);
    }

    public PageResult<TaskDto> listTasks(int page, int size) {
        Page<SearchTask> taskPage = taskRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        return PageResult.of(taskPage, this::toDto);
    }

    public TaskDto getTask(Long id) {
        SearchTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));
        return toDto(task);
    }

    public TaskDto startTask(Long id) {
        SearchTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));

        if (task.getStatus() == SearchTask.TaskStatus.RUNNING) {
            throw new IllegalStateException("任务已在运行中");
        }
        if (task.getStatus() == SearchTask.TaskStatus.CANCELLED) {
            throw new IllegalStateException("已取消的任务不能重新启动");
        }
        if (task.getParentTaskId() != null) {
            SearchTask parentTask = taskRepository.findById(task.getParentTaskId()).orElse(null);
            if (parentTask != null && parentTask.getStatus() == SearchTask.TaskStatus.RUNNING) {
                throw new IllegalStateException("所属批量任务运行中，请等待批量任务完成后再单独启动该目标");
            }
        }
        if (task.getTaskType() == SearchTask.TaskType.SEARCH_BATCH && hasRunningBatchChild(task.getId())) {
            throw new IllegalStateException("批量任务中存在运行中的子任务，请稍后重试");
        }

        long runningCount = runningThreads.size();
        if (runningCount >= appProperties.getTask().getMaxConcurrentTasks()) {
            throw new IllegalStateException("已达到最大并发任务数限制: " + appProperties.getTask().getMaxConcurrentTasks());
        }

        task.setStatus(SearchTask.TaskStatus.RUNNING);
        task.setErrorMessage(null);
        taskRepository.save(task);

        submitTaskThread(task);
        log.info("启动任务: id={}", id);
        return toDto(task);
    }

    public TaskDto pauseTask(Long id) {
        SearchTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));

        if (task.getStatus() != SearchTask.TaskStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的任务才能暂停");
        }

        interruptTask(id);
        task.setStatus(SearchTask.TaskStatus.PAUSED);
        taskRepository.save(task);
        if (task.getTaskType() == SearchTask.TaskType.SEARCH_BATCH) {
            updateRunningBatchChildStatus(id, SearchTask.TaskStatus.PAUSED);
        }
        log.info("暂停任务: id={}", id);
        return toDto(task);
    }

    public TaskDto resumeTask(Long id) {
        SearchTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));

        if (task.getStatus() != SearchTask.TaskStatus.PAUSED) {
            throw new IllegalStateException("只有已暂停的任务才能恢复");
        }
        if (task.getTaskType() == SearchTask.TaskType.SEARCH_BATCH && hasRunningBatchChild(id)) {
            throw new IllegalStateException("批量任务中存在运行中的子任务，请稍后重试");
        }

        task.setStatus(SearchTask.TaskStatus.RUNNING);
        taskRepository.save(task);
        submitTaskThread(task);
        log.info("恢复任务: id={}", id);
        return toDto(task);
    }

    public TaskDto cancelTask(Long id) {
        SearchTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));

        interruptTask(id);
        task.setStatus(SearchTask.TaskStatus.CANCELLED);
        taskRepository.save(task);
        if (task.getTaskType() == SearchTask.TaskType.SEARCH_BATCH) {
            updateRunningBatchChildStatus(id, SearchTask.TaskStatus.CANCELLED);
        }
        log.info("取消任务: id={}", id);
        return toDto(task);
    }

    @Transactional
    public void deleteTask(Long id) {
        deleteTaskInternal(id);
    }

    public List<FetchFailureDto> listFailures(Long taskId) {
        return fetchFailureService.listTaskFailures(taskId);
    }

    public List<FetchFailureDto> retryFailures(Long taskId) {
        SearchTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        return fetchFailureService.listPendingFailures(taskId).stream()
                .map(failure -> FetchFailureDto.from(retryFailureInternal(task, failure)))
                .toList();
    }

    public FetchFailureDto retryFailure(Long taskId, Long failureId) {
        SearchTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        FetchFailure failure = fetchFailureService.getTaskFailure(taskId, failureId);
        return FetchFailureDto.from(retryFailureInternal(task, failure));
    }

    private TaskDto createSingleSearchTask(CreateTaskRequest request, String keyword) {
        SearchTask task = new SearchTask();
        task.setTaskType(SearchTask.TaskType.SEARCH);
        task.setName(resolveTaskName(request, SearchTask.TaskType.SEARCH));
        task.setKeyword(keyword);
        task.setStatus(SearchTask.TaskStatus.PENDING);
        taskRepository.save(task);
        log.info("创建检索任务: id={}, keyword={}", task.getId(), task.getKeyword());
        return toDto(task);
    }

    private TaskDto createBatchSearchTask(CreateTaskRequest request, List<String> keywords) {
        SearchTask batchTask = new SearchTask();
        batchTask.setTaskType(SearchTask.TaskType.SEARCH_BATCH);
        batchTask.setName(resolveTaskName(request, SearchTask.TaskType.SEARCH_BATCH));
        batchTask.setKeyword(buildBatchKeywordSummary(keywords));
        batchTask.setStatus(SearchTask.TaskStatus.PENDING);
        batchTask.setCurrentPage(0);
        batchTask.setTotalPages(keywords.size());
        taskRepository.save(batchTask);

        for (String keyword : keywords) {
            SearchTask childTask = new SearchTask();
            childTask.setTaskType(SearchTask.TaskType.SEARCH);
            childTask.setParentTaskId(batchTask.getId());
            childTask.setName(buildBatchChildTaskName(batchTask.getName(), keyword));
            childTask.setKeyword(keyword);
            childTask.setStatus(SearchTask.TaskStatus.PENDING);
            childTask.setCreatedAt(batchTask.getCreatedAt().minusSeconds(1));
            childTask.setUpdatedAt(childTask.getCreatedAt());
            taskRepository.save(childTask);
        }

        log.info("创建批量检索任务: id={}, name={}, targets={}", batchTask.getId(), batchTask.getName(), keywords.size());
        return toDto(batchTask);
    }

    private void submitTaskThread(SearchTask task) {
        final Long taskId = task.getId();
        Thread thread = new Thread(() -> runTaskLoop(taskId), "artfetch-task-" + taskId);
        thread.setDaemon(true);
        runningThreads.put(taskId, thread);
        taskExecutor.submit(thread);
    }

    private void runTaskLoop(Long taskId) {
        int intervalSeconds = appProperties.getSource().getFetchIntervalSeconds();
        log.info("Task[{}] 后台线程启动", taskId);

        try {
            while (!Thread.currentThread().isInterrupted()) {
                SearchTask task = taskRepository.findById(taskId).orElse(null);
                if (task == null || task.getStatus() != SearchTask.TaskStatus.RUNNING) {
                    break;
                }

                try {
                    SearchTask.TaskType taskType = task.getTaskType() == null
                            ? SearchTask.TaskType.SEARCH
                            : task.getTaskType();
                    if (taskType == SearchTask.TaskType.ORIGINAL_IMAGE) {
                        OriginalImageTaskResult result = originalImageService.runTask(task);
                        markTaskCompleted(taskId, result.getFailedCount() > 0
                                ? "原图补充已完成，但有 " + result.getFailedCount() + " 条下载失败，可在详情页重试"
                                : null);
                        break;
                    } else if (taskType == SearchTask.TaskType.HD_IMAGE) {
                        HdImageTaskResult result = hdImageService.runTask(task);
                        markTaskCompleted(taskId, result.getFailedCount() > 0
                                ? "超清无损图补充已完成，但有 " + result.getFailedCount() + " 条下载失败"
                                : null);
                        break;
                    } else if (taskType == SearchTask.TaskType.TRANSACTION_PRICE) {
                        TransactionPriceTaskResult result = transactionPriceService.runTask(task);
                        markTaskCompleted(taskId, buildTransactionPriceSummary(result));
                        break;
                    } else if (taskType == SearchTask.TaskType.SEARCH_BATCH) {
                        runBatchSearchTask(taskId);
                        break;
                    } else {
                        FetchRunResult result = fetchService.fetchAll(task);
                        if (!result.isCompletedAllPages()) {
                            markTaskFailed(taskId, result.getFatalErrorMessage());
                            break;
                        }

                        if (appProperties.getSource().getFetchIntervalSeconds() <= 0) {
                            markTaskCompleted(taskId, null);
                            break;
                        }

                        clearTaskError(taskId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Task[{}] 抓取异常: {}", taskId, e.getMessage(), e);
                    markTaskFailed(taskId, e.getMessage());
                    break;
                }

                // 等待间隔后重新开始
                log.info("Task[{}] 本轮抓取完毕，等待{}秒后重新开始...", taskId, intervalSeconds);
                try {
                    Thread.sleep(intervalSeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            runningThreads.remove(taskId);
            log.info("Task[{}] 后台线程退出", taskId);
        }
    }

    private void runBatchSearchTask(Long batchTaskId) throws InterruptedException {
        List<SearchTask> children = taskRepository.findByParentTaskIdOrderByIdAsc(batchTaskId);
        if (children.isEmpty()) {
            markTaskFailed(batchTaskId, "批量任务下没有可执行的检索目标");
            return;
        }

        refreshBatchTaskProgress(batchTaskId, null);

        for (int index = 0; index < children.size(); index++) {
            SearchTask childSummary = children.get(index);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Task interrupted");
            }

            SearchTask batchTask = taskRepository.findById(batchTaskId).orElse(null);
            if (batchTask == null || batchTask.getStatus() != SearchTask.TaskStatus.RUNNING) {
                return;
            }

            SearchTask childTask = taskRepository.findById(childSummary.getId()).orElse(null);
            if (childTask == null || childTask.getStatus() == SearchTask.TaskStatus.COMPLETED) {
                refreshBatchTaskProgress(batchTaskId, null);
                continue;
            }

            if (runningThreads.containsKey(childTask.getId())) {
                throw new IllegalStateException("子任务正在独立运行中: " + childTask.getName());
            }

            setBatchTaskMessage(batchTaskId,
                    "正在检索目标 " + (index + 1)
                            + "/" + children.size() + "：" + childTask.getKeyword());

            childTask.setStatus(SearchTask.TaskStatus.RUNNING);
            childTask.setErrorMessage(null);
            taskRepository.save(childTask);

            try {
                FetchRunResult result = fetchService.fetchAll(childTask);
                if (!result.isCompletedAllPages()) {
                    markTaskFailed(childTask.getId(), result.getFatalErrorMessage());
                } else {
                    markTaskCompleted(childTask.getId(), null);
                }
            } catch (InterruptedException e) {
                updateRunningBatchChildStatus(batchTaskId, resolveBatchChildTerminalStatus(batchTaskId));
                throw e;
            } catch (Exception e) {
                log.error("批量检索子任务失败: batchTaskId={}, childTaskId={}, keyword={}, message={}",
                        batchTaskId, childTask.getId(), childTask.getKeyword(), e.getMessage(), e);
                markTaskFailed(childTask.getId(), e.getMessage());
            }

            refreshBatchTaskProgress(batchTaskId, null);
        }

        finishBatchTask(batchTaskId);
    }

    private void finishBatchTask(Long batchTaskId) {
        taskRepository.findById(batchTaskId).ifPresent(batchTask -> {
            List<SearchTask> children = taskRepository.findByParentTaskIdOrderByIdAsc(batchTaskId);
            int completedCount = 0;
            int failedCount = 0;
            for (SearchTask child : children) {
                if (child.getStatus() == SearchTask.TaskStatus.COMPLETED) {
                    completedCount++;
                } else if (child.getStatus() == SearchTask.TaskStatus.FAILED
                        || child.getStatus() == SearchTask.TaskStatus.CANCELLED) {
                    failedCount++;
                }
            }

            batchTask.setStatus(SearchTask.TaskStatus.COMPLETED);
            batchTask.setCurrentPage(completedCount);
            batchTask.setTotalPages(children.size());
            batchTask.setErrorMessage(failedCount > 0
                    ? "批量检索已完成，成功 " + completedCount + " 个，失败 " + failedCount + " 个"
                    : null);
            taskRepository.save(batchTask);
        });
    }

    private void refreshBatchTaskProgress(Long batchTaskId, String message) {
        taskRepository.findById(batchTaskId).ifPresent(batchTask -> {
            List<SearchTask> children = taskRepository.findByParentTaskIdOrderByIdAsc(batchTaskId);
            batchTask.setCurrentPage((int) children.stream()
                    .filter(child -> child.getStatus() == SearchTask.TaskStatus.COMPLETED)
                    .count());
            batchTask.setTotalPages(children.size());
            if (message != null) {
                batchTask.setErrorMessage(message);
            }
            taskRepository.save(batchTask);
        });
    }

    private void setBatchTaskMessage(Long batchTaskId, String message) {
        taskRepository.findById(batchTaskId).ifPresent(batchTask -> {
            batchTask.setErrorMessage(message);
            taskRepository.save(batchTask);
        });
    }

    private SearchTask.TaskStatus resolveBatchChildTerminalStatus(Long batchTaskId) {
        return taskRepository.findById(batchTaskId)
                .map(SearchTask::getStatus)
                .filter(status -> status == SearchTask.TaskStatus.PAUSED || status == SearchTask.TaskStatus.CANCELLED)
                .orElse(SearchTask.TaskStatus.PAUSED);
    }

    private boolean hasRunningBatchChild(Long batchTaskId) {
        return taskRepository.findByParentTaskIdOrderByIdAsc(batchTaskId).stream()
                .anyMatch(child -> child.getStatus() == SearchTask.TaskStatus.RUNNING || runningThreads.containsKey(child.getId()));
    }

    private void updateRunningBatchChildStatus(Long batchTaskId, SearchTask.TaskStatus targetStatus) {
        taskRepository.findByParentTaskIdOrderByIdAsc(batchTaskId).stream()
                .filter(child -> child.getStatus() == SearchTask.TaskStatus.RUNNING)
                .forEach(child -> {
                    child.setStatus(targetStatus);
                    taskRepository.save(child);
                });
    }

    private void interruptTask(Long taskId) {
        Thread thread = runningThreads.remove(taskId);
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Transactional
    protected void markTaskCompleted(Long taskId) {
        markTaskCompleted(taskId, null);
    }

    @Transactional
    protected void markTaskCompleted(Long taskId, String message) {
        taskRepository.findById(taskId).ifPresent(t -> {
            t.setStatus(SearchTask.TaskStatus.COMPLETED);
            long pendingFailures = fetchFailureService.countPendingFailures(taskId);
            if (message != null && !message.isBlank()) {
                t.setErrorMessage(message);
            } else if ((t.getTaskType() == null || t.getTaskType() == SearchTask.TaskType.SEARCH) && pendingFailures > 0) {
                t.setErrorMessage("抓取已完成，但仍有 " + pendingFailures + " 条失败记录待重试");
            } else {
                t.setErrorMessage(null);
            }
            taskRepository.save(t);
            log.info("Task[{}] 已完成", taskId);
        });
    }

    @Transactional
    protected void markTaskFailed(Long taskId, String error) {
        taskRepository.findById(taskId).ifPresent(t -> {
            t.setStatus(SearchTask.TaskStatus.FAILED);
            t.setErrorMessage(error);
            taskRepository.save(t);
        });
    }

    @Transactional
    protected void clearTaskError(Long taskId) {
        taskRepository.findById(taskId).ifPresent(t -> {
            long pendingFailures = fetchFailureService.countPendingFailures(taskId);
            t.setErrorMessage(pendingFailures > 0
                    ? "抓取已完成，但仍有 " + pendingFailures + " 条失败记录待重试"
                    : null);
            taskRepository.save(t);
        });
    }

    private void deleteTaskInternal(Long id) {
        interruptTask(id);
        SearchTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));

        if (task.getTaskType() == SearchTask.TaskType.SEARCH_BATCH) {
            List<Long> childIds = taskRepository.findByParentTaskIdOrderByIdAsc(id).stream()
                    .map(SearchTask::getId)
                    .toList();
            for (Long childId : childIds) {
                deleteTaskInternal(childId);
            }
        }

        artworkRepository.deleteAll(artworkRepository.findAll(ArtworkSpec.search(id, null, null, null, null, null)));
        fetchFailureService.deleteTaskFailures(id);
        taskRepository.delete(task);
        log.info("删除任务: id={}", id);
    }

    private TaskDto toDto(SearchTask task) {
        TaskDto dto = TaskDto.from(task);
        if (task.getTaskType() == SearchTask.TaskType.SEARCH_BATCH) {
            dto.setArtworkCount(countBatchArtworks(task.getId()));
            dto.setPendingFailureCount(countBatchPendingFailures(task.getId()));
        } else {
            dto.setArtworkCount(artworkRepository.countByTaskId(task.getId()));
            dto.setPendingFailureCount(fetchFailureService.countPendingFailures(task.getId()));
        }
        dto.setEstimatedRemainingMs(estimateRemainingMs(task));
        if (task.getParentTaskId() != null) {
            taskRepository.findById(task.getParentTaskId())
                    .ifPresent(parentTask -> dto.setParentTaskName(parentTask.getName()));
        }
        if (task.getTargetTaskId() != null) {
            taskRepository.findById(task.getTargetTaskId())
                    .ifPresent(targetTask -> dto.setTargetTaskName(targetTask.getName()));
        }
        return dto;
    }

    private long countBatchArtworks(Long batchTaskId) {
        return taskRepository.findByParentTaskIdOrderByIdAsc(batchTaskId).stream()
                .mapToLong(child -> artworkRepository.countByTaskId(child.getId()))
                .sum();
    }

    private long countBatchPendingFailures(Long batchTaskId) {
        return taskRepository.findByParentTaskIdOrderByIdAsc(batchTaskId).stream()
                .mapToLong(child -> fetchFailureService.countPendingFailures(child.getId()))
                .sum();
    }

    private Long estimateRemainingMs(SearchTask task) {
        if (task.getStatus() == SearchTask.TaskStatus.COMPLETED
                || task.getStatus() == SearchTask.TaskStatus.CANCELLED) {
            return 0L;
        }
        if (task.getTaskType() == SearchTask.TaskType.SEARCH_BATCH) {
            return null;
        }
        if (task.getTotalPages() <= 0) {
            return null;
        }

        int remainingItems = Math.max(0, task.getTotalPages() - task.getCurrentPage());
        if (remainingItems == 0) {
            return 0L;
        }

        if (task.getLastPageItemsPerMinute() > 0) {
            return Math.max(1L, Math.round(remainingItems * 60_000D / task.getLastPageItemsPerMinute()));
        }
        if (task.getLastPageDurationMs() <= 0) {
            return null;
        }

        if (task.getTaskType() == SearchTask.TaskType.TRANSACTION_PRICE) {
            int batchSize = Math.max(1,
                    Math.max(appProperties.getPrice().getBatchSize(), appProperties.getPrice().getFetchConcurrency()));
            long remainingBatches = (long) Math.ceil((double) remainingItems / batchSize);
            return remainingBatches * task.getLastPageDurationMs();
        }
        if (task.getTaskType() == SearchTask.TaskType.ORIGINAL_IMAGE
                || task.getTaskType() == SearchTask.TaskType.HD_IMAGE) {
            int batchSize = Math.max(1,
                    Math.max(appProperties.getImage().getBatchSize(), appProperties.getImage().getArtworkConcurrency()));
            long remainingBatches = (long) Math.ceil((double) remainingItems / batchSize);
            return remainingBatches * task.getLastPageDurationMs();
        }

        long perPageEstimateMs = task.getLastPageDurationMs() + appProperties.getSource().getRequestDelayMs();
        return remainingItems * perPageEstimateMs;
    }

    private FetchFailure retryFailureInternal(SearchTask task, FetchFailure failure) {
        fetchFailureService.markRetryAttempted(failure.getId());
        boolean resolved = fetchService.retryFailure(task, failure);
        if (resolved) {
            fetchFailureService.markResolved(failure.getId());
        }

        SearchTask freshTask = taskRepository.findById(task.getId())
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + task.getId()));
        if (freshTask.getStatus() == SearchTask.TaskStatus.COMPLETED) {
            long pendingFailures = fetchFailureService.countPendingFailures(task.getId());
            freshTask.setErrorMessage(pendingFailures > 0
                    ? "抓取已完成，但仍有 " + pendingFailures + " 条失败记录待重试"
                    : null);
            taskRepository.save(freshTask);
        }

        return fetchFailureService.getTaskFailure(task.getId(), failure.getId());
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在中断所有运行中的任务...");
        runningThreads.values().forEach(Thread::interrupt);
        runningThreads.clear();
    }

    private SearchTask.TaskType parseTaskType(String value) {
        if (value == null || value.isBlank()) {
            return SearchTask.TaskType.SEARCH;
        }
        try {
            return SearchTask.TaskType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的任务类型: " + value);
        }
    }

    private List<String> resolveSearchKeywords(CreateTaskRequest request) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();

        if (request.getKeywords() != null) {
            request.getKeywords().forEach(keyword -> addKeyword(keywords, keyword));
        }

        if (keywords.isEmpty() && request.getKeyword() != null && !request.getKeyword().isBlank()) {
            String rawKeyword = request.getKeyword().trim();
            if (rawKeyword.contains("\n") || rawKeyword.contains("\r")) {
                for (String line : rawKeyword.split("\\r?\\n")) {
                    addKeyword(keywords, line);
                }
            } else {
                addKeyword(keywords, rawKeyword);
            }
        }

        if (keywords.isEmpty()) {
            throw new IllegalArgumentException("检索关键词不能为空");
        }

        return new ArrayList<>(keywords);
    }

    private void addKeyword(LinkedHashSet<String> keywords, String keyword) {
        if (keyword == null) {
            return;
        }
        String normalized = keyword.trim();
        if (!normalized.isBlank()) {
            keywords.add(normalized);
        }
    }

    private String resolveTaskName(CreateTaskRequest request, SearchTask.TaskType taskType) {
        if (request.getName() != null && !request.getName().isBlank()) {
            return request.getName().trim();
        }
        if (taskType == SearchTask.TaskType.ORIGINAL_IMAGE) {
            SearchTask targetTask = requireTargetSearchTask(request.getTargetTaskId());
            return targetTask.getName() + " 原图补充";
        }
        if (taskType == SearchTask.TaskType.HD_IMAGE) {
            SearchTask targetTask = requireTargetSearchTask(request.getTargetTaskId());
            return targetTask.getName() + " 超清无损图补充";
        }
        if (taskType == SearchTask.TaskType.TRANSACTION_PRICE) {
            SearchTask targetTask = requireTargetSearchTask(request.getTargetTaskId());
            return targetTask.getName() + " 成交价补充";
        }
        throw new IllegalArgumentException("任务名称不能为空");
    }

    private String resolveKeyword(CreateTaskRequest request, SearchTask.TaskType taskType) {
        if (taskType == SearchTask.TaskType.ORIGINAL_IMAGE
                || taskType == SearchTask.TaskType.HD_IMAGE
                || taskType == SearchTask.TaskType.TRANSACTION_PRICE) {
            return requireTargetSearchTask(request.getTargetTaskId()).getKeyword();
        }
        return resolveSearchKeywords(request).get(0);
    }

    private Long resolveTargetTaskId(CreateTaskRequest request, SearchTask.TaskType taskType) {
        if (taskType != SearchTask.TaskType.ORIGINAL_IMAGE
                && taskType != SearchTask.TaskType.HD_IMAGE
                && taskType != SearchTask.TaskType.TRANSACTION_PRICE) {
            return null;
        }
        return requireTargetSearchTask(request.getTargetTaskId()).getId();
    }

    private SearchTask requireTargetSearchTask(Long targetTaskId) {
        if (targetTaskId == null) {
            throw new IllegalArgumentException("补充类任务必须选择目标检索任务");
        }
        SearchTask targetTask = taskRepository.findById(targetTaskId)
                .orElseThrow(() -> new IllegalArgumentException("目标检索任务不存在: " + targetTaskId));
        if (targetTask.getTaskType() != null && targetTask.getTaskType() != SearchTask.TaskType.SEARCH) {
            throw new IllegalArgumentException("补充类任务只能关联单个检索目标任务");
        }
        return targetTask;
    }

    private String buildBatchKeywordSummary(List<String> keywords) {
        if (keywords.size() <= 3) {
            return String.join(" / ", keywords);
        }
        return String.join(" / ", keywords.subList(0, 3)) + " 等" + keywords.size() + "个目标";
    }

    private String buildBatchChildTaskName(String batchTaskName, String keyword) {
        return batchTaskName + " / " + keyword;
    }

    private String buildTransactionPriceSummary(TransactionPriceTaskResult result) {
        if (result.getFailedCount() == 0 && result.getLoginRequiredCount() == 0 && result.getMissingCount() == 0) {
            return null;
        }

        StringBuilder summary = new StringBuilder("成交价补充已完成");
        if (result.getFailedCount() > 0) {
            summary.append("，").append(result.getFailedCount()).append(" 条请求失败");
        }
        if (result.getLoginRequiredCount() > 0) {
            summary.append("，").append(result.getLoginRequiredCount()).append(" 条因需要登录未拿到成交价");
        }
        if (result.getMissingCount() > 0) {
            summary.append("，").append(result.getMissingCount()).append(" 条详情页未提供成交价");
        }
        return summary.toString();
    }
}
