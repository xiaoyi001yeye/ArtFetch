package com.artfetch.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
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

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(columnDefinition = "TEXT")
    private String valuation;       // 估价

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
}
