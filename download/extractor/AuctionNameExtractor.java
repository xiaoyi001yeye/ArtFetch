package extractor;

import org.jsoup.nodes.Document;

public class AuctionNameExtractor extends BaseLabelExtractor {

    public AuctionNameExtractor() {
        super("拍卖会", "专场拍卖", "拍卖名称");
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        String value = extractFirstMatch(doc);
        if (value != null) {
            data.setAuctionName(value);
        }
    }
}
