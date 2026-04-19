package com.artfetch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "artfetch")
public class AppProperties {

    private Source source = new Source();
    private Task task = new Task();
    private Image image = new Image();
    private Price price = new Price();
    private Auth auth = new Auth();

    @Data
    public static class Source {
        /** 雅昌艺搜检索地址 */
        private String baseUrl = "https://artso.artron.net/auction/search_auction.php";
        /** 每轮抓取完毕后等待多少秒再重新开始（0 = 不重复） */
        private int fetchIntervalSeconds = 0;
        /** 每次请求之间的延迟毫秒，避免被限流 */
        private long requestDelayMs = 300;
        /** 单任务内详情页抓取并发数 */
        private int detailFetchConcurrency = 96;
    }

    @Data
    public static class Task {
        private int maxConcurrentTasks = 8;
        private int threadPoolSize = 8;
    }

    @Data
    public static class Image {
        /** 原始图片的持久化目录 */
        private String storagePath = "storage/original-images";
        /** 下载原始图片的超时时间 */
        private int downloadTimeoutMs = 30_000;
        /** 原图补充任务按多少条为一批更新进度 */
        private int batchSize = 25;
        /** 补图任务单任务内作品并发数 */
        private int artworkConcurrency = 4;
        /** 超清大图瓦片下载并发数 */
        private int fetchConcurrency = 96;
    }

    @Data
    public static class Price {
        /** 成交价补充任务按多少条为一批更新进度 */
        private int batchSize = 96;
        /** 单任务内成交价详情页抓取并发数 */
        private int fetchConcurrency = 96;
        /** 抓取详情页补充成交价时的超时时间 */
        private int fetchTimeoutMs = 30_000;
    }

    @Data
    public static class Auth {
        /** 雅昌登录后的 Cookie Header，用于抓取会员可见字段 */
        private String artronCookie = "";
        /** 雅昌登录账号（本机环境变量注入） */
        private String artronAccount = "";
        /** 雅昌登录密码（本机环境变量注入） */
        private String artronPassword = "";
    }
}
