package com.artfetch.service;

import com.artfetch.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtronAuthService {

    private static final String LOGIN_PAGE_URL =
            "https://passport.artron.net/login?appId=1&redirect=https%3A%2F%2Fauction.artron.net%2F";
    private static final String LOGIN_SUBMIT_URL = "https://passport.artron.net/login/doing/";
    private static final String LOGIN_REDIRECT = "https://auction.artron.net/";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("name=\"_token\" value=\"([^\"]+)\"");

    private final AppProperties appProperties;

    private final Object loginLock = new Object();
    private volatile String cachedCookieHeader;
    private volatile Instant cachedAt;

    public String resolveCookieHeader() {
        String configuredCookie = trimToNull(appProperties.getAuth().getArtronCookie());
        if (configuredCookie != null) {
            return configuredCookie;
        }
        if (!hasCredentials()) {
            return null;
        }

        Instant now = Instant.now();
        if (cachedCookieHeader != null && cachedAt != null && cachedAt.plus(Duration.ofMinutes(90)).isAfter(now)) {
            return cachedCookieHeader;
        }

        synchronized (loginLock) {
            now = Instant.now();
            if (cachedCookieHeader != null && cachedAt != null && cachedAt.plus(Duration.ofMinutes(90)).isAfter(now)) {
                return cachedCookieHeader;
            }
            cachedCookieHeader = loginAndBuildCookieHeader();
            cachedAt = Instant.now();
            return cachedCookieHeader;
        }
    }

    public boolean hasAnyAuthMaterial() {
        return trimToNull(appProperties.getAuth().getArtronCookie()) != null || hasCredentials();
    }

    private boolean hasCredentials() {
        return trimToNull(appProperties.getAuth().getArtronAccount()) != null
                && trimToNull(appProperties.getAuth().getArtronPassword()) != null;
    }

    private String loginAndBuildCookieHeader() {
        String account = appProperties.getAuth().getArtronAccount().trim();
        String password = appProperties.getAuth().getArtronPassword().trim();

        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .cookieHandler(cookieManager)
                    .build();

            HttpRequest loginPageRequest = HttpRequest.newBuilder(URI.create(LOGIN_PAGE_URL))
                    .header("User-Agent", userAgent())
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> loginPageResponse = client.send(loginPageRequest, HttpResponse.BodyHandlers.ofString());
            String token = extractToken(loginPageResponse.body());

            Map<String, String> form = new LinkedHashMap<>();
            form.put("_token", token);
            form.put("appId", "1");
            form.put("redirect", LOGIN_REDIRECT);
            form.put("account", account);
            form.put("passwd", password);

            HttpRequest loginRequest = HttpRequest.newBuilder(URI.create(LOGIN_SUBMIT_URL))
                    .header("User-Agent", userAgent())
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(toFormData(form)))
                    .build();
            HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());

            String cookieHeader = buildCookieHeader(cookieManager.getCookieStore().getCookies());
            if (cookieHeader == null || !cookieHeader.contains("_at_pt_0_")) {
                throw new IllegalStateException("未拿到有效的雅昌登录 Cookie");
            }

            log.info("雅昌登录态已刷新: account={}, cookies={}",
                    maskAccount(account),
                    cookieManager.getCookieStore().getCookies().stream()
                            .map(HttpCookie::getName)
                            .distinct()
                            .toList());
            return cookieHeader;
        } catch (Exception e) {
            throw new IllegalStateException("雅昌自动登录失败: " + e.getMessage(), e);
        }
    }

    private String buildCookieHeader(List<HttpCookie> cookies) {
        String cookieHeader = cookies.stream()
                .filter(cookie -> cookie.getName() != null && !cookie.getName().isBlank())
                .filter(cookie -> cookie.hasExpired() == false)
                .filter(cookie -> {
                    String domain = cookie.getDomain();
                    return domain == null || domain.isBlank() || domain.contains("artron.net");
                })
                .collect(Collectors.toMap(
                        HttpCookie::getName,
                        HttpCookie::getValue,
                        (left, right) -> right,
                        LinkedHashMap::new
                ))
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
        return trimToNull(cookieHeader);
    }

    private String extractToken(String html) {
        Matcher matcher = TOKEN_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("登录页未返回 _token");
        }
        return matcher.group(1);
    }

    private String toFormData(Map<String, String> data) {
        return data.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String maskAccount(String account) {
        if (account.length() <= 4) {
            return "****";
        }
        return account.substring(0, 3) + "****" + account.substring(account.length() - 2);
    }

    private String userAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "Chrome/120.0.0.0 Safari/537.36";
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
