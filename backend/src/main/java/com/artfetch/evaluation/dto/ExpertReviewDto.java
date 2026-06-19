package com.artfetch.evaluation.dto;

import com.artfetch.evaluation.entity.ExpertReview;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ExpertReviewDto(
        Long id,
        Long evaluationId,
        Long artworkId,
        Long expertId,
        String expertName,
        String finalEstimate,
        BigDecimal finalEstimateAmount,
        String finalEstimateCurrency,
        String comment,
        String status,
        String rejectedReason,
        LocalDateTime rejectedAt,
        LocalDateTime resubmittedAt,
        LocalDateTime submittedAt,
        List<ExpertReviewScoreDto> scores
) {
    public static ExpertReviewDto from(ExpertReview review, List<ExpertReviewScoreDto> scores) {
        return new ExpertReviewDto(
                review.getId(),
                review.getEvaluationId(),
                review.getArtworkId(),
                review.getExpertId(),
                review.getExpertName(),
                review.getFinalEstimate(),
                review.getFinalEstimateAmount(),
                review.getFinalEstimateCurrency(),
                review.getComment(),
                review.getStatus().name(),
                review.getRejectedReason(),
                review.getRejectedAt(),
                review.getResubmittedAt(),
                review.getSubmittedAt(),
                scores
        );
    }
}
