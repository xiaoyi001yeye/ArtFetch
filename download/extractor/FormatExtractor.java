package extractor;

import org.jsoup.nodes.Document;

public class FormatExtractor extends BaseLabelExtractor {

    public FormatExtractor() {
        super("形制");
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        String value = extractFirstMatch(doc);
        if (value != null) {
            data.setFormat(value);
        }
    }
}
