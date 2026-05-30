package com.artfetch.repository;

import com.artfetch.entity.HdImageMigrationTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface HdImageMigrationTaskRepository extends JpaRepository<HdImageMigrationTask, Long> {
    Page<HdImageMigrationTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatusIn(Collection<HdImageMigrationTask.Status> statuses);

    List<HdImageMigrationTask> findByStatus(HdImageMigrationTask.Status status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update HdImageMigrationTask t
            set t.processedCount = t.processedCount + :processed,
                t.successCount = t.successCount + :success,
                t.skippedCount = t.skippedCount + :skipped,
                t.failedCount = t.failedCount + :failed,
                t.currentArtworkId = :currentArtworkId,
                t.updatedAt = :updatedAt
            where t.id = :taskId
            """)
    int incrementProgress(@Param("taskId") Long taskId,
                          @Param("currentArtworkId") Long currentArtworkId,
                          @Param("processed") int processed,
                          @Param("success") int success,
                          @Param("skipped") int skipped,
                          @Param("failed") int failed,
                          @Param("updatedAt") LocalDateTime updatedAt);
}
