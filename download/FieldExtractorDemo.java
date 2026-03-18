import extractor.ArtworkData;
import extractor.FieldExtractorChain;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * 字段提取器Demo - 直接读取本地已下载的HTML文件测试提取逻辑
 * 不需要启动Spring Boot，不需要Docker，直接运行即可验证
 */
public class FieldExtractorDemo {

    public static void main(String[] args) throws IOException {
        System.out.println("=========================================");
        System.out.println("字段提取器Demo - 测试本地HTML文件提取");
        System.out.println("=========================================\n");

        String fileName = "test-sample.html";
        String filePath = "c:/code/ArtFetch/download/" + fileName;
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("File not found: " + filePath);
            return;
        }

        System.out.println("\n===== Processing: " + fileName);
        System.out.println("-----------------------------------------");

        try {
            Document doc = Jsoup.parse(file, "UTF-8");
            ArtworkData data = new ArtworkData();
            FieldExtractorChain chain = new FieldExtractorChain();
            long start = System.currentTimeMillis();
            chain.extractAll(doc, data);
            long totalElapsed = System.currentTimeMillis() - start;
            System.out.println("Total extraction time: " + totalElapsed + "ms\n");

            printResult(data);
        } catch (Exception e) {
            System.err.println("Error processing " + fileName + ": " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=========================================");
        System.out.println("Extraction complete!");
    }

    private static void printResult(ArtworkData data) {
        System.out.println("Extraction Result:");
        System.out.println("-----------------------------------------");

        Field[] fields = ArtworkData.class.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(data);
                String valueStr = value == null ? "(null)" : "\"" + value + "\"";
                System.out.printf("%-20s : %s%n", field.getName(), valueStr);
            } catch (IllegalAccessException e) {
                System.out.printf("%-20s : [access denied%n", field.getName());
            }
        }
    }
}
