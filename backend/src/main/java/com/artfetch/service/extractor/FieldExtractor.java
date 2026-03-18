package com.artfetch.service.extractor;

import org.jsoup.nodes.Document;

/**
 * 字段提取器接口 - 每个需要从详情页提取的字段都实现此接口
 */
public interface FieldExtractor {
    void extract(Document doc, ArtworkData data);

    default String getExtractorName() {
        return this.getClass().getSimpleName();
    }
}
