package com.artfetch.evaluation.repository;

import com.artfetch.evaluation.entity.ExpertReviewScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ExpertReviewScoreRepository extends JpaRepository<ExpertReviewScore, Long> {
    List<ExpertReviewScore> findByReviewIdIn(Collection<Long> reviewIds);
    List<ExpertReviewScore> findByReviewId(Long reviewId);
    void deleteByReviewId(Long reviewId);
    void deleteByReviewIdIn(Collection<Long> reviewIds);
}
