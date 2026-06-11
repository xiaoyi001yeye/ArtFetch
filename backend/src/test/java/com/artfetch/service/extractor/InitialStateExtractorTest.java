package com.artfetch.service.extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class InitialStateExtractorTest {

    private final InitialStateExtractor extractor = new InitialStateExtractor();

    @Test
    void extractsStructuredFieldsFromInitialStateJson() throws Exception {
        ArtworkData data = new ArtworkData();
        data.title = "瑞士风雪";
        data.artist = "张大千";
        data.imageUrl = "thumb-from-list";

        extractor.extract(loadDocument("art5242552056.html"), data);

        assertThat(data.lotNumber).isEqualTo("2056");
        assertThat(data.title).isEqualTo("瑞士风雪");
        assertThat(data.artist).isEqualTo("张大千");
        assertThat(data.medium).isEqualTo("设色纸本");
        assertThat(data.dimensions).isEqualTo("43.5×59cm");
        assertThat(data.format).isEqualTo("镜心");
        assertThat(data.valuation).isEqualTo("RMB 7,000,000-9,000,000");
        assertThat(data.transactionPrice).isEqualTo("8,165,000 RMB");
        assertThat(data.transactionPriceLoginRequired).isFalse();
        assertThat(data.auctionHouse).isEqualTo("北京保利");
        assertThat(data.auctionName).isEqualTo("保利拍卖二十周年春季艺术品拍卖会");
        assertThat(data.auctionSession).isEqualTo("中国书画夜场");
        assertThat(data.auctionDate).isEqualTo("2025-06-08");
        assertThat(data.auctionLocation).contains("北京国贸大酒店");
        assertThat(data.previewTime).isEqualTo("2025年6月5日-28日");
        assertThat(data.previewLocation).contains("北京市朝阳区建国门外大街1号");
        assertThat(data.description).contains("《瑞士风雪》");
        assertThat(data.extraData).contains("\"creationEra\":\"1965年作\"");
        assertThat(data.imageUrl).isEqualTo("thumb-from-list");
    }

    @Test
    void extractsAuthorFromSampleArrayWhenDirectTextIsMissing() throws Exception {
        ArtworkData data = new ArtworkData();

        extractor.extract(loadDocument("art31600061.html"), data);

        assertThat(data.lotNumber).isEqualTo("0061");
        assertThat(data.artist).isEqualTo("张大千");
        assertThat(data.medium).isEqualTo("设色纸本");
        assertThat(data.format).isEqualTo("成扇");
        assertThat(data.transactionPrice).isEqualTo("132,000 RMB");
        assertThat(data.auctionHouse).isEqualTo("上海信仁");
        assertThat(data.auctionDate).isEqualTo("2005-04-27 上午10:00");
        assertThat(data.description).contains("款识");
        assertThat(data.extraData).contains("\"creationEra\":\"1939年作\"");
    }

    @Test
    void keepsOriginalDisplayTextForDualCurrencyTransactionPrice() {
        ArtworkData data = new ArtworkData();
        Document doc = Jsoup.parse("""
                <html><body><script>
                window.__INITIAL_STATE__={"pageProDetail":{"data":{
                  "detail":{"extraInfo":[
                    {"type":"gujia","label":"估价","text":" 2,600,000-4,000,000 ","smallText":"HKD",
                     "loginText":"","fullText":"","nonMemberText":"","otherText":"","otherSmallText":"","needMember":0},
                    {"type":"price","label":"成交价","text":"3,302,000\\nRMB 2,894,203","smallText":"HKD",
                     "loginText":"请登录后查看","fullText":"","nonMemberText":"****","otherText":"","otherSmallText":"","needMember":1}
                  ]},
                  "pc_extra_info":[],
                  "picAttribute":{"resultPrice":"3,302,000\\nRMB 2,894,203","resultCurrency":"HKD"}
                }}};
                (function(){})();
                </script></body></html>
                """);

        extractor.extract(doc, data);

        assertThat(data.valuation).isEqualTo("HKD 2,600,000-4,000,000");
        assertThat(data.transactionPrice).isEqualTo("2,894,203 RMB\n3,302,000 HKD");
        assertThat(data.transactionPriceLoginRequired).isFalse();
    }

    @Test
    void keepsPrimaryUsdTransactionPriceBeforeRmbConversion() {
        ArtworkData data = new ArtworkData();
        Document doc = Jsoup.parse("""
                <html><body><script>
                window.__INITIAL_STATE__={"pageProDetail":{"data":{
                  "detail":{"extraInfo":[
                    {"type":"gujia","label":"估价","text":" 45,000-55,000 \\nRMB 361,575-441,925 ","smallText":"USD",
                     "loginText":"","fullText":"","nonMemberText":"","otherText":" 361,575-441,925 ","otherSmallText":"RMB","needMember":0},
                    {"type":"price","label":"成交价","text":"54,000\\nRMB 435,780","smallText":"USD",
                     "loginText":"请登录后查看","fullText":"","nonMemberText":"****","otherText":"","otherSmallText":"","needMember":1}
                  ]},
                  "pc_extra_info":[],
                  "picAttribute":{"resultPrice":"54,000\\nRMB 435,780","resultCurrency":"USD"}
                }}};
                (function(){})();
                </script></body></html>
                """);

        extractor.extract(doc, data);

        assertThat(data.valuation).isEqualTo("USD 45,000-55,000");
        assertThat(data.transactionPrice).isEqualTo("435,780 RMB\n54,000 USD");
        assertThat(data.transactionPriceLoginRequired).isFalse();
    }

    @Test
    void keepsVisualOrderForHkdTransactionPriceWithRmbConversion() {
        ArtworkData data = new ArtworkData();
        Document doc = Jsoup.parse("""
                <html><body><script>
                window.__INITIAL_STATE__={"pageProDetail":{"data":{
                  "detail":{"extraInfo":[
                    {"type":"gujia","label":"估价","text":" 1,800,000-2,800,000 \\nRMB 1,577,700-2,454,200 ","smallText":"HKD",
                     "loginText":"","fullText":"","nonMemberText":"","otherText":" 1,577,700-2,454,200 ","otherSmallText":"RMB","needMember":0},
                    {"type":"price","label":"成交价","text":"2,413,000\\nRMB 2,114,994","smallText":"HKD",
                     "loginText":"请登录后查看","fullText":"","nonMemberText":"****","otherText":"","otherSmallText":"","needMember":1}
                  ]},
                  "pc_extra_info":[],
                  "picAttribute":{"resultPrice":"2,413,000\\nRMB 2,114,994","resultCurrency":"HKD"}
                }}};
                (function(){})();
                </script></body></html>
                """);

        extractor.extract(doc, data);

        assertThat(data.valuation).isEqualTo("HKD 1,800,000-2,800,000");
        assertThat(data.transactionPrice).isEqualTo("2,114,994 RMB\n2,413,000 HKD");
        assertThat(data.transactionPriceLoginRequired).isFalse();
    }

    @Test
    void fieldExtractorChainDoesNotOverwriteInitialStateValuationWithFallbackText() {
        ArtworkData data = new ArtworkData();
        Document doc = Jsoup.parse("""
                <html><body>
                <div>估价：2,600,000-4,000,000 RMB 2,278,900-3,506,000</div>
                <script>
                window.__INITIAL_STATE__={"pageProDetail":{"data":{
                  "detail":{"extraInfo":[
                    {"type":"gujia","label":"估价","text":" 2,600,000-4,000,000 ","smallText":"HKD",
                     "loginText":"","fullText":"","nonMemberText":"","otherText":"","otherSmallText":"","needMember":0}
                  ]},
                  "pc_extra_info":[]
                }}};
                (function(){})();
                </script></body></html>
                """);

        new FieldExtractorChain().extractAll(doc, data);

        assertThat(data.valuation).isEqualTo("HKD 2,600,000-4,000,000");
    }

    private Document loadDocument(String fileName) throws IOException {
        Path file = Path.of("..", "download", fileName);
        assumeTrue(Files.exists(file), "sample HTML is stored outside this repository: " + file);
        return Jsoup.parse(Files.readString(file));
    }
}
