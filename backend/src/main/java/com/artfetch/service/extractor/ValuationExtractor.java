package com.artfetch.service.extractor;

import org.jsoup.nodes.Document;

public class ValuationExtractor extends BaseLabelExtractor {

    public ValuationExtractor() {
        super("估价", "参考价", "起拍价");
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        if (data.valuation != null && !data.valuation.isBlank()) {
            return;
        }
        String value = extractFirstMatch(doc);
        if (value != null) {
            data.valuation = value;
        }
    }
}
