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
    private String artist;
    private String year;
    private String medium;
    private String dimensions;
    private String description;
    private String category;
    private String collection;
    private String imageUrl;
    private String sourceUrl;
    private String valuation;
    private String format;
    private String auctionName;
    private String auctionSession;
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
        dto.setArtist(artwork.getArtist());
        dto.setYear(artwork.getYear());
        dto.setMedium(artwork.getMedium());
        dto.setDimensions(artwork.getDimensions());
        dto.setDescription(artwork.getDescription());
        dto.setCategory(artwork.getCategory());
        dto.setCollection(artwork.getCollection());
        dto.setImageUrl(artwork.getImageUrl());
        dto.setSourceUrl(artwork.getSourceUrl());
        dto.setValuation(artwork.getValuation());
        dto.setFormat(artwork.getFormat());
        dto.setAuctionName(artwork.getAuctionName());
        dto.setAuctionSession(artwork.getAuctionSession());
        dto.setAuctionLocation(artwork.getAuctionLocation());
        dto.setPreviewTime(artwork.getPreviewTime());
        dto.setPreviewLocation(artwork.getPreviewLocation());
        dto.setCreatedAt(artwork.getCreatedAt());
        return dto;
    }
}
