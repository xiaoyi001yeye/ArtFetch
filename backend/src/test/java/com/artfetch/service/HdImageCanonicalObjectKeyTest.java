package com.artfetch.service;

import com.artfetch.entity.Artwork;
import com.artfetch.config.AppProperties;
import com.artfetch.entity.ObjectStorageConfig;
import com.artfetch.entity.SearchTask;
import com.volcengine.tos.model.object.DeleteObjectInput;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HdImageCanonicalObjectKeyTest {

    @Test
    void taskScopedObjectKeyCannotBeSharedAcrossEnvironments() {
        HdImageObjectStorageService service = new HdImageObjectStorageService(null, null);
        ObjectStorageConfig config = objectStorageConfig("artfetch/hd-images/prod");

        Artwork testArtwork = artwork(11L, "art5060841293");
        Artwork workArtwork = artwork(98L, "art5060841293");

        assertThat(service.buildObjectKey(config, testArtwork))
                .isEqualTo("artfetch/hd-images/prod/task-11/art5060841293/hd-lossless.png");
        assertThat(service.buildObjectKey(config, workArtwork))
                .isEqualTo("artfetch/hd-images/prod/task-98/art5060841293/hd-lossless.png");
        assertThat(service.buildObjectKey(config, testArtwork))
                .isNotEqualTo(service.buildObjectKey(config, workArtwork));
    }

    @Test
    void v2CanonicalObjectKeyUsesHardcodedCrossEnvironmentPrefixAndArtCodeHashShard() {
        HdImageObjectStorageService service = new HdImageObjectStorageService(null, null);

        assertThat(service.buildCanonicalObjectKey("artron", "art5060841293"))
                .isEqualTo("artfetch/hd-images/v2/source/artron/art-code/00/00bf/art5060841293/hd-lossless.png");
    }

    @Test
    void canonicalObjectKeyCanBeReadFromRealTosByWorkEnvironmentWithoutImportedImageMetadata() throws Exception {
        Assumptions.assumeTrue(realTosIntegrationEnabled(), """
                Set ARTFETCH_TOS_INTEGRATION_TEST=true and either direct ARTFETCH_TOS_* credentials \
                or DB-backed SPRING_DATASOURCE_* plus ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY.
                """);

        ObjectStorageConfig config = realTosConfig();
        ObjectStorageClientFactory clientFactory = realTosClientFactory();
        HdImageObjectStorageService service = new HdImageObjectStorageService(null, clientFactory);

        String externalId = "artfetch-canonical-it-" + UUID.randomUUID();
        Artwork testArtwork = artwork(11L, externalId);
        String uploadedKey = service.buildCanonicalObjectKey("artron", testArtwork.getExternalId());
        byte[] hdImage = ("real-tos-canonical-test:" + externalId).getBytes(StandardCharsets.UTF_8);

        try {
            HdImageObjectStorageService.UploadResult upload = service.uploadBytes(config, hdImage, uploadedKey);
            assertThat(upload.objectKey()).isEqualTo(uploadedKey);

            Artwork workArtwork = artwork(98L, externalId);
            workArtwork.setHdImageObjectKey(null);
            workArtwork.setHdImageObjectConfigId(null);

            String discoveredKey = service.buildCanonicalObjectKey("artron", workArtwork.getExternalId());

            assertThat(discoveredKey).isEqualTo(uploadedKey);
            try (HdImageObjectStorageService.StoredObject object = service.loadObject(config, discoveredKey)) {
                assertThat(object.inputStream().readAllBytes()).isEqualTo(hdImage);
            }
        } finally {
            try {
                clientFactory.create(config).deleteObject(new DeleteObjectInput()
                        .setBucket(config.getBucket())
                        .setKey(uploadedKey));
            } catch (Exception cleanupError) {
                // Do not hide the upload/read assertion failure; the key is unique per run.
            }
        }
    }

    private static Artwork artwork(Long taskId, String externalId) {
        SearchTask task = new SearchTask();
        task.setId(taskId);

        Artwork artwork = new Artwork();
        artwork.setTask(task);
        artwork.setExternalId(externalId);
        return artwork;
    }

    private static ObjectStorageConfig objectStorageConfig(String pathPrefix) {
        ObjectStorageConfig config = new ObjectStorageConfig();
        config.setPathPrefix(pathPrefix);
        return config;
    }

    private static ObjectStorageConfig realTosConfig() throws Exception {
        if (!hasDirectTosEnv()) {
            return realTosConfigFromDatabase();
        }

        String pathPrefix = envOrDefault("ARTFETCH_TOS_PATH_PREFIX", "artfetch/hd-images/integration-tests");
        ObjectStorageConfig config = objectStorageConfig(integrationTestPrefix(pathPrefix));
        config.setEndpoint(requiredEnv("ARTFETCH_TOS_ENDPOINT"));
        config.setRegion(requiredEnv("ARTFETCH_TOS_REGION"));
        config.setBucket(requiredEnv("ARTFETCH_TOS_BUCKET"));
        config.setAccessKey(requiredEnv("ARTFETCH_TOS_ACCESS_KEY"));
        config.setSecretKeyEncrypted(realTosSecretService().encrypt(requiredEnv("ARTFETCH_TOS_SECRET_KEY")));
        return config;
    }

    private static ObjectStorageConfig realTosConfigFromDatabase() throws Exception {
        try (var connection = DriverManager.getConnection(
                requiredEnv("SPRING_DATASOURCE_URL"),
                envOrDefault("SPRING_DATASOURCE_USERNAME", envOrDefault("POSTGRES_USER", "artfetch")),
                envOrDefault("SPRING_DATASOURCE_PASSWORD", requiredEnv("POSTGRES_PASSWORD")));
             var statement = connection.prepareStatement("""
                     select endpoint, region, bucket, path_prefix, access_key, secret_key_encrypted
                     from object_storage_configs
                     where enabled = true
                     order by id
                     limit 1
                     """)) {
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("No enabled object_storage_configs row found");
                }
                ObjectStorageConfig config = new ObjectStorageConfig();
                config.setEndpoint(result.getString("endpoint"));
                config.setRegion(result.getString("region"));
                config.setBucket(result.getString("bucket"));
                config.setPathPrefix(integrationTestPrefix(result.getString("path_prefix")));
                config.setAccessKey(result.getString("access_key"));
                config.setSecretKeyEncrypted(result.getString("secret_key_encrypted"));
                return config;
            }
        }
    }

    private static ObjectStorageClientFactory realTosClientFactory() {
        return new ObjectStorageClientFactory(realTosSecretService());
    }

    private static ObjectStorageSecretService realTosSecretService() {
        AppProperties appProperties = new AppProperties();
        appProperties.getObjectStorage().setEncryptionKey(envOrDefault(
                "ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY",
                "artfetch-tos-integration-test-key"
        ));
        return new ObjectStorageSecretService(appProperties);
    }

    private static String integrationTestPrefix(String pathPrefix) {
        return normalizePrefix(pathPrefix) + "canonical-integration-tests";
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String value = prefix.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isBlank() ? "" : value + "/";
    }

    private static boolean realTosIntegrationEnabled() {
        return "true".equalsIgnoreCase(System.getenv("ARTFETCH_TOS_INTEGRATION_TEST"))
                && (hasDirectTosEnv() || hasDatabaseTosEnv());
    }

    private static boolean hasDirectTosEnv() {
        return hasEnv("ARTFETCH_TOS_ENDPOINT")
                && hasEnv("ARTFETCH_TOS_REGION")
                && hasEnv("ARTFETCH_TOS_BUCKET")
                && hasEnv("ARTFETCH_TOS_ACCESS_KEY")
                && hasEnv("ARTFETCH_TOS_SECRET_KEY");
    }

    private static boolean hasDatabaseTosEnv() {
        return hasEnv("SPRING_DATASOURCE_URL")
                && (hasEnv("SPRING_DATASOURCE_PASSWORD") || hasEnv("POSTGRES_PASSWORD"))
                && hasEnv("ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY");
    }

    private static boolean hasEnv(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
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
}
