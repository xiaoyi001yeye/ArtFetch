package com.artfetch.repository;

import com.artfetch.entity.Artwork;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ArtworkRepository extends JpaRepository<Artwork, Long>,
        JpaSpecificationExecutor<Artwork> {

    Optional<Artwork> findByTaskIdAndExternalId(Long taskId, String externalId);

    List<Artwork> findAllByTaskIdAndExternalIdIn(Long taskId, Collection<String> externalIds);

    boolean existsByTaskIdAndExternalId(Long taskId, String externalId);

    Optional<Artwork> findByExternalId(String externalId);

    long countByTaskId(Long taskId);

    Page<Artwork> findByTaskIdOrderByIdAsc(Long taskId, Pageable pageable);

    @Query("""
            select a.id from Artwork a
            where a.task.id = :taskId
              and (
                a.hdImageStatus is null
                or a.hdImageStatus <> :downloadedStatus
                or a.hdImagePath is null
                or a.hdImagePath = ''
              )
            order by a.id asc
            """)
    List<Long> findPendingHdImageIdsByTaskIdOrderByIdAsc(@Param("taskId") Long taskId,
                                                         @Param("downloadedStatus") Artwork.HdImageStatus downloadedStatus);

    @Query("""
            select count(a) from Artwork a
            where a.task.id = :taskId and (a.transactionPrice is null or a.transactionPrice = '')
            """)
    long countMissingTransactionPriceByTaskId(@Param("taskId") Long taskId);

    @Query("""
            select a from Artwork a
            where a.task.id = :taskId and (a.transactionPrice is null or a.transactionPrice = '')
            order by a.id asc
            """)
    Page<Artwork> findMissingTransactionPriceByTaskIdOrderByIdAsc(@Param("taskId") Long taskId, Pageable pageable);

    @Query("""
            select a.id from Artwork a
            where a.task.id = :taskId and (a.transactionPrice is null or a.transactionPrice = '')
            order by a.id asc
            """)
    List<Long> findMissingTransactionPriceIdsByTaskIdOrderByIdAsc(@Param("taskId") Long taskId);

    List<Artwork> findByIdInOrderByIdAsc(Collection<Long> ids);
}
