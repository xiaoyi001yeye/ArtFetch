package com.artfetch.service;

import com.artfetch.config.AppProperties;
import com.artfetch.dto.ArtworkDto;
import com.artfetch.entity.Artwork;
import com.artfetch.entity.SearchTask;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.repository.SearchTaskRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionPriceServiceTest {

    private final ArtworkRepository artworkRepository = mock(ArtworkRepository.class);
    private final SearchTaskRepository taskRepository = mock(SearchTaskRepository.class);
    private final AppProperties appProperties = new AppProperties();
    private final ArtronRequestSupport artronRequestSupport =
            new ArtronRequestSupport(appProperties, new ArtronAuthService(appProperties));
    private final TransactionPriceService service = new TransactionPriceService(
            artworkRepository,
            taskRepository,
            appProperties,
            artronRequestSupport
    );

    @Test
    void supplementSingleArtworkAlsoRefreshesValuation() throws Exception {
        String html = """
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
                </script></body></html>
                """;

        HttpServer server = startServer(html);
        try {
            Artwork artwork = artwork("http://127.0.0.1:" + server.getAddress().getPort() + "/detail");
            artwork.setValuation("2,600,000-4,000,000 RMB 2,278,900-3,506,000");
            artwork.setTransactionPrice("3,302,000 RMB 2,894,203");

            when(artworkRepository.findById(1L)).thenReturn(Optional.of(artwork));

            ArtworkDto updated = service.supplementSingleArtwork(1L);

            assertThat(updated.getValuation()).isEqualTo("HKD 2,600,000-4,000,000");
            assertThat(updated.getTransactionPrice()).isEqualTo("2,894,203 RMB\n3,302,000 HKD");
            assertThat(updated.getTransactionPriceNote()).isNull();
            assertThat(updated.getTransactionPriceStatus()).isEqualTo("HAS_PRICE");
            assertThat(artwork.getTransactionPriceStatus()).isEqualTo(Artwork.TransactionPriceStatus.HAS_PRICE);
            verify(artworkRepository).save(artwork);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void supplementSingleArtworkMarksLoginRequiredWhenPriceIsMasked() throws Exception {
        String html = """
                <html><body><script>
                window.__INITIAL_STATE__={"pageProDetail":{"data":{
                  "detail":{"extraInfo":[
                    {"type":"price","label":"成交价","text":"","smallText":"",
                     "loginText":"请登录后查看","fullText":"","nonMemberText":"****","needMember":1}
                  ]},
                  "pc_extra_info":[],
                  "picAttribute":{}
                }}};
                </script></body></html>
                """;

        HttpServer server = startServer(html);
        try {
            Artwork artwork = artwork("http://127.0.0.1:" + server.getAddress().getPort() + "/detail");
            when(artworkRepository.findById(1L)).thenReturn(Optional.of(artwork));

            ArtworkDto updated = service.supplementSingleArtwork(1L);

            assertThat(updated.getTransactionPrice()).isNull();
            assertThat(updated.getTransactionPriceNote()).isEqualTo("需要登录");
            assertThat(updated.getTransactionPriceStatus()).isEqualTo("LOGIN_REQUIRED");
            assertThat(artwork.getTransactionPriceStatus()).isEqualTo(Artwork.TransactionPriceStatus.LOGIN_REQUIRED);
            verify(artworkRepository).save(artwork);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void supplementSingleArtworkMarksMissingWhenDetailHasNoPriceField() throws Exception {
        String html = """
                <html><body><script>
                window.__INITIAL_STATE__={"pageProDetail":{"data":{
                  "detail":{"extraInfo":[]},
                  "pc_extra_info":[],
                  "picAttribute":{}
                }}};
                </script></body></html>
                """;

        HttpServer server = startServer(html);
        try {
            Artwork artwork = artwork("http://127.0.0.1:" + server.getAddress().getPort() + "/detail");
            when(artworkRepository.findById(1L)).thenReturn(Optional.of(artwork));

            ArtworkDto updated = service.supplementSingleArtwork(1L);

            assertThat(updated.getTransactionPrice()).isNull();
            assertThat(updated.getTransactionPriceNote()).isEqualTo("页面未提供");
            assertThat(updated.getTransactionPriceStatus()).isEqualTo("MISSING");
            assertThat(artwork.getTransactionPriceStatus()).isEqualTo(Artwork.TransactionPriceStatus.MISSING);
            verify(artworkRepository).save(artwork);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void supplementSingleArtworkMarksFailedWhenSourceUrlIsMissing() {
        Artwork artwork = artwork(null);
        when(artworkRepository.findById(1L)).thenReturn(Optional.of(artwork));

        ArtworkDto updated = service.supplementSingleArtwork(1L);

        assertThat(updated.getTransactionPrice()).isNull();
        assertThat(updated.getTransactionPriceNote()).isEqualTo("缺少详情页地址");
        assertThat(updated.getTransactionPriceStatus()).isEqualTo("FAILED");
        assertThat(artwork.getTransactionPriceStatus()).isEqualTo(Artwork.TransactionPriceStatus.FAILED);
        verify(artworkRepository).save(artwork);
    }

    private HttpServer startServer(String html) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] response = html.getBytes(StandardCharsets.UTF_8);
        server.createContext("/detail", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        return server;
    }

    private Artwork artwork(String sourceUrl) {
        SearchTask task = new SearchTask();
        task.setId(10L);
        task.setName("test task");

        Artwork artwork = new Artwork();
        artwork.setId(1L);
        artwork.setTask(task);
        artwork.setTitle("test artwork");
        artwork.setExternalId("external-1");
        artwork.setSourceUrl(sourceUrl);
        return artwork;
    }
}
