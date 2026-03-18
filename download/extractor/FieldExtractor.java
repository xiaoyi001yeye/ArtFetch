package extractor;

import org.jsoup.nodes.Document;

/**
 * 字段提取器接口
 */
public interface FieldExtractor {
    void extract(Document doc, ArtworkData data);
    
    default String getExtractorName() {
        return this.getClass().getSimpleName();
    }
}
