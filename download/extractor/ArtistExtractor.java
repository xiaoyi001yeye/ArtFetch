package extractor;

import org.jsoup.nodes.Document;

public class ArtistExtractor extends BaseLabelExtractor {

    public ArtistExtractor() {
        super("作者", "艺术家");
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        String value = extractFirstMatch(doc);
        if (value != null) {
            data.setArtist(value);
        }
    }
}
