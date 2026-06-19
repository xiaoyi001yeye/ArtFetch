package com.artfetch.evaluation.dto;

public record AutoEvaluationArtworkCandidateDto(
        Long artworkId,
        String title,
        String artist,
        String lotNumber,
        String imageUrl,
        String auctionDate,
        String imageSourceCandidate,
        long estimatedImageSize,
        boolean selected
) {
}
