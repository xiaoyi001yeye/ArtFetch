package com.artfetch.service.extractor;

import org.jsoup.nodes.Document;

public class AuctionLocationExtractor extends BaseLabelExtractor {

    public AuctionLocationExtractor() {
        super("拍卖地点", "拍卖地址");
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        String value = extractFirstMatch(doc);
        if (value != null) {
            data.auctionLocation = value;
        }
    }
}
