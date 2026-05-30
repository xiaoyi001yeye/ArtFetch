package com.artfetch.repository;

import com.artfetch.entity.HdImageMigrationItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface HdImageMigrationItemRepository extends JpaRepository<HdImageMigrationItem, Long> {
    Page<HdImageMigrationItem> findByMigrationTaskIdOrderByIdAsc(Long migrationTaskId, Pageable pageable);

    Page<HdImageMigrationItem> findByMigrationTaskIdAndStatusOrderByIdAsc(Long migrationTaskId,
                                                                          HdImageMigrationItem.Status status,
                                                                          Pageable pageable);

    List<HdImageMigrationItem> findByMigrationTaskIdAndStatusInOrderByIdAsc(Long migrationTaskId,
                                                                            Collection<HdImageMigrationItem.Status> statuses);

    long countByMigrationTaskId(Long migrationTaskId);

    @Query("select i from HdImageMigrationItem i where i.migrationTaskId = :taskId and i.status in :statuses order by i.id asc")
    List<HdImageMigrationItem> findRunnableItems(@Param("taskId") Long taskId,
                                                 @Param("statuses") Collection<HdImageMigrationItem.Status> statuses);
}
