package com.artfetch.service.extractor;

import org.jsoup.nodes.Document;

public class PreviewLocationExtractor extends BaseLabelExtractor {

    public PreviewLocationExtractor() {
        super("预展地点", "预展地址");
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        String value = extractFirstMatch(doc);
        if (value != null) {
            data.previewLocation = value;
        }
    }
}
