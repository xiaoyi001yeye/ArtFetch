package com.artfetch.evaluation.dto;

import com.artfetch.entity.Artwork;

public record ExpertArtworkDto(
        Long id,
        String title,
        String artist,
        String lotNumber,
        String medium,
        String format,
        String dimensions,
        String description,
        String valuation,
        String auctionHouse,
        String auctionName,
        String auctionSession,
        String auctionDate,
        String auctionLocation,
        String previewTime,
        String previewLocation,
        boolean previewImageAvailable,
        boolean originalImageAvailable,
        boolean hdImageAvailable
) {
    public static ExpertArtworkDto from(Artwork artwork) {
        return new ExpertArtworkDto(
                artwork.getId(),
                artwork.getTitle(),
                artwork.getArtist(),
                artwork.getLotNumber(),
                artwork.getMedium(),
                artwork.getFormat(),
                artwork.getDimensions(),
                artwork.getDescription(),
                artwork.getValuation(),
                artwork.getAuctionHouse(),
                artwork.getAuctionName(),
                artwork.getAuctionSession(),
                artwork.getAuctionDate(),
                artwork.getAuctionLocation(),
                artwork.getPreviewTime(),
                artwork.getPreviewLocation(),
                hasText(artwork.getImageUrl()),
                hasText(artwork.getOriginalImagePath()),
                hasText(artwork.getHdImagePath()) || hasText(artwork.getHdImageObjectKey())
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
