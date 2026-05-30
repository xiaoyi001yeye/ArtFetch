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
    private String originalImageSourceUrl;
    private String originalImageStatus;
    private boolean originalImageAvailable;
    private String hdImageSourceUrl;
    private String hdImageStatus;
    private boolean hdImageAvailable;
    private String hdImageLastError;
    private String hdImageStorageType;
    private String hdImageMigrationStatus;
    private String hdImageMigrationLastError;
    private String sourceUrl;
    private String valuation;
    private String transactionPrice;
    private String transactionPriceNote;
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
        dto.setOriginalImageSourceUrl(artwork.getOriginalImageSourceUrl());
        dto.setOriginalImageStatus(artwork.getOriginalImageStatus() == null
                ? Artwork.OriginalImageStatus.MISSING.name()
                : artwork.getOriginalImageStatus().name());
        dto.setOriginalImageAvailable(artwork.getOriginalImagePath() != null && !artwork.getOriginalImagePath().isBlank());
        dto.setHdImageSourceUrl(artwork.getHdImageSourceUrl());
        dto.setHdImageStatus(artwork.getHdImageStatus() == null
                ? Artwork.HdImageStatus.MISSING.name()
                : artwork.getHdImageStatus().name());
        dto.setHdImageAvailable((artwork.getHdImagePath() != null && !artwork.getHdImagePath().isBlank())
                || (artwork.getHdImageObjectKey() != null && !artwork.getHdImageObjectKey().isBlank()));
        dto.setHdImageLastError(artwork.getHdImageLastError());
        dto.setHdImageStorageType(artwork.getHdImageStorageType() == null
                ? Artwork.HdImageStorageType.LOCAL.name()
                : artwork.getHdImageStorageType().name());
        dto.setHdImageMigrationStatus(artwork.getHdImageMigrationStatus() == null
                ? Artwork.HdImageMigrationStatus.NOT_MIGRATED.name()
                : artwork.getHdImageMigrationStatus().name());
        dto.setHdImageMigrationLastError(artwork.getHdImageMigrationLastError());
        dto.setSourceUrl(artwork.getSourceUrl());
        dto.setValuation(artwork.getValuation());
        dto.setTransactionPrice(artwork.getTransactionPrice());
        dto.setTransactionPriceNote(artwork.getTransactionPriceNote());
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
