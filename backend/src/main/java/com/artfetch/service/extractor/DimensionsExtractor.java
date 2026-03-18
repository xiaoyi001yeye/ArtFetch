package com.artfetch.service.extractor;

import org.jsoup.nodes.Document;

public class DimensionsExtractor extends BaseLabelExtractor {

    public DimensionsExtractor() {
        super("尺寸", "大小");
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        String value = extractFirstMatch(doc);
        if (value != null) {
            data.dimensions = value;
        }
    }
}
