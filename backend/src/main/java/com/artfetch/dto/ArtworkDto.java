package com.artfetch.dto;

import com.artfetch.entity.Artwork;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArtworkDto {

    private Long id;
    private Long taskId;
    private String taskName;
    private String externalId;
    private String title;
    private String lotNumber;
    private String artist;
    private String medium;
    private String format;
    private String dimensions;
    private String description;
    private String imageUrl;
    private String sourceUrl;
    private String valuation;
    private String auctionHouse;
    private String auctionName;
    private String auctionSession;
    private String auctionDate;
    private String auctionLocation;
    private String previewTime;
    private String previewLocation;
    private LocalDateTime createdAt;

    public static ArtworkDto from(Artwork artwork) {
        ArtworkDto dto = new ArtworkDto();
        dto.setId(artwork.getId());
        dto.setTaskId(artwork.getTask().getId());
        dto.setTaskName(artwork.getTask().getName());
        dto.setExternalId(artwork.getExternalId());
        dto.setTitle(artwork.getTitle());
        dto.setLotNumber(artwork.getLotNumber());
        dto.setArtist(artwork.getArtist());
        dto.setMedium(artwork.getMedium());
        dto.setFormat(artwork.getFormat());
        dto.setDimensions(artwork.getDimensions());
        dto.setDescription(artwork.getDescription());
        dto.setImageUrl(artwork.getImageUrl());
        dto.setSourceUrl(artwork.getSourceUrl());
        dto.setValuation(artwork.getValuation());
        dto.setAuctionHouse(artwork.getAuctionHouse());
        dto.setAuctionName(artwork.getAuctionName());
        dto.setAuctionSession(artwork.getAuctionSession());
        dto.setAuctionDate(artwork.getAuctionDate());
        dto.setAuctionLocation(artwork.getAuctionLocation());
        dto.setPreviewTime(artwork.getPreviewTime());
        dto.setPreviewLocation(artwork.getPreviewLocation());
        dto.setCreatedAt(artwork.getCreatedAt());
        return dto;
    }
}
