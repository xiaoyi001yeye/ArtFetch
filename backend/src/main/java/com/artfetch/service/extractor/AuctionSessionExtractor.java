package com.artfetch.service.extractor;

import org.jsoup.nodes.Document;

public class AuctionSessionExtractor extends BaseLabelExtractor {

    public AuctionSessionExtractor() {
        super("专场", "拍卖专场");
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        String value = extractFirstMatch(doc);
        if (value != null) {
            data.auctionSession = value;
        }
    }
}
