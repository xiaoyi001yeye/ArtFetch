package com.artfetch.service;

import com.artfetch.config.AppProperties;
import com.artfetch.dto.CreateTaskRequest;
import com.artfetch.dto.PageResult;
import com.artfetch.dto.TaskDto;
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
    private final AppProperties appProperties;
    private final ExecutorService taskExecutor;

    // taskId -> 运行中的线程
    private final Map<Long, Thread> runningThreads = new ConcurrentHashMap<>();

    public TaskService(SearchTaskRepository taskRepository,
                       ArtworkRepository artworkRepository,
                       FetchService fetchService,
                       AppProperties appProperties,
                       ExecutorService taskExecutor) {
        this.taskRepository = taskRepository;
        this.artworkRepository = artworkRepository;
        this.fetchService = fetchService;
        this.appProperties = appProperties;
        this.taskExecutor = taskExecutor;
    }

    @Transactional
    public TaskDto createTask(CreateTaskRequest request) {
        SearchTask task = new SearchTask();
        task.setName(request.getName());
        task.setKeyword(request.getKeyword());
        task.setStatus(SearchTask.TaskStatus.PENDING);
        taskRepository.save(task);
        log.info("创建检索任务: id={}, keyword={}", task.getId(), task.getKeyword());
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
        artworkRepository.deleteAll(artworkRepository.findAll(ArtworkSpec.search(id, null, null, null, null)));
        taskRepository.delete(task);
        log.info("删除任务: id={}", id);
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
                    fetchService.fetchAll(task);
                    // 抓取正常完成
                    if (appProperties.getSource().getFetchIntervalSeconds() <= 0) {
                        markTaskCompleted(taskId);
                        break;
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
        taskRepository.findById(taskId).ifPresent(t -> {
            t.setStatus(SearchTask.TaskStatus.COMPLETED);
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

    private TaskDto toDto(SearchTask task) {
        TaskDto dto = TaskDto.from(task);
        dto.setArtworkCount(artworkRepository.countByTaskId(task.getId()));
        return dto;
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在中断所有运行中的任务...");
        runningThreads.values().forEach(Thread::interrupt);
        runningThreads.clear();
    }
}
