package com.artfetch.evaluation.dto;

import com.artfetch.dto.ArtworkDto;

public record ArtworkPreviewDto(
        Long id,
        String title,
        String artist,
        String lotNumber,
        String medium,
        String valuation,
        String auctionHouse,
        String auctionDate,
        String imageUrl
) {
    public static ArtworkPreviewDto from(ArtworkDto artwork) {
        return new ArtworkPreviewDto(
                artwork.getId(),
                artwork.getTitle(),
                artwork.getArtist(),
                artwork.getLotNumber(),
                artwork.getMedium(),
                artwork.getValuation(),
                artwork.getAuctionHouse(),
                artwork.getAuctionDate(),
                artwork.getImageUrl()
        );
    }
}
