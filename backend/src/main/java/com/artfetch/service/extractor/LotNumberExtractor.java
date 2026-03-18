package com.artfetch.service.extractor;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 拍品编号提取器
 * 策略1：按 dt/th 标签"LOT"/"拍品号"等提取
 * 策略2：在整个文档中搜索 "LOT XXX" 模式
 */
public class LotNumberExtractor implements FieldExtractor {

    private static final Pattern LOT_NUMBER_PATTERN =
            Pattern.compile("(?i)lot\\D*(\\d+)");

    private final String[] candidateLabels = {"LOT", "Lot", "拍品号", "拍品编号"};

    @Override
    public void extract(Document doc, ArtworkData data) {
        String lotNumber = null;

        // 策略 1：按标签文本提取 dt/th 结构
        for (String label : candidateLabels) {
            lotNumber = extractByLabel(doc, label);
            if (lotNumber != null) break;
        }

        // 策略 2：全文搜索 LOT XXX 模式（处理非结构化布局）
        if (lotNumber == null) {
            lotNumber = extractFromDocumentText(doc);
        }

        if (lotNumber != null) {
            lotNumber = lotNumber.replaceAll("^(?i)lot\\s*", "").trim();
            if (!lotNumber.isBlank()) {
                data.lotNumber = lotNumber;
            }
        }
    }

    private String extractByLabel(Document doc, String label) {
        Element th = doc.selectFirst("th:containsOwn(" + label + ")");
        if (th != null) {
            Element td = th.nextElementSibling();
            if (td != null && !td.text().isBlank()) return td.text().trim();
        }
        Element dt = doc.selectFirst("dt:containsOwn(" + label + ")");
        if (dt != null) {
            Element dd = dt.nextElementSibling();
            if (dd != null && !dd.text().isBlank()) return dd.text().trim();
        }
        return null;
    }

    private String extractFromDocumentText(Document doc) {
        Elements elements = doc.select("*:containsOwn(LOT), *:containsOwn(Lot)");
        for (Element el : elements) {
            // 跳过子元素很多的容器（标题/body 等），只取叶子附近的节点
            if (el.children().size() > 5) continue;
            String text = el.ownText().trim();
            if (text.isBlank()) text = el.text().trim();
            Matcher m = LOT_NUMBER_PATTERN.matcher(text);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    @Override
    public String getExtractorName() {
        return "LotNumberExtractor";
    }
}
