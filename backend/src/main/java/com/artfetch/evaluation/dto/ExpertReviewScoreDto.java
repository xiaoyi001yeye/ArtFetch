package com.artfetch.evaluation.dto;

import com.artfetch.evaluation.entity.ExpertReviewScore;

public record ExpertReviewScoreDto(
        Long id,
        Long projectMetricId,
        Double score,
        String optionValue,
        String textValue,
        String comment
) {
    public static ExpertReviewScoreDto from(ExpertReviewScore score) {
        return new ExpertReviewScoreDto(
                score.getId(),
                score.getProjectMetricId(),
                score.getScore(),
                score.getOptionValue(),
                score.getTextValue(),
                score.getComment()
        );
    }
}
