package com.artfetch.service.extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * 拍品描述兜底提取器。
 * 优先使用详情页 JSON；这里处理没有 window.__INITIAL_STATE__ 或 JSON 缺描述时的 DOM 文本。
 */
public class DescriptionExtractor extends BaseLabelExtractor {

    private static final String[] SECTION_LABELS = {
            "拍品描述", "作品描述", "描述", "款识", "题识", "说明"
    };

    public DescriptionExtractor() {
        super(SECTION_LABELS);
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        if (data.description != null && !data.description.isBlank()) {
            return;
        }

        String value = extractSectionText(doc);
        if (value == null) {
            value = extractFirstMatch(doc);
        }
        if (value != null) {
            data.description = value;
        }
    }

    private String extractSectionText(Document doc) {
        for (String label : SECTION_LABELS) {
            Elements labelElements = doc.select("h1, h2, h3, h4, h5, h6, dt, th, strong, b, span, div");
            for (Element element : labelElements) {
                if (!label.equals(element.ownText().trim())) {
                    continue;
                }

                String siblingText = cleanText(nextSiblingText(element));
                if (siblingText != null) {
                    return siblingText;
                }

                String parentText = cleanText(removeLeadingLabel(element.parent() == null ? null : element.parent().text(), label));
                if (parentText != null) {
                    return parentText;
                }
            }
        }
        return null;
    }

    private String nextSiblingText(Element element) {
        Element sibling = element.nextElementSibling();
        while (sibling != null) {
            String text = sibling.text();
            if (text != null && !text.isBlank()) {
                return text;
            }
            sibling = sibling.nextElementSibling();
        }
        return null;
    }

    private String removeLeadingLabel(String text, String label) {
        if (text == null) {
            return null;
        }
        return text.replaceFirst("^\\s*" + java.util.regex.Pattern.quote(label) + "\\s*[：:]?\\s*", "");
    }

    private String cleanText(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = Jsoup.parseBodyFragment(text)
                .text()
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
