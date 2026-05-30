package com.artfetch.evaluation.dto;

import com.artfetch.entity.Artwork;
import com.artfetch.evaluation.entity.ExpertReview;

import java.time.LocalDateTime;

public record ExpertArtworkListItemDto(
        Long artworkId,
        String title,
        String artist,
        String lotNumber,
        String reviewStatus,
        String rejectedReason,
        boolean previewImageAvailable,
        boolean originalImageAvailable,
        boolean hdImageAvailable,
        LocalDateTime updatedAt
) {
    public static ExpertArtworkListItemDto from(Artwork artwork, ExpertReview review) {
        ExpertArtworkDto detail = ExpertArtworkDto.from(artwork);
        return new ExpertArtworkListItemDto(
                artwork.getId(),
                artwork.getTitle(),
                artwork.getArtist(),
                artwork.getLotNumber(),
                review.getStatus().name(),
                review.getRejectedReason(),
                detail.previewImageAvailable(),
                detail.originalImageAvailable(),
                detail.hdImageAvailable(),
                review.getUpdatedAt()
        );
    }
}
