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
        SearchTask.TaskType taskType = parseTaskType(request.getTaskType());
        SearchTask task = new SearchTask();
        task.setTaskType(taskType);
        task.setName(resolveTaskName(request, taskType));
        task.setKeyword(resolveKeyword(request, taskType));
        task.setTargetTaskId(resolveTargetTaskId(request, taskType));
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
        log.info("暂停任务: id={}", id);
        return toDto(task);
    }

    public TaskDto resumeTask(Long id) {
        SearchTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));

        if (task.getStatus() != SearchTask.TaskStatus.PAUSED) {
            throw new IllegalStateException("只有已暂停的任务才能恢复");
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
        log.info("取消任务: id={}", id);
        return toDto(task);
    }

    @Transactional
    public void deleteTask(Long id) {
        interruptTask(id);
        SearchTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));
        artworkRepository.deleteAll(artworkRepository.findAll(ArtworkSpec.search(id, null, null, null, null, null)));
        fetchFailureService.deleteTaskFailures(id);
        taskRepository.delete(task);
        log.info("删除任务: id={}", id);
    }

    public java.util.List<FetchFailureDto> listFailures(Long taskId) {
        return fetchFailureService.listTaskFailures(taskId);
    }

    public java.util.List<FetchFailureDto> retryFailures(Long taskId) {
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
                if (task == null || task.getStatus() != SearchTask.TaskStatus.RUNNING) break;

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

    private TaskDto toDto(SearchTask task) {
        TaskDto dto = TaskDto.from(task);
        dto.setArtworkCount(artworkRepository.countByTaskId(task.getId()));
        dto.setPendingFailureCount(fetchFailureService.countPendingFailures(task.getId()));
        dto.setEstimatedRemainingMs(estimateRemainingMs(task));
        if (task.getTargetTaskId() != null) {
            taskRepository.findById(task.getTargetTaskId())
                    .ifPresent(targetTask -> dto.setTargetTaskName(targetTask.getName()));
        }
        return dto;
    }

    private Long estimateRemainingMs(SearchTask task) {
        if (task.getStatus() == SearchTask.TaskStatus.COMPLETED
                || task.getStatus() == SearchTask.TaskStatus.CANCELLED) {
            return 0L;
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

        int remainingPages = remainingItems;
        long perPageEstimateMs = task.getLastPageDurationMs() + appProperties.getSource().getRequestDelayMs();
        return remainingPages * perPageEstimateMs;
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
        if (request.getKeyword() == null || request.getKeyword().isBlank()) {
            throw new IllegalArgumentException("检索关键词不能为空");
        }
        return request.getKeyword().trim();
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
            throw new IllegalArgumentException("补充类任务只能关联检索任务");
        }
        return targetTask;
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
