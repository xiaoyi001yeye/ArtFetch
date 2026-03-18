package com.artfetch.service.extractor;

import org.jsoup.nodes.Document;

public class PreviewTimeExtractor extends BaseLabelExtractor {

    public PreviewTimeExtractor() {
        super("预展时间", "预展日期");
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        String value = extractFirstMatch(doc);
        if (value != null) {
            data.previewTime = value;
        }
    }
}
