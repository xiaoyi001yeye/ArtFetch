package com.artfetch.repository;

import com.artfetch.entity.FetchFailure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FetchFailureRepository extends JpaRepository<FetchFailure, Long> {

    Optional<FetchFailure> findByFailureKey(String failureKey);

    List<FetchFailure> findByTaskIdOrderByResolvedAscLastOccurredAtDesc(Long taskId);

    List<FetchFailure> findByTaskIdAndResolvedFalseOrderByLastOccurredAtAsc(Long taskId);

    long countByTaskIdAndResolvedFalse(Long taskId);

    void deleteByTaskId(Long taskId);
}
