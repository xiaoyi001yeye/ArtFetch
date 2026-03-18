package com.artfetch.service.extractor;

import org.jsoup.nodes.Document;

public class AuctionHouseExtractor extends BaseLabelExtractor {

    public AuctionHouseExtractor() {
        super("拍卖公司", "拍卖行");
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        String value = extractFirstMatch(doc);
        if (value != null) {
            data.auctionHouse = value;
        }
    }
}
