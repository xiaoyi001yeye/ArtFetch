package com.artfetch.repository;

import com.artfetch.entity.SearchTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchTaskRepository extends JpaRepository<SearchTask, Long> {

    Page<SearchTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<SearchTask> findByStatus(SearchTask.TaskStatus status);

    List<SearchTask> findByParentTaskIdOrderByIdAsc(Long parentTaskId);
}
