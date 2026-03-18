package com.artfetch.service.extractor;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * 基于"标签→值"模式的提取器抽象基类
 * 依次尝试 th/td、dt/dd、li/p/div 中的 "label：value" 文本
 */
public abstract class BaseLabelExtractor implements FieldExtractor {

    protected final String[] candidateLabels;

    public BaseLabelExtractor(String... candidateLabels) {
        this.candidateLabels = candidateLabels;
    }

    protected String extractFirstMatch(Document doc) {
        for (String label : candidateLabels) {
            String value = extractByLabel(doc, label);
            if (value != null) return value;
        }
        return null;
    }

    protected String extractByLabel(Document doc, String label) {
        // th → td
        Element th = doc.selectFirst("th:containsOwn(" + label + ")");
        if (th != null) {
            Element td = th.nextElementSibling();
            if (td != null && !td.text().isBlank()) return td.text().trim();
        }
        // dt → dd
        Element dt = doc.selectFirst("dt:containsOwn(" + label + ")");
        if (dt != null) {
            Element dd = dt.nextElementSibling();
            if (dd != null && !dd.text().isBlank()) return dd.text().trim();
        }
        // li/p/div 中的 "label：value" 纯文本
        Elements containers = doc.select("li, p, div");
        for (Element el : containers) {
            String text = el.ownText();
            if (text.contains(label + "：") || text.contains(label + ":")) {
                String val = text.replaceFirst(".*" + label + "[：:]\\s*", "").trim();
                if (!val.isBlank()) return val;
            }
        }
        return null;
    }
}
