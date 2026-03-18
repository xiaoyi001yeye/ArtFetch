package extractor;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

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
}
