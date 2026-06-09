package com.artfetch.service;

import com.artfetch.config.AppProperties;
import com.artfetch.entity.ObjectStorageConfig;
import com.volcengine.tos.TosServerException;
import com.volcengine.tos.model.object.DeleteObjectInput;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class HdImageCanonicalUpgradeSampleTest {

    private static final String CANONICAL_PREFIX = "artfetch/hd-images";
    private static final String KEY_VERSION = "v2";
    private static final String SOURCE_PROVIDER = "artron";
    private static final String FILENAME = "hd-lossless.png";
    private static final Pattern ARTRON_SOURCE_URL_PATTERN = Pattern.compile("/paimai-([^/?#]+)", Pattern.CASE_INSENSITIVE);

    @Test
    void upgradeSampleDownloadedHdImagesToCanonicalTosObjects() throws Exception {
        Assumptions.assumeTrue(enabled(), """
                Set ARTFETCH_HD_CANONICAL_UPGRADE_SAMPLE=true to run this destructive integration test.
                It uploads v2 canonical TOS objects and deletes old TOS objects only when
                ARTFETCH_HD_CANONICAL_UPGRADE_DELETE_OLD_TOS=true is also set.
                """);
        Assumptions.assumeTrue(hasDatabaseEnv(), """
                Set SPRING_DATASOURCE_URL plus database credentials and
                ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY so the test can load old artwork rows
                and enabled TOS config from the old environment database.
                """);

        int sampleSize = intEnv("ARTFETCH_HD_CANONICAL_UPGRADE_SAMPLE_SIZE", 10);
        boolean deleteOldTos = boolEnv("ARTFETCH_HD_CANONICAL_UPGRADE_DELETE_OLD_TOS");
        Path storageRoot = storageRoot();

        AppProperties appProperties = new AppProperties();
        appProperties.getObjectStorage().setEncryptionKey(requiredEnv("ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY"));
        ObjectStorageClientFactory clientFactory = new ObjectStorageClientFactory(new ObjectStorageSecretService(appProperties));
        HdImageObjectStorageService tos = new HdImageObjectStorageService(null, clientFactory);

        try (Connection connection = openConnection()) {
            Map<Long, ObjectStorageConfig> configs = loadConfigs(connection);
            ObjectStorageConfig targetConfig = enabledConfig(configs);
            List<ArtworkRow> rows = sampleRows(connection, sampleSize);
            assertThat(rows)
                    .as("No sampled downloaded HD images found in old database")
                    .isNotEmpty();

            List<ResultRow> results = new ArrayList<>();
            for (ArtworkRow row : rows) {
                results.add(upgradeOne(row, configs, targetConfig, storageRoot, tos, clientFactory, deleteOldTos));
            }

            System.out.println();
            System.out.println("HD canonical upgrade sample results:");
            for (ResultRow result : results) {
                System.out.println(result.toLogLine());
            }
            System.out.println();
            System.out.println("Check these canonical keys in TOS bucket `" + targetConfig.getBucket() + "`.");
        }
    }

    private ResultRow upgradeOne(ArtworkRow row,
                                 Map<Long, ObjectStorageConfig> configs,
                                 ObjectStorageConfig targetConfig,
                                 Path storageRoot,
                                 HdImageObjectStorageService tos,
                                 ObjectStorageClientFactory clientFactory,
                                 boolean deleteOldTos) {
        String artCode = resolveArtCode(row);
        if (artCode == null || artCode.isBlank()) {
            return ResultRow.failed(row, null, null, "NONE", "Cannot resolve artCode");
        }
        String canonicalKey = canonicalKey(artCode);

        try {
            if (row.oldObjectKey() != null && !row.oldObjectKey().isBlank()) {
                ObjectStorageConfig oldConfig = row.objectConfigId() == null
                        ? targetConfig
                        : configs.getOrDefault(row.objectConfigId(), targetConfig);
                try {
                    HdImageObjectStorageService.ObjectMetadata oldMetadata = tos.head(oldConfig, row.oldObjectKey());
                    try (HdImageObjectStorageService.StoredObject object = tos.loadObject(oldConfig, row.oldObjectKey())) {
                        tos.upload(targetConfig, object.inputStream(), oldMetadata.size(), canonicalKey);
                    }
                    HdImageObjectStorageService.ObjectMetadata canonicalMetadata = tos.head(targetConfig, canonicalKey);
                    String deleteStatus = "SKIPPED_BY_FLAG";
                    if (deleteOldTos && !row.oldObjectKey().equals(canonicalKey)) {
                        try {
                            clientFactory.create(oldConfig).deleteObject(new DeleteObjectInput()
                                    .setBucket(oldConfig.getBucket())
                                    .setKey(row.oldObjectKey()));
                            deleteStatus = "DELETED";
                        } catch (Exception deleteError) {
                            deleteStatus = "FAILED:" + deleteError.getMessage();
                        }
                    }
                    return ResultRow.success(row, artCode, canonicalKey, "OLD_TOS",
                            oldMetadata.size(), canonicalMetadata.size(), deleteStatus);
                } catch (TosServerException e) {
                    if (e.getStatusCode() != 404) {
                        return ResultRow.failed(row, artCode, canonicalKey, "OLD_TOS",
                                "Old TOS read failed: " + e.getMessage());
                    }
                }
            }

            if (row.localPath() != null && !row.localPath().isBlank()) {
                Path localPath = storageRoot.resolve(row.localPath()).normalize();
                if (!localPath.startsWith(storageRoot)) {
                    return ResultRow.failed(row, artCode, canonicalKey, "LOCAL_FILE",
                            "Local path escapes storage root: " + row.localPath());
                }
                if (Files.exists(localPath)) {
                    long oldSize = Files.size(localPath);
                    tos.uploadFile(targetConfig, localPath, canonicalKey);
                    HdImageObjectStorageService.ObjectMetadata canonicalMetadata = tos.head(targetConfig, canonicalKey);
                    return ResultRow.success(row, artCode, canonicalKey, "LOCAL_FILE",
                            oldSize, canonicalMetadata.size(), "NOT_REQUIRED");
                }
            }

            return ResultRow.failed(row, artCode, canonicalKey, "REGENERATE_REQUIRED",
                    "Old TOS object missing and local HD file missing; regenerate from Artron tiles in formal upgrade task");
        } catch (Exception e) {
            return ResultRow.failed(row, artCode, canonicalKey, "ERROR", e.getMessage());
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

    private List<ArtworkRow> sampleRows(Connection connection, int sampleSize) throws SQLException {
        List<ArtworkRow> rows = new ArrayList<>();
        try (var statement = connection.prepareStatement("""
                select id, external_id, source_url, hd_image_path, hd_image_object_config_id, hd_image_object_key
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
                order by random()
                limit ?
                """)) {
            statement.setInt(1, sampleSize);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Long objectConfigId = result.getObject("hd_image_object_config_id") == null
                            ? null
                            : result.getLong("hd_image_object_config_id");
                    rows.add(new ArtworkRow(
                            result.getLong("id"),
                            result.getString("external_id"),
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
        if (row.externalId() != null && !row.externalId().isBlank()) {
            return normalizeArtCode(row.externalId());
        }
        if (row.sourceUrl() == null || row.sourceUrl().isBlank()) {
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

    private String canonicalKey(String artCode) {
        String hashHex = sha256Hex(SOURCE_PROVIDER + ":" + artCode);
        return CANONICAL_PREFIX
                + "/" + KEY_VERSION
                + "/source/" + SOURCE_PROVIDER
                + "/art-code/" + hashHex.substring(0, 2)
                + "/" + hashHex.substring(0, 4)
                + "/" + artCode
                + "/" + FILENAME;
    }

    private String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot calculate SHA-256", e);
        }
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
        return boolEnv("ARTFETCH_HD_CANONICAL_UPGRADE_SAMPLE");
    }

    private static boolean boolEnv(String name) {
        return "true".equalsIgnoreCase(System.getenv(name));
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

    private record ArtworkRow(Long id,
                              String externalId,
                              String sourceUrl,
                              String localPath,
                              Long objectConfigId,
                              String oldObjectKey) {
    }

    private record ResultRow(Long artworkId,
                             String artCode,
                             String sourceType,
                             String canonicalKey,
                             String status,
                             Long sourceSize,
                             Long canonicalSize,
                             String legacyDeleteStatus,
                             String message) {

        private static ResultRow success(ArtworkRow row,
                                         String artCode,
                                         String canonicalKey,
                                         String sourceType,
                                         long sourceSize,
                                         long canonicalSize,
                                         String legacyDeleteStatus) {
            return new ResultRow(row.id(), artCode, sourceType, canonicalKey, "SUCCESS",
                    sourceSize, canonicalSize, legacyDeleteStatus, "");
        }

        private static ResultRow failed(ArtworkRow row,
                                        String artCode,
                                        String canonicalKey,
                                        String sourceType,
                                        String message) {
            return new ResultRow(row.id(), artCode, sourceType, canonicalKey, "FAILED",
                    null, null, "NOT_REQUIRED", message);
        }

        private String toLogLine() {
            return "artworkId=" + artworkId
                    + ", artCode=" + artCode
                    + ", status=" + status
                    + ", sourceType=" + sourceType
                    + ", sourceSize=" + sourceSize
                    + ", canonicalSize=" + canonicalSize
                    + ", legacyDeleteStatus=" + legacyDeleteStatus
                    + ", canonicalKey=" + canonicalKey
                    + (message == null || message.isBlank() ? "" : ", message=" + message);
        }
    }
}
