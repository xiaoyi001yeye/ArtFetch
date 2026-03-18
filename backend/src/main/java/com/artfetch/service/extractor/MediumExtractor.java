package com.artfetch.service.extractor;

import org.jsoup.nodes.Document;

public class MediumExtractor extends BaseLabelExtractor {

    public MediumExtractor() {
        super("材质", "材质尺寸");
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        String value = extractFirstMatch(doc);
        if (value != null) {
            data.medium = value;
        }
    }
}
