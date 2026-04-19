package com.artfetch.service;

import com.artfetch.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArtronRequestSupport {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "Chrome/120.0.0.0 Safari/537.36";

    private final AppProperties appProperties;
    private final ArtronAuthService artronAuthService;

    public Connection configure(Connection connection, String referer, int timeoutMs) {
        connection.userAgent(USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .timeout(timeoutMs);
        if (referer != null && !referer.isBlank()) {
            connection.header("Referer", referer);
        }
        String cookie = cookieHeader();
        if (cookie != null) {
            connection.header("Cookie", cookie);
        }
        return connection;
    }

    public boolean hasAuthCookie() {
        return cookieHeader() != null;
    }

    public String resolveCookieHeader() {
        return cookieHeader();
    }

    private String cookieHeader() {
        return artronAuthService.resolveCookieHeader();
    }
}
