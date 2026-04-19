package com.artfetch.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "artworks", indexes = {
        @Index(name = "idx_artwork_task_id", columnList = "task_id"),
        @Index(name = "idx_artwork_external_id", columnList = "external_id")
})
public class Artwork {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private SearchTask task;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "lot_number")
    private String lotNumber;       // 拍品编号

    private String artist;          // 作者

    private String medium;          // 材质

    private String format;          // 形制

    private String dimensions;      // 尺寸

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "original_image_source_url", columnDefinition = "TEXT")
    private String originalImageSourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "original_image_status")
    private OriginalImageStatus originalImageStatus = OriginalImageStatus.MISSING;

    @Column(name = "original_image_path", columnDefinition = "TEXT")
    private String originalImagePath;

    @Column(name = "original_image_content_type")
    private String originalImageContentType;

    @Column(name = "original_image_size")
    private Long originalImageSize;

    @Column(name = "original_image_downloaded_at")
    private LocalDateTime originalImageDownloadedAt;

    @Column(name = "original_image_last_error", columnDefinition = "TEXT")
    private String originalImageLastError;

    @Column(name = "hd_image_source_url", columnDefinition = "TEXT")
    private String hdImageSourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "hd_image_status")
    private HdImageStatus hdImageStatus = HdImageStatus.MISSING;

    @Column(name = "hd_image_path", columnDefinition = "TEXT")
    private String hdImagePath;

    @Column(name = "hd_image_content_type")
    private String hdImageContentType;

    @Column(name = "hd_image_size")
    private Long hdImageSize;

    @Column(name = "hd_image_downloaded_at")
    private LocalDateTime hdImageDownloadedAt;

    @Column(name = "hd_image_last_error", columnDefinition = "TEXT")
    private String hdImageLastError;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(columnDefinition = "TEXT")
    private String valuation;       // 估价

    @Column(name = "transaction_price", columnDefinition = "TEXT")
    private String transactionPrice; // 成交价

    @Column(name = "transaction_price_note", columnDefinition = "TEXT")
    private String transactionPriceNote; // 未拿到成交价时的简短原因

    @Column(name = "auction_house")
    private String auctionHouse;    // 拍卖公司

    @Column(name = "auction_name")
    private String auctionName;     // 拍卖会

    @Column(name = "auction_session")
    private String auctionSession;  // 拍卖专场

    @Column(name = "auction_date")
    private String auctionDate;     // 拍卖日期

    @Column(name = "auction_location")
    private String auctionLocation; // 拍卖地点

    @Column(name = "preview_time")
    private String previewTime;     // 预展时间

    @Column(name = "preview_location")
    private String previewLocation; // 预展地点

    @Column(name = "extra_data", columnDefinition = "TEXT")
    private String extraData;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum OriginalImageStatus {
        MISSING,
        DOWNLOADED,
        FAILED
    }

    public enum HdImageStatus {
        MISSING,
        DOWNLOADED,
        FAILED
    }
}
