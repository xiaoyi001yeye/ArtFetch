package com.artfetch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "artfetch")
public class AppProperties {

    private Source source = new Source();
    private Task task = new Task();

    @Data
    public static class Source {
        /** 雅昌艺搜检索地址 */
        private String baseUrl = "https://artso.artron.net/auction/search_auction.php";
        /** 每轮抓取完毕后等待多少秒再重新开始（0 = 不重复） */
        private int fetchIntervalSeconds = 0;
        /** 每次请求之间的延迟毫秒，避免被限流 */
        private long requestDelayMs = 1000;
    }

    @Data
    public static class Task {
        private int maxConcurrentTasks = 5;
        private int threadPoolSize = 10;
    }
}
