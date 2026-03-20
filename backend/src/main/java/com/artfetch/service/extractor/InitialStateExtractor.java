package com.artfetch.service.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 从页面内嵌的 window.__INITIAL_STATE__ 中提取结构化字段。
 * 雅昌详情页大部分真实数据都在这段 JSON 中，DOM 仅作为兜底来源。
 */
public class InitialStateExtractor implements FieldExtractor {

    private static final String STATE_PREFIX = "window.__INITIAL_STATE__=";
    private static final String STATE_END_MARKER = ";(function()";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
                findItemValue(detail.path("extraInfo"), "估价", "参考价", "起拍价"),
                data.valuation
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
        data.description = firstNonBlank(
                extractDescription(detail.path("extraInfo")),
                data.description
        );
        data.extraData = firstNonBlank(
                buildExtraData(pageData, detail),
                data.extraData
        );
    }

    private JsonNode extractPageData(Document doc) {
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
            item = findByLabel(extraInfo, "拍品描述");
        }
        if (item == null) {
            return null;
        }
        return firstNonBlank(
                text(item, "fullText"),
                text(item, "text")
        );
    }

    private String buildExtraData(JsonNode pageData, JsonNode detail) {
        ObjectNode extra = OBJECT_MAPPER.createObjectNode();

        putIfPresent(extra, "creationEra", findItemValue(detail.path("extraInfo"), "创作年代"));
        putIfPresent(extra, "transactionPrice", findItemValue(detail.path("extraInfo"), "成交价"));
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
