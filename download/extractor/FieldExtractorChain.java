package extractor;

import org.jsoup.nodes.Document;

import java.util.List;

public class FieldExtractorChain {

    private final List<FieldExtractor> extractors;

    public FieldExtractorChain() {
        this.extractors = List.of(
            new LotNumberExtractor(),
            new ArtistExtractor(),
            new MediumExtractor(),
            new FormatExtractor(),
            new DimensionsExtractor(),
            new ValuationExtractor(),
            new AuctionHouseExtractor(),
            new AuctionNameExtractor(),
            new AuctionSessionExtractor(),
            new AuctionDateExtractor(),
            new AuctionLocationExtractor(),
            new PreviewTimeExtractor(),
            new PreviewLocationExtractor()
        );
    }

    public void extractAll(Document doc, ArtworkData data) {
        System.out.println("Starting extraction with " + extractors.size() + " extractors...");
        for (FieldExtractor extractor : extractors) {
            long start = System.currentTimeMillis();
            try {
                extractor.extract(doc, data);
                long elapsed = System.currentTimeMillis() - start;
                System.out.printf("  ✓ %-30s done in %3dms%n", extractor.getExtractorName(), elapsed);
            } catch (Exception e) {
                System.out.printf("  ✗ %-30s failed: %s%n", extractor.getExtractorName(), e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends FieldExtractor> T getExtractor(Class<T> type) {
        for (FieldExtractor extractor : extractors) {
            if (type.isInstance(extractor)) {
                return (T) extractor;
            }
        }
        return null;
    }
}
