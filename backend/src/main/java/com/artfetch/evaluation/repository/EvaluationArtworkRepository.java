package com.artfetch.evaluation.repository;

import com.artfetch.evaluation.entity.EvaluationArtwork;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EvaluationArtworkRepository extends JpaRepository<EvaluationArtwork, Long> {
    List<EvaluationArtwork> findByEvaluationIdOrderByIdAsc(Long evaluationId);
    Page<EvaluationArtwork> findByEvaluationIdOrderByIdAsc(Long evaluationId, Pageable pageable);
    List<EvaluationArtwork> findByEvaluationIdAndArtworkIdInOrderByIdAsc(Long evaluationId, Collection<Long> artworkIds);
    Optional<EvaluationArtwork> findByEvaluationIdAndArtworkId(Long evaluationId, Long artworkId);
    void deleteByEvaluationId(Long evaluationId);

    @Query("""
            select ea from EvaluationArtwork ea
            join Artwork a on a.id = ea.artworkId
            where ea.evaluationId = :evaluationId
              and (
                :keyword is null
                or lower(a.title) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(a.artist, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(a.lotNumber, '')) like lower(concat('%', :keyword, '%'))
              )
            order by ea.id asc
            """)
    Page<EvaluationArtwork> searchByEvaluationId(@Param("evaluationId") Long evaluationId,
                                                 @Param("keyword") String keyword,
                                                 Pageable pageable);
}
