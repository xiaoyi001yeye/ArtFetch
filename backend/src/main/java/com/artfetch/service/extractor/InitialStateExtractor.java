package com.artfetch.service.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从页面内嵌的 window.__INITIAL_STATE__ 中提取结构化字段。
 * 雅昌详情页大部分真实数据都在这段 JSON 中，DOM 仅作为兜底来源。
 */
public class InitialStateExtractor implements FieldExtractor {

    private static final String STATE_PREFIX = "window.__INITIAL_STATE__=";
    private static final String STATE_END_MARKER = ";(function()";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern LEADING_CURRENCY_PATTERN =
            Pattern.compile("^(RMB|HKD|CNY|USD|EUR|JPY|GBP|人民币|港币)\\s+(.+)$");
    @Override
    public void extract(Document doc, ArtworkData data) {
        JsonNode pageData = extractPageData(doc);
        if (pageData == null || pageData.isMissingNode()) {
            return;
        }

        JsonNode detail = pageData.path("detail");

        data.lotNumber = firstNonBlank(
                text(detail, "tlNumber"),
                findItemValue(pageData.path("pc_extra_info"), "lot号"),
                data.lotNumber
        );
        data.artist = firstNonBlank(
                data.artist,
                extractArtist(detail.path("extraInfo"))
        );
        data.medium = firstNonBlank(
                findItemValue(detail.path("extraInfo"), "材质"),
                data.medium
        );
        data.format = firstNonBlank(
                findItemValue(detail.path("extraInfo"), "形制"),
                data.format
        );
        data.dimensions = firstNonBlank(
                findItemValue(detail.path("extraInfo"), "尺寸", "大小"),
                data.dimensions
        );
        data.valuation = firstNonBlank(
                readValuationValue(detail.path("extraInfo")),
                data.valuation
        );
        TransactionPriceInfo transactionPriceInfo = extractTransactionPrice(detail, pageData);
        data.transactionPrice = firstNonBlank(
                transactionPriceInfo.value(),
                data.transactionPrice
        );
        data.transactionPriceLoginRequired = transactionPriceInfo.loginRequired();
        data.transactionPriceMessage = firstNonBlank(
                transactionPriceInfo.message(),
                data.transactionPriceMessage
        );
        data.auctionHouse = firstNonBlank(
                findItemValue(detail.path("attribute"), "拍卖公司", "拍卖行"),
                findItemValue(pageData.path("pc_extra_info"), "拍卖公司"),
                data.auctionHouse
        );
        data.auctionName = firstNonBlank(
                findItemValue(detail.path("attribute"), "拍卖会", "拍卖名称"),
                findItemLabelByType(pageData.path("pc_extra_info"), "session_code"),
                data.auctionName
        );
        data.auctionSession = firstNonBlank(
                findItemValue(detail.path("attribute"), "拍卖专场", "专场"),
                findItemLabelByType(pageData.path("pc_extra_info"), "special_code"),
                data.auctionSession
        );
        data.auctionDate = firstNonBlank(
                findItemValue(detail.path("attribute"), "拍卖日期", "拍卖时间"),
                text(pageData.path("picAttribute"), "auctionDate"),
                findItemValue(pageData.path("pc_extra_info"), "拍卖时间"),
                data.auctionDate
        );
        data.auctionLocation = firstNonBlank(
                findItemValue(detail.path("attribute"), "拍卖地点", "拍卖地址"),
                firstNonBlank(
                        findItemLabelByType(pageData.path("pc_extra_info"), "special_city"),
                        findItemTextByType(pageData.path("pc_extra_info"), "special_city")
                ),
                data.auctionLocation
        );
        data.previewTime = firstNonBlank(
                findItemValue(detail.path("attribute"), "预展时间", "预展日期"),
                data.previewTime
        );
        data.previewLocation = firstNonBlank(
                findItemValue(detail.path("attribute"), "预展地点", "预展地址"),
                data.previewLocation
        );

        // 列表页标题通常更适合作为“拍品名称”，这里只在缺失时回填详情页标题。
        data.title = firstNonBlank(
                data.title,
                text(detail, "workName"),
                text(pageData, "show_title")
        );
        data.imageUrl = firstNonBlank(
                data.imageUrl,
                text(detail, "LogoUrl"),
                text(pageData, "coverPic")
        );
        data.originalImageUrl = firstNonBlank(
                data.originalImageUrl,
                extractPrimaryOriginalImageUrl(doc)
        );
        data.description = firstNonBlank(
                extractDescription(detail.path("extraInfo")),
                data.description
        );
        data.extraData = firstNonBlank(
                buildExtraData(pageData, detail),
                data.extraData
        );
    }

    public static String extractPrimaryOriginalImageUrl(Document doc) {
        JsonNode pageData = extractPageData(doc);
        if (pageData == null || pageData.isMissingNode()) {
            return null;
        }

        JsonNode detail = pageData.path("detail");
        return firstNonBlankStatic(
                firstText(detail.path("PicUrl")),
                unwrapThumbUrl(textStatic(detail, "bigPic")),
                firstText(detail.path("bigPicArray")),
                unwrapThumbUrl(textStatic(detail, "middlePic")),
                unwrapThumbUrl(textStatic(detail, "LogoUrl"))
        );
    }

    public static String unwrapThumbUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        int srcIdx = trimmed.indexOf("src=");
        if (srcIdx < 0) {
            return trimmed;
        }

        String value = trimmed.substring(srcIdx + 4);
        int amp = value.indexOf('&');
        if (amp >= 0) {
            value = value.substring(0, amp);
        }
        String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
        return decoded.isBlank() ? null : decoded;
    }

    public static JsonNode extractPageData(Document doc) {
        Element script = doc.selectFirst("script:containsData(window.__INITIAL_STATE__)");
        if (script == null) {
            return null;
        }

        String scriptData = script.data();
        int start = scriptData.indexOf(STATE_PREFIX);
        if (start == -1) {
            return null;
        }

        int jsonStart = start + STATE_PREFIX.length();
        int end = scriptData.indexOf(STATE_END_MARKER, jsonStart);
        if (end == -1) {
            end = scriptData.lastIndexOf("};");
            if (end == -1) {
                return null;
            }
            end += 1;
        }

        String jsonText = scriptData.substring(jsonStart, end).trim();
        if (jsonText.isBlank()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.readTree(jsonText).path("pageProDetail").path("data");
        } catch (Exception e) {
            return null;
        }
    }

    private String extractArtist(JsonNode extraInfo) {
        JsonNode artistItem = findByLabel(extraInfo, "作者", "艺术家");
        if (artistItem == null) {
            return null;
        }

        String direct = firstNonBlank(
                text(artistItem, "fullText"),
                text(artistItem, "text")
        );
        if (direct != null) {
            return cleanArtistName(direct);
        }

        JsonNode samples = artistItem.path("isSample");
        if (samples.isArray()) {
            for (JsonNode sample : samples) {
                String name = cleanArtistName(text(sample, "name"));
                if (name != null) {
                    return name;
                }
            }
        }
        return null;
    }

    private String extractDescription(JsonNode extraInfo) {
        JsonNode item = findByName(extraInfo, "description");
        if (item == null) {
            item = findByLabel(extraInfo, "拍品描述", "作品描述", "描述", "款识", "题识", "说明");
        }
        if (item == null) {
            return null;
        }
        return cleanDescription(firstNonBlank(
                text(item, "fullText"),
                text(item, "text"),
                text(item, "value"),
                text(item, "content")
        ));
    }

    private String buildExtraData(JsonNode pageData, JsonNode detail) {
        ObjectNode extra = OBJECT_MAPPER.createObjectNode();
        TransactionPriceInfo transactionPriceInfo = extractTransactionPrice(detail, pageData);

        putIfPresent(extra, "creationEra", findItemValue(detail.path("extraInfo"), "创作年代"));
        putIfPresent(extra, "transactionPrice", transactionPriceInfo.value());
        putIfPresent(extra, "categoryLevel1", text(detail, "classCodeOneName"));
        putIfPresent(extra, "categoryLevel2", text(detail, "classCodeTwoName"));
        putIfPresent(extra, "currency", findItemValue(pageData.path("pc_extra_info"), "币种"));

        if (extra.isEmpty()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(extra);
        } catch (Exception e) {
            return null;
        }
    }

    private void putIfPresent(ObjectNode node, String fieldName, String value) {
        if (value != null) {
            node.put(fieldName, value);
        }
    }

    private TransactionPriceInfo extractTransactionPrice(JsonNode detail, JsonNode pageData) {
        JsonNode detailItem = findByLabel(detail.path("extraInfo"), "成交价");
        JsonNode detailPrice = detail.path("price");
        JsonNode listItem = findByLabel(pageData.path("pc_extra_info"), "成交价");
        JsonNode picAttribute = pageData.path("picAttribute");

        String transactionPrice = firstNonBlank(
                readPriceValue(detailItem),
                readPriceValue(detailPrice),
                readPicAttributePriceValue(picAttribute),
                readPriceValue(listItem)
        );
        if (transactionPrice != null) {
            return new TransactionPriceInfo(transactionPrice, false, null);
        }

        if (isUnavailable(detailItem) || isUnavailable(detailPrice) || isUnavailable(listItem)
                || isUnavailableText(text(picAttribute, "resultPrice"))
                || isUnavailableText(text(picAttribute, "resultNoLoginText"))) {
            return new TransactionPriceInfo(null, false, "详情页未提供成交价");
        }

        if (isLoginRequired(detailItem) || isLoginRequired(detailPrice) || isLoginRequired(listItem)) {
            return new TransactionPriceInfo(null, true, "需要登录后才能查看成交价");
        }

        return new TransactionPriceInfo(null, false, "详情页未返回成交价字段");
    }

    private String readValuationValue(JsonNode extraInfo) {
        JsonNode item = findByLabel(extraInfo, "估价", "参考价", "起拍价");
        if (item == null) {
            return null;
        }
        return readPrimaryPriceValue(item, true);
    }

    private JsonNode findByLabel(JsonNode items, String... labels) {
        if (!items.isArray()) {
            return null;
        }

        for (JsonNode item : items) {
            String label = text(item, "label");
            if (label == null) {
                continue;
            }
            for (String candidate : labels) {
                if (candidate.equals(label)) {
                    return item;
                }
            }
        }
        return null;
    }

    private JsonNode findByName(JsonNode items, String name) {
        if (!items.isArray()) {
            return null;
        }

        for (JsonNode item : items) {
            if (name.equals(text(item, "name"))) {
                return item;
            }
        }
        return null;
    }

    private JsonNode findByType(JsonNode items, String type) {
        if (!items.isArray()) {
            return null;
        }

        for (JsonNode item : items) {
            if (type.equals(text(item, "type"))) {
                return item;
            }
        }
        return null;
    }

    private String findItemValue(JsonNode items, String... labels) {
        JsonNode item = findByLabel(items, labels);
        if (item == null) {
            return null;
        }
        return firstNonBlank(
                text(item, "fullText"),
                text(item, "text")
        );
    }

    private String findItemLabelByType(JsonNode items, String type) {
        JsonNode item = findByType(items, type);
        return item == null ? null : text(item, "label");
    }

    private String findItemTextByType(JsonNode items, String type) {
        JsonNode item = findByType(items, type);
        return item == null ? null : text(item, "text");
    }

    private String readPriceValue(JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return null;
        }

        String primary = firstNonBlank(
                normalizePriceText(text(item, "fullText")),
                normalizePriceText(text(item, "text"))
        );
        String primaryWithCurrency = appendPrimaryCurrency(primary, normalizePriceText(text(item, "smallText")));
        String secondaryWithCurrency = appendTrailingCurrency(
                normalizePriceText(text(item, "otherText")),
                normalizePriceText(text(item, "otherSmallText"))
        );
        return joinPriceLines(primaryWithCurrency, secondaryWithCurrency);
    }

    private String readPrimaryPriceValue(JsonNode item, boolean prefixCurrency) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return null;
        }
        String primary = firstNonBlank(
                normalizePriceText(text(item, "fullText")),
                normalizePriceText(text(item, "text"))
        );
        primary = firstLine(primary);
        String currency = normalizePriceText(text(item, "smallText"));
        return prefixCurrency
                ? prependCurrency(primary, currency)
                : primary;
    }

    private String readPicAttributePriceValue(JsonNode picAttribute) {
        if (picAttribute == null || picAttribute.isMissingNode() || picAttribute.isNull()) {
            return null;
        }
        return appendPrimaryCurrency(
                normalizePriceText(text(picAttribute, "resultPrice")),
                normalizePriceText(text(picAttribute, "resultCurrency"))
        );
    }

    private String appendPrimaryCurrency(String value, String currency) {
        String normalized = normalizePriceLines(value);
        if (normalized == null || currency == null || containsCurrency(normalized, currency)) {
            return normalized;
        }

        int lineBreak = normalized.indexOf('\n');
        if (lineBreak >= 0) {
            String firstLine = normalized.substring(0, lineBreak).trim();
            String rest = normalized.substring(lineBreak + 1).trim();
            String firstLineWithCurrency = appendTrailingCurrency(firstLine, currency);
            String restWithTrailingCurrency = moveLeadingCurrencyToEnd(rest);
            return rest.isBlank()
                    ? firstLineWithCurrency
                    : restWithTrailingCurrency + "\n" + firstLineWithCurrency;
        }

        int firstSpace = normalized.indexOf(' ');
        if (firstSpace > 0 && containsKnownCurrency(normalized)) {
            return normalized.substring(0, firstSpace) + " " + currency + normalized.substring(firstSpace);
        }
        return normalized + " " + currency;
    }

    private String prependCurrency(String value, String currency) {
        String normalized = normalizePriceLines(value);
        if (normalized == null || currency == null || containsCurrency(normalized, currency)) {
            return normalized;
        }
        return currency + " " + normalized;
    }

    private String firstLine(String value) {
        String normalized = normalizePriceLines(value);
        if (normalized == null) {
            return null;
        }
        int lineBreak = normalized.indexOf('\n');
        return lineBreak >= 0 ? normalized.substring(0, lineBreak).trim() : normalized;
    }

    private String appendTrailingCurrency(String value, String currency) {
        String normalized = normalizePriceLines(value);
        if (normalized == null || currency == null || containsCurrency(normalized, currency)) {
            return normalized;
        }
        return normalized + " " + currency;
    }

    private String moveLeadingCurrencyToEnd(String value) {
        String normalized = normalizePriceLines(value);
        if (normalized == null) {
            return null;
        }
        Matcher matcher = LEADING_CURRENCY_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return normalized;
        }
        return matcher.group(2).trim() + " " + matcher.group(1).trim();
    }

    private String joinPriceLines(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            String normalized = normalizePriceLines(value);
            if (normalized == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(normalized);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private String normalizePriceLines(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .trim();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean containsCurrency(String value, String currency) {
        return value != null && currency != null && value.contains(currency);
    }

    private boolean containsKnownCurrency(String value) {
        return value != null && (value.contains("RMB")
                || value.contains("HKD")
                || value.contains("CNY")
                || value.contains("USD")
                || value.contains("EUR")
                || value.contains("JPY")
                || value.contains("GBP")
                || value.contains("人民币")
                || value.contains("港币"));
    }

    private boolean isLoginRequired(JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return false;
        }
        boolean needMember = item.path("needMember").asInt(0) == 1;
        String loginText = text(item, "loginText");
        String maskedText = text(item, "nonMemberText");
        String price = readPriceValue(item);
        return price == null && (needMember || hasLoginHint(loginText) || isMaskedText(maskedText));
    }

    private boolean isUnavailable(JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return false;
        }
        return isUnavailableText(text(item, "text"))
                || isUnavailableText(text(item, "fullText"))
                || isUnavailableText(text(item, "nonMemberText"));
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }

        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }

        String value = child.asText();
        if (value == null) {
            return null;
        }

        value = value.trim();
        return value.isBlank() ? null : value;
    }

    private String cleanArtistName(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.replaceAll("\\s*[（(].*$", "").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String cleanDescription(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = Jsoup.parseBodyFragment(value.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n"))
                .text()
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String normalizePriceText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank() || isUnavailableText(trimmed) || isMaskedText(trimmed) || hasLoginHint(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private boolean isUnavailableText(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.isBlank()
                || "未提供".equals(trimmed)
                || "暂无".equals(trimmed)
                || "-".equals(trimmed)
                || "—".equals(trimmed);
    }

    private boolean hasLoginHint(String value) {
        return value != null && value.contains("登录");
    }

    private boolean isMaskedText(String value) {
        return value != null && value.contains("****");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String firstText(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return null;
        }
        String text = node.get(0).asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String textStatic(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }

        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }

        String value = child.asText();
        if (value == null) {
            return null;
        }

        value = value.trim();
        return value.isBlank() ? null : value;
    }

    private static String firstNonBlankStatic(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private record TransactionPriceInfo(String value, boolean loginRequired, String message) {
    }
}
