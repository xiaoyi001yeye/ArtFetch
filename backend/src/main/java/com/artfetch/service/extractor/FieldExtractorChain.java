package com.artfetch.service.extractor;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;

import java.util.List;

/**
 * 字段提取器执行链 - 按顺序运行所有提取器，单个提取器异常不影响其他字段
 */
@Slf4j
public class FieldExtractorChain {

    private final List<FieldExtractor> extractors;

    public FieldExtractorChain() {
        this.extractors = List.of(
            new LotNumberExtractor(),
            new ArtistExtractor(),
            new MediumExtractor(),
            new FormatExtractor(),
            new DimensionsExtractor(),
            new ValuationExtractor(),
            new AuctionHouseExtractor(),
            new AuctionNameExtractor(),
            new AuctionSessionExtractor(),
            new AuctionDateExtractor(),
            new AuctionLocationExtractor(),
            new PreviewTimeExtractor(),
            new PreviewLocationExtractor()
        );
    }

    public void extractAll(Document doc, ArtworkData data) {
        for (FieldExtractor extractor : extractors) {
            try {
                extractor.extract(doc, data);
            } catch (Exception e) {
                log.warn("Extractor {} failed: {}", extractor.getExtractorName(), e.getMessage());
            }
        }
    }
}
