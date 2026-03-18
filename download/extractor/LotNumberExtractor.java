package extractor;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LotNumberExtractor implements FieldExtractor {

    private static final Pattern LOT_NUMBER_PATTERN =
            Pattern.compile("(?i)lot\\D*(\\d+)", Pattern.CASE_INSENSITIVE);

    private final String[] candidateLabels = {"LOT", "Lot", "拍品号", "拍品编号"};

    @Override
    public void extract(Document doc, ArtworkData data) {
        String lotNumber = null;

        for (String label : candidateLabels) {
            lotNumber = extractByLabel(doc, label);
            if (lotNumber != null) break;
        }

        if (lotNumber == null) {
            lotNumber = extractFromDocumentText(doc);
        }

        if (lotNumber != null) {
            lotNumber = lotNumber.replaceAll("^(?i)lot\\s*", "").trim();
            if (!lotNumber.isBlank()) {
                data.setLotNumber(lotNumber);
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
            String text = el.text().trim();
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
