package com.artfetch.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.artfetch.config.AppProperties;
import com.artfetch.entity.ObjectStorageConfig;
import com.volcengine.tos.TosServerException;
import com.volcengine.tos.model.object.DeleteObjectInput;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class HdImageCanonicalUpgradeAllTest {

    private static final String SOURCE_PROVIDER = "artron";
    private static final Pattern ARTRON_SOURCE_URL_PATTERN = Pattern.compile("/paimai-([^/?#]+)", Pattern.CASE_INSENSITIVE);

    @Test
    void upgradeAllDownloadedHdImagesToCanonicalTosObjects() throws Exception {
        quietTosSdkLogs();
        Assumptions.assumeTrue(enabled(), """
                Set ARTFETCH_HD_CANONICAL_UPGRADE_ALL=true to run this destructive integration test.
                It creates hd_image_canonical_sync_results, uploads v2 canonical TOS objects,
                and deletes old TOS objects only when ARTFETCH_HD_CANONICAL_UPGRADE_DELETE_OLD_TOS=true.
                """);
        Assumptions.assumeTrue(hasDatabaseEnv(), """
                Set SPRING_DATASOURCE_URL plus database credentials and
                ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY so the test can load artwork rows
                and enabled TOS config from the old environment database.
                """);

        boolean deleteOldTos = boolEnv("ARTFETCH_HD_CANONICAL_UPGRADE_DELETE_OLD_TOS");
        boolean skipSuccessful = boolEnv("ARTFETCH_HD_CANONICAL_UPGRADE_SKIP_SUCCESSFUL", true);
        int limit = intEnv("ARTFETCH_HD_CANONICAL_UPGRADE_LIMIT", 0);
        int concurrency = Math.max(1, intEnv("ARTFETCH_HD_CANONICAL_UPGRADE_CONCURRENCY", 8));
        int maxAttempts = Math.max(1, intEnv("ARTFETCH_HD_CANONICAL_UPGRADE_MAX_ATTEMPTS", 3));
        Path storageRoot = storageRoot();

        AppProperties appProperties = new AppProperties();
        appProperties.getObjectStorage().setEncryptionKey(requiredEnv("ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY"));
        ObjectStorageClientFactory clientFactory = new ObjectStorageClientFactory(new ObjectStorageSecretService(appProperties));
        HdImageObjectStorageService tos = new HdImageObjectStorageService(null, clientFactory);

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(true);
            ensureResultTable(connection);

            Map<Long, ObjectStorageConfig> configs = loadConfigs(connection);
            ObjectStorageConfig targetConfig = enabledConfig(configs);
            List<ArtworkRow> rows = allRows(connection, limit, skipSuccessful);
            assertThat(rows)
                    .as("No downloaded HD images found in old database")
                    .isNotEmpty();

            Summary summary = new Summary();
            int index = 0;
            System.out.println();
            System.out.println("HD canonical full upgrade started:");
            System.out.println("targetBucket=" + targetConfig.getBucket()
                    + ", totalRows=" + rows.size()
                    + ", deleteOldTos=" + deleteOldTos
                    + ", skipSuccessful=" + skipSuccessful
                    + ", concurrency=" + concurrency
                    + ", maxAttempts=" + maxAttempts
                    + ", storageRoot=" + storageRoot);

            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            CompletionService<ResultRow> completionService = new ExecutorCompletionService<>(executor);
            try {
                int nextIndex = 0;
                int inFlight = 0;
                while (nextIndex < rows.size() || inFlight > 0) {
                    while (inFlight < concurrency && nextIndex < rows.size()) {
                        ArtworkRow row = rows.get(nextIndex++);
                        completionService.submit(() -> upgradeOne(
                                row,
                                configs,
                                targetConfig,
                                storageRoot,
                                tos,
                                clientFactory,
                                deleteOldTos,
                                maxAttempts
                        ));
                        inFlight++;
                    }

                    ResultRow result = completionService.take().get();
                    inFlight--;
                    saveResult(connection, result, targetConfig.getBucket());
                    summary.record(result);
                    index++;
                    if (index <= 20 || index % 50 == 0 || index == rows.size() || !"SUCCESS".equals(result.status())) {
                        System.out.println(index + "/" + rows.size() + " " + result.toLogLine());
                    }
                }
            } finally {
                executor.shutdownNow();
            }

            System.out.println();
            System.out.println("HD canonical full upgrade summary:");
            System.out.println(summary.toLogLine());
            System.out.println("Results table: hd_image_canonical_sync_results");
            System.out.println("Check status, art_code, artwork_title, canonical_key, source_type, legacy_delete_status, message.");
        }
    }

    private ResultRow upgradeOne(ArtworkRow row,
                                 Map<Long, ObjectStorageConfig> configs,
                                 ObjectStorageConfig targetConfig,
                                 Path storageRoot,
                                 HdImageObjectStorageService tos,
                                 ObjectStorageClientFactory clientFactory,
                                 boolean deleteOldTos,
                                 int maxAttempts) {
        ResultRow lastResult = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            lastResult = upgradeOneAttempt(row, configs, targetConfig, storageRoot, tos, clientFactory, deleteOldTos);
            if ("SUCCESS".equals(lastResult.status()) || !isRetriable(lastResult)) {
                return lastResult;
            }
            sleepBeforeRetry(attempt);
        }
        return lastResult;
    }

    private ResultRow upgradeOneAttempt(ArtworkRow row,
                                        Map<Long, ObjectStorageConfig> configs,
                                        ObjectStorageConfig targetConfig,
                                        Path storageRoot,
                                        HdImageObjectStorageService tos,
                                        ObjectStorageClientFactory clientFactory,
                                        boolean deleteOldTos) {
        LocalDateTime startedAt = LocalDateTime.now();
        String artCode = resolveArtCode(row);
        if (artCode == null || artCode.isBlank()) {
            return ResultRow.failed(row, null, null, "NONE", "Cannot resolve artCode", startedAt);
        }
        String canonicalKey = tos.buildCanonicalObjectKey(SOURCE_PROVIDER, artCode);

        try {
            if (hasText(row.oldObjectKey())) {
                ObjectStorageConfig oldConfig = row.objectConfigId() == null
                        ? targetConfig
                        : configs.getOrDefault(row.objectConfigId(), targetConfig);
                try {
                    HdImageObjectStorageService.ObjectMetadata oldMetadata = tos.head(oldConfig, row.oldObjectKey());
                    copyOldTosObject(tos, oldConfig, row.oldObjectKey(), targetConfig, canonicalKey, oldMetadata.size());
                    HdImageObjectStorageService.ObjectMetadata canonicalMetadata = tos.head(targetConfig, canonicalKey);
                    String deleteStatus = deleteOldObjectIfNeeded(
                            clientFactory,
                            deleteOldTos,
                            oldConfig,
                            row.oldObjectKey(),
                            canonicalKey
                    );
                    return ResultRow.success(row, artCode, canonicalKey, "OLD_TOS",
                            oldMetadata.size(), canonicalMetadata.size(), deleteStatus, startedAt);
                } catch (TosServerException e) {
                    if (e.getStatusCode() != 404) {
                        return ResultRow.failed(row, artCode, canonicalKey, "OLD_TOS",
                                "Old TOS read failed: " + e.getMessage(), startedAt);
                    }
                }
            }

            try {
                HdImageObjectStorageService.ObjectMetadata canonicalMetadata = tos.head(targetConfig, canonicalKey);
                return ResultRow.success(row, artCode, canonicalKey, "CANONICAL_TOS_EXISTING",
                        canonicalMetadata.size(), canonicalMetadata.size(), "NOT_REQUIRED", startedAt);
            } catch (TosServerException e) {
                if (e.getStatusCode() != 404) {
                    if (hasText(row.localPath())) {
                        // Fall through to local-file upload. The canonical HEAD is only an idempotency fast path.
                    } else {
                    return ResultRow.failed(row, artCode, canonicalKey, "CANONICAL_TOS_EXISTING",
                            "Canonical TOS read failed: " + e.getMessage(), startedAt);
                    }
                }
            }

            if (hasText(row.localPath())) {
                Path localPath = resolveLocalPath(storageRoot, row.localPath());
                if (localPath == null) {
                    return ResultRow.failed(row, artCode, canonicalKey, "LOCAL_FILE",
                            "Local path escapes storage root: " + row.localPath(), startedAt);
                }
                Path localFile = resolveLocalHdFile(localPath);
                if (localFile != null) {
                    long oldSize = Files.size(localFile);
                    try (var inputStream = Files.newInputStream(localFile)) {
                        tos.upload(targetConfig, inputStream, oldSize, canonicalKey);
                    }
                    HdImageObjectStorageService.ObjectMetadata canonicalMetadata = tos.head(targetConfig, canonicalKey);
                    return ResultRow.success(row, artCode, canonicalKey, "LOCAL_FILE",
                            oldSize, canonicalMetadata.size(), "NOT_REQUIRED", startedAt);
                }
            }

            return ResultRow.failed(row, artCode, canonicalKey, "REGENERATE_REQUIRED",
                    "Old TOS object missing and local HD file missing; regenerate from Artron tiles in formal upgrade task",
                    startedAt);
        } catch (Exception e) {
            return ResultRow.failed(row, artCode, canonicalKey, "ERROR", e.getMessage(), startedAt);
        }
    }

    private Path resolveLocalPath(Path storageRoot, String localPath) {
        Path resolved = storageRoot.resolve(localPath).normalize();
        return resolved.startsWith(storageRoot) ? resolved : null;
    }

    private Path resolveLocalHdFile(Path localPath) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(localPath);
        if (Files.isDirectory(localPath)) {
            candidates.add(localPath.resolve("hd-lossless.png"));
        }
        Path fileName = localPath.getFileName();
        if (fileName != null && "checkpoint".equals(fileName.toString()) && localPath.getParent() != null) {
            candidates.add(localPath.getParent().resolve("hd-lossless.png"));
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isRetriable(ResultRow result) {
        if (!"FAILED".equals(result.status())) {
            return false;
        }
        String message = result.message() == null ? "" : result.message().toLowerCase();
        return "ERROR".equals(result.sourceType())
                || message.contains("read failed")
                || message.contains("request exception")
                || message.contains("timeout")
                || message.contains("connection")
                || message.contains("reset");
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(Math.min(5000L, attempt * 1000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void copyOldTosObject(HdImageObjectStorageService tos,
                                  ObjectStorageConfig oldConfig,
                                  String oldObjectKey,
                                  ObjectStorageConfig targetConfig,
                                  String canonicalKey,
                                  long objectSize) throws Exception {
        try {
            tos.copyObject(oldConfig, oldObjectKey, targetConfig, canonicalKey);
        } catch (Exception copyError) {
            try (HdImageObjectStorageService.StoredObject object = tos.loadObject(oldConfig, oldObjectKey)) {
                tos.upload(targetConfig, object.inputStream(), objectSize, canonicalKey);
            }
        }
    }

    private String deleteOldObjectIfNeeded(ObjectStorageClientFactory clientFactory,
                                           boolean deleteOldTos,
                                           ObjectStorageConfig oldConfig,
                                           String oldObjectKey,
                                           String canonicalKey) {
        if (!deleteOldTos) {
            return "SKIPPED_BY_FLAG";
        }
        if (oldObjectKey.equals(canonicalKey)) {
            return "NOT_REQUIRED";
        }
        try {
            clientFactory.create(oldConfig).deleteObject(new DeleteObjectInput()
                    .setBucket(oldConfig.getBucket())
                    .setKey(oldObjectKey));
            return "DELETED";
        } catch (Exception deleteError) {
            return "FAILED:" + deleteError.getMessage();
        }
    }

    private void ensureResultTable(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists hd_image_canonical_sync_results (
                        id bigserial primary key,
                        artwork_id bigint not null unique,
                        art_code varchar(255),
                        artwork_title text,
                        external_id varchar(255),
                        source_url text,
                        status varchar(30) not null,
                        source_type varchar(40) not null,
                        canonical_bucket varchar(255),
                        canonical_key text,
                        old_object_config_id bigint,
                        old_object_key text,
                        local_path text,
                        source_size bigint,
                        canonical_size bigint,
                        legacy_delete_status text not null default 'NOT_REQUIRED',
                        message text,
                        attempt_count integer not null default 0,
                        started_at timestamp,
                        completed_at timestamp,
                        updated_at timestamp not null default now()
                    )
                    """);
            statement.execute("create index if not exists idx_hd_canonical_sync_art_code on hd_image_canonical_sync_results (art_code)");
            statement.execute("create index if not exists idx_hd_canonical_sync_status on hd_image_canonical_sync_results (status)");
            statement.execute("create index if not exists idx_hd_canonical_sync_source_type on hd_image_canonical_sync_results (source_type)");
        }
    }

    private void saveResult(Connection connection, ResultRow result, String canonicalBucket) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into hd_image_canonical_sync_results (
                    artwork_id, art_code, artwork_title, external_id, source_url,
                    status, source_type, canonical_bucket, canonical_key,
                    old_object_config_id, old_object_key, local_path,
                    source_size, canonical_size, legacy_delete_status, message,
                    attempt_count, started_at, completed_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, now())
                on conflict (artwork_id) do update set
                    art_code = excluded.art_code,
                    artwork_title = excluded.artwork_title,
                    external_id = excluded.external_id,
                    source_url = excluded.source_url,
                    status = excluded.status,
                    source_type = excluded.source_type,
                    canonical_bucket = excluded.canonical_bucket,
                    canonical_key = excluded.canonical_key,
                    old_object_config_id = excluded.old_object_config_id,
                    old_object_key = excluded.old_object_key,
                    local_path = excluded.local_path,
                    source_size = excluded.source_size,
                    canonical_size = excluded.canonical_size,
                    legacy_delete_status = excluded.legacy_delete_status,
                    message = excluded.message,
                    attempt_count = hd_image_canonical_sync_results.attempt_count + 1,
                    started_at = excluded.started_at,
                    completed_at = excluded.completed_at,
                    updated_at = now()
                """)) {
            statement.setLong(1, result.artworkId());
            statement.setString(2, result.artCode());
            statement.setString(3, result.artworkTitle());
            statement.setString(4, result.externalId());
            statement.setString(5, result.sourceUrl());
            statement.setString(6, result.status());
            statement.setString(7, result.sourceType());
            statement.setString(8, canonicalBucket);
            statement.setString(9, result.canonicalKey());
            if (result.oldObjectConfigId() == null) {
                statement.setObject(10, null);
            } else {
                statement.setLong(10, result.oldObjectConfigId());
            }
            statement.setString(11, result.oldObjectKey());
            statement.setString(12, result.localPath());
            setLongOrNull(statement, 13, result.sourceSize());
            setLongOrNull(statement, 14, result.canonicalSize());
            statement.setString(15, result.legacyDeleteStatus());
            statement.setString(16, result.message());
            statement.setTimestamp(17, Timestamp.valueOf(result.startedAt()));
            statement.setTimestamp(18, Timestamp.valueOf(result.completedAt()));
            statement.executeUpdate();
        }
    }

    private void setLongOrNull(java.sql.PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    private Map<Long, ObjectStorageConfig> loadConfigs(Connection connection) throws SQLException {
        Map<Long, ObjectStorageConfig> configs = new HashMap<>();
        try (var statement = connection.prepareStatement("""
                select id, name, endpoint, region, bucket, path_prefix, access_key, secret_key_encrypted,
                       enabled, upload_enabled, migrate_enabled
                from object_storage_configs
                order by id
                """);
             var result = statement.executeQuery()) {
            while (result.next()) {
                ObjectStorageConfig config = new ObjectStorageConfig();
                config.setId(result.getLong("id"));
                config.setName(result.getString("name"));
                config.setEndpoint(result.getString("endpoint"));
                config.setRegion(result.getString("region"));
                config.setBucket(result.getString("bucket"));
                config.setPathPrefix(result.getString("path_prefix"));
                config.setAccessKey(result.getString("access_key"));
                config.setSecretKeyEncrypted(result.getString("secret_key_encrypted"));
                config.setEnabled(result.getBoolean("enabled"));
                config.setUploadEnabled(result.getBoolean("upload_enabled"));
                config.setMigrateEnabled(result.getBoolean("migrate_enabled"));
                configs.put(config.getId(), config);
            }
        }
        return configs;
    }

    private ObjectStorageConfig enabledConfig(Map<Long, ObjectStorageConfig> configs) {
        return configs.values().stream()
                .filter(ObjectStorageConfig::isEnabled)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No enabled object_storage_configs row found"));
    }

    private List<ArtworkRow> allRows(Connection connection, int limit, boolean skipSuccessful) throws SQLException {
        List<ArtworkRow> rows = new ArrayList<>();
        String sql = """
                select id, external_id, title, source_url, hd_image_path, hd_image_object_config_id, hd_image_object_key
                from artworks
                where hd_image_status = 'DOWNLOADED'
                  and (
                    (hd_image_object_key is not null and hd_image_object_key <> '')
                    or (hd_image_path is not null and hd_image_path <> '')
                  )
                  and (
                    (external_id is not null and external_id <> '')
                    or (source_url is not null and source_url like '%/paimai-%')
                  )
                """ + (skipSuccessful ? """
                  and not exists (
                    select 1
                    from hd_image_canonical_sync_results result
                    where result.artwork_id = artworks.id
                      and result.status = 'SUCCESS'
                  )
                """ : "") + """
                order by id asc
                """ + (limit > 0 ? " limit ?" : "");
        try (var statement = connection.prepareStatement(sql)) {
            if (limit > 0) {
                statement.setInt(1, limit);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Long objectConfigId = result.getObject("hd_image_object_config_id") == null
                            ? null
                            : result.getLong("hd_image_object_config_id");
                    rows.add(new ArtworkRow(
                            result.getLong("id"),
                            result.getString("external_id"),
                            result.getString("title"),
                            result.getString("source_url"),
                            result.getString("hd_image_path"),
                            objectConfigId,
                            result.getString("hd_image_object_key")
                    ));
                }
            }
        }
        return rows;
    }

    private String resolveArtCode(ArtworkRow row) {
        if (hasText(row.externalId())) {
            return normalizeArtCode(row.externalId());
        }
        if (!hasText(row.sourceUrl())) {
            return null;
        }
        var matcher = ARTRON_SOURCE_URL_PATTERN.matcher(row.sourceUrl());
        return matcher.find() ? normalizeArtCode(matcher.group(1)) : null;
    }

    private String normalizeArtCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        return normalized.isBlank() ? null : normalized;
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                requiredEnv("SPRING_DATASOURCE_URL"),
                envOrDefault("SPRING_DATASOURCE_USERNAME", envOrDefault("POSTGRES_USER", "artfetch")),
                envOrDefault("SPRING_DATASOURCE_PASSWORD", envOrDefault("POSTGRES_PASSWORD", "artfetch123"))
        );
    }

    private Path storageRoot() {
        return Path.of(envOrDefault("ARTFETCH_HD_UPGRADE_STORAGE_ROOT",
                        envOrDefault("ARTFETCH_IMAGE_STORAGE_PATH", "storage/original-images")))
                .toAbsolutePath()
                .normalize();
    }

    private static boolean enabled() {
        return boolEnv("ARTFETCH_HD_CANONICAL_UPGRADE_ALL");
    }

    private static boolean boolEnv(String name) {
        return "true".equalsIgnoreCase(System.getenv(name));
    }

    private static boolean boolEnv(String name, boolean defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : "true".equalsIgnoreCase(value);
    }

    private static boolean hasDatabaseEnv() {
        return hasEnv("SPRING_DATASOURCE_URL")
                && hasEnv("ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY");
    }

    private static boolean hasEnv(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }

    private static int intEnv(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void quietTosSdkLogs() {
        ((Logger) LoggerFactory.getLogger("com.volcengine.tos")).setLevel(Level.WARN);
    }

    private record ArtworkRow(Long id,
                              String externalId,
                              String title,
                              String sourceUrl,
                              String localPath,
                              Long objectConfigId,
                              String oldObjectKey) {
    }

    private record ResultRow(Long artworkId,
                             String artCode,
                             String artworkTitle,
                             String externalId,
                             String sourceUrl,
                             String sourceType,
                             String canonicalKey,
                             Long oldObjectConfigId,
                             String oldObjectKey,
                             String localPath,
                             String status,
                             Long sourceSize,
                             Long canonicalSize,
                             String legacyDeleteStatus,
                             String message,
                             LocalDateTime startedAt,
                             LocalDateTime completedAt) {

        private static ResultRow success(ArtworkRow row,
                                         String artCode,
                                         String canonicalKey,
                                         String sourceType,
                                         long sourceSize,
                                         long canonicalSize,
                                         String legacyDeleteStatus,
                                         LocalDateTime startedAt) {
            return new ResultRow(row.id(), artCode, row.title(), row.externalId(), row.sourceUrl(),
                    sourceType, canonicalKey, row.objectConfigId(), row.oldObjectKey(), row.localPath(),
                    "SUCCESS", sourceSize, canonicalSize, legacyDeleteStatus, "",
                    startedAt, LocalDateTime.now());
        }

        private static ResultRow failed(ArtworkRow row,
                                        String artCode,
                                        String canonicalKey,
                                        String sourceType,
                                        String message,
                                        LocalDateTime startedAt) {
            return new ResultRow(row.id(), artCode, row.title(), row.externalId(), row.sourceUrl(),
                    sourceType, canonicalKey, row.objectConfigId(), row.oldObjectKey(), row.localPath(),
                    "FAILED", null, null, "NOT_REQUIRED", message,
                    startedAt, LocalDateTime.now());
        }

        private String toLogLine() {
            return "artworkId=" + artworkId
                    + ", artCode=" + artCode
                    + ", title=" + artworkTitle
                    + ", status=" + status
                    + ", sourceType=" + sourceType
                    + ", sourceSize=" + sourceSize
                    + ", canonicalSize=" + canonicalSize
                    + ", legacyDeleteStatus=" + legacyDeleteStatus
                    + ", canonicalKey=" + canonicalKey
                    + (message == null || message.isBlank() ? "" : ", message=" + message);
        }
    }

    private static class Summary {
        private int success;
        private int failed;
        private int oldTos;
        private int localFile;
        private int canonicalExisting;
        private int regenerateRequired;
        private int oldDeleted;
        private int oldDeleteFailed;

        void record(ResultRow result) {
            if ("SUCCESS".equals(result.status())) {
                success++;
            } else {
                failed++;
            }
            switch (result.sourceType()) {
                case "OLD_TOS" -> oldTos++;
                case "LOCAL_FILE" -> localFile++;
                case "CANONICAL_TOS_EXISTING" -> canonicalExisting++;
                case "REGENERATE_REQUIRED" -> regenerateRequired++;
                default -> {
                }
            }
            if ("DELETED".equals(result.legacyDeleteStatus())) {
                oldDeleted++;
            } else if (result.legacyDeleteStatus() != null && result.legacyDeleteStatus().startsWith("FAILED:")) {
                oldDeleteFailed++;
            }
        }

        String toLogLine() {
            return "success=" + success
                    + ", failed=" + failed
                    + ", oldTos=" + oldTos
                    + ", localFile=" + localFile
                    + ", canonicalExisting=" + canonicalExisting
                    + ", regenerateRequired=" + regenerateRequired
                    + ", oldDeleted=" + oldDeleted
                    + ", oldDeleteFailed=" + oldDeleteFailed;
        }
    }
}
