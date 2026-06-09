package com.artfetch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "artfetch")
public class AppProperties {

    private Source source = new Source();
    private Task task = new Task();
    private Image image = new Image();
    private ObjectStorage objectStorage = new ObjectStorage();
    private Price price = new Price();
    private Description description = new Description();
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
        /** 第二版高清图写入方式 */
        private HdWriteMode hdWriteMode = HdWriteMode.LEGACY_LOCAL;
        /** 高清图读取方式 */
        private HdDisplayMode hdDisplayMode = HdDisplayMode.TOS_CANONICAL;
        /** 新下载高清图写入方式 */
        private HdStorageMode hdStorageMode = HdStorageMode.LOCAL_ONLY;
        private Migration migration = new Migration();
    }

    public enum HdWriteMode {
        LEGACY_LOCAL,
        TOS_ONLY,
        LOCAL_AND_TOS_CANONICAL
    }

    public enum HdDisplayMode {
        LEGACY,
        DUAL_READ,
        TOS_CANONICAL
    }

    public enum HdStorageMode {
        LOCAL_ONLY,
        OBJECT_ONLY,
        LOCAL_AND_OBJECT
    }

    @Data
    public static class Migration {
        private int maxConcurrentTasks = 1;
        private int uploadConcurrency = 4;
        private int batchSize = 100;
        private int failFastThreshold = 50;
        private boolean deleteLocalAfterMigrated = false;
    }

    @Data
    public static class ObjectStorage {
        /** 用于加密对象存储 Secret Key 的服务端密钥 */
        private String encryptionKey = "change-me-object-storage-key";
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
    public static class Description {
        /** 拍品描述补充任务按多少条为一批更新进度 */
        private int batchSize = 96;
        /** 单任务内拍品描述详情页抓取并发数 */
        private int fetchConcurrency = 96;
        /** 抓取详情页补充拍品描述时的超时时间 */
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
        /** ArtFetch 默认管理员账号，仅在用户表为空时创建 */
        private String adminUsername = "admin";
        /** ArtFetch 默认管理员密码，仅在用户表为空时创建 */
        private String adminPassword = "change-me";
    }
}
