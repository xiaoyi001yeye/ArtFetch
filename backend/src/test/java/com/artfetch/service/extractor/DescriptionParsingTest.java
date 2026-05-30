package com.artfetch.service.extractor;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptionParsingTest {

    @Test
    void extractsDescriptionFromInitialStateJson() {
        String html = """
                <html><body>
                <script>
                window.__INITIAL_STATE__={"pageProDetail":{"data":{"detail":{"extraInfo":[
                  {"name":"description","fullText":"<p>款识：山居图。</p><p>钤印：某某。</p>"}
                ]}}}};(function(){})();
                </script>
                </body></html>
                """;
        ArtworkData data = new ArtworkData();

        new InitialStateExtractor().extract(Jsoup.parse(html), data);

        assertThat(data.description).contains("款识：山居图。");
        assertThat(data.description).contains("钤印：某某。");
    }

    @Test
    void extractsDescriptionFromDomFallback() {
        String html = """
                <html><body>
                  <section>
                    <h3>拍品描述</h3>
                    <div>款识：松下高士。<br>出版：《某某画集》。</div>
                  </section>
                </body></html>
                """;
        ArtworkData data = new ArtworkData();

        new DescriptionExtractor().extract(Jsoup.parse(html), data);

        assertThat(data.description).contains("款识：松下高士。");
        assertThat(data.description).contains("出版：《某某画集》。");
    }
}
