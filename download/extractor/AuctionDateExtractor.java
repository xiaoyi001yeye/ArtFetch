package extractor;

import org.jsoup.nodes.Document;

public class AuctionDateExtractor extends BaseLabelExtractor {

    public AuctionDateExtractor() {
        super("拍卖日期", "拍卖时间");
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        String value = extractFirstMatch(doc);
        if (value != null) {
            data.setAuctionDate(value);
        }
    }
}
