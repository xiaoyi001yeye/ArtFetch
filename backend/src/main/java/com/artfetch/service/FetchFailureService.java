package com.artfetch.service;

import com.artfetch.dto.FetchFailureDto;
import com.artfetch.entity.FetchFailure;
import com.artfetch.entity.SearchTask;
import com.artfetch.repository.FetchFailureRepository;
import com.artfetch.repository.SearchTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FetchFailureService {

    private final FetchFailureRepository fetchFailureRepository;
    private final SearchTaskRepository taskRepository;

    @Transactional
    public void recordListPageFailure(Long taskId, int pageNumber, String requestUrl, Exception e) {
        recordFailure(taskId,
                FetchFailure.FailureType.LIST_PAGE,
                buildFailureKey(taskId, FetchFailure.FailureType.LIST_PAGE, pageNumber, null),
                pageNumber,
                null,
                requestUrl,
                null,
                e);
    }

    @Transactional
    public void recordDetailFailure(Long taskId, int pageNumber, String externalId, String sourceUrl, Exception e) {
        recordFailure(taskId,
                FetchFailure.FailureType.DETAIL_PAGE,
                buildFailureKey(taskId, FetchFailure.FailureType.DETAIL_PAGE, pageNumber, externalId),
                pageNumber,
                externalId,
                sourceUrl,
                sourceUrl,
                e);
    }

    @Transactional(readOnly = true)
    public List<FetchFailureDto> listTaskFailures(Long taskId) {
        assertTaskExists(taskId);
        return fetchFailureRepository.findByTaskIdOrderByResolvedAscLastOccurredAtDesc(taskId)
                .stream()
                .map(FetchFailureDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FetchFailure> listPendingFailures(Long taskId) {
        assertTaskExists(taskId);
        return fetchFailureRepository.findByTaskIdAndResolvedFalseOrderByLastOccurredAtAsc(taskId);
    }

    @Transactional(readOnly = true)
    public FetchFailure getTaskFailure(Long taskId, Long failureId) {
        assertTaskExists(taskId);
        return fetchFailureRepository.findById(failureId)
                .filter(failure -> failure.getTask().getId().equals(taskId))
                .orElseThrow(() -> new IllegalArgumentException("失败记录不存在: " + failureId));
    }

    @Transactional(readOnly = true)
    public long countPendingFailures(Long taskId) {
        return fetchFailureRepository.countByTaskIdAndResolvedFalse(taskId);
    }

    @Transactional
    public void markRetryAttempted(Long failureId) {
        FetchFailure failure = fetchFailureRepository.findById(failureId)
                .orElseThrow(() -> new IllegalArgumentException("失败记录不存在: " + failureId));
        failure.setLastRetriedAt(LocalDateTime.now());
        fetchFailureRepository.save(failure);
    }

    @Transactional
    public void markResolved(Long failureId) {
        FetchFailure failure = fetchFailureRepository.findById(failureId)
                .orElseThrow(() -> new IllegalArgumentException("失败记录不存在: " + failureId));
        failure.setResolved(true);
        failure.setResolvedAt(LocalDateTime.now());
        fetchFailureRepository.save(failure);
    }

    @Transactional
    public void deleteTaskFailures(Long taskId) {
        fetchFailureRepository.deleteByTaskId(taskId);
    }

    private void recordFailure(Long taskId,
                               FetchFailure.FailureType failureType,
                               String failureKey,
                               int pageNumber,
                               String externalId,
                               String requestUrl,
                               String sourceUrl,
                               Exception e) {
        SearchTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        LocalDateTime now = LocalDateTime.now();
        FetchFailure failure = fetchFailureRepository.findByFailureKey(failureKey)
                .orElseGet(FetchFailure::new);

        failure.setTask(task);
        failure.setFailureType(failureType);
        failure.setFailureKey(failureKey);
        failure.setPageNumber(pageNumber);
        failure.setExternalId(blankToNull(externalId));
        failure.setRequestUrl(blankToNull(requestUrl));
        failure.setSourceUrl(blankToNull(sourceUrl));
        failure.setErrorType(e.getClass().getName());
        failure.setErrorMessage(e.getMessage());
        failure.setFailureCount(failure.getFailureCount() + 1);
        failure.setResolved(false);
        if (failure.getId() == null) {
            failure.setFirstOccurredAt(now);
        }
        failure.setLastOccurredAt(now);
        failure.setResolvedAt(null);

        fetchFailureRepository.save(failure);
    }

    private void assertTaskExists(Long taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
    }

    private String buildFailureKey(Long taskId,
                                   FetchFailure.FailureType failureType,
                                   int pageNumber,
                                   String externalId) {
        return taskId + ":" + failureType.name() + ":" + pageNumber + ":" + blankToNull(externalId);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
