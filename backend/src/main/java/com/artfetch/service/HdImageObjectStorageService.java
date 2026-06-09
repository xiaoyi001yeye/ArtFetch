package com.artfetch.service;

import com.artfetch.entity.Artwork;
import com.artfetch.entity.ObjectStorageConfig;
import com.artfetch.repository.ObjectStorageConfigRepository;
import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TosClientException;
import com.volcengine.tos.TosException;
import com.volcengine.tos.TosServerException;
import com.volcengine.tos.model.object.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HdImageObjectStorageService {

    private static final long LARGE_FILE_THRESHOLD_BYTES = 100L * 1024L * 1024L;
    private static final long MULTIPART_PART_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final int MULTIPART_TASK_NUM = 5;
    private static final String CANONICAL_PREFIX = "artfetch/hd-images";
    private static final String CANONICAL_VERSION = "v2";
    private static final String CANONICAL_FILENAME = "hd-lossless.png";

    private final ObjectStorageConfigRepository configRepository;
    private final ObjectStorageClientFactory clientFactory;

    public ObjectStorageConfig activeConfigForUpload() {
        ObjectStorageConfig config = configRepository.findByEnabledTrue()
                .orElseThrow(() -> new IllegalStateException("尚未启用火山 TOS 对象存储配置"));
        if (!config.isUploadEnabled()) {
            throw new IllegalStateException("当前火山 TOS 配置未开启新图上传");
        }
        return config;
    }

    public ObjectStorageConfig activeConfigForRead() {
        return configRepository.findByEnabledTrue()
                .orElseThrow(() -> new IllegalStateException("尚未启用火山 TOS 对象存储配置"));
    }

    public ObjectStorageConfig loadConfig(Long id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("对象存储配置不存在: " + id));
    }

    public UploadResult uploadFile(ObjectStorageConfig config, Path file, String objectKey) throws Exception {
        long size = Files.size(file);
        TOSV2 client = clientFactory.create(config);
        if (size >= LARGE_FILE_THRESHOLD_BYTES) {
            Path checkpoint = checkpointPath(file);
            Files.createDirectories(checkpoint.getParent());
            UploadFileV2Input input = new UploadFileV2Input()
                    .setBucket(config.getBucket())
                    .setKey(objectKey)
                    .setFilePath(file.toString())
                    .setEnableCheckpoint(true)
                    .setCheckpointFile(checkpoint.toString())
                    .setPartSize(MULTIPART_PART_SIZE_BYTES)
                    .setTaskNum(MULTIPART_TASK_NUM);
            UploadFileV2Output output = client.uploadFile(input);
            return new UploadResult(objectKey, output.getEtag(), size);
        }
        PutObjectFromFileInput input = new PutObjectFromFileInput()
                .setBucket(config.getBucket())
                .setKey(objectKey)
                .setFilePath(file.toString());
        PutObjectFromFileOutput output = client.putObjectFromFile(input);
        return new UploadResult(objectKey, output.getEtag(), size);
    }

    public UploadResult uploadBytes(ObjectStorageConfig config, byte[] bytes, String objectKey) throws Exception {
        return upload(config, new ByteArrayInputStream(bytes), bytes.length, objectKey);
    }

    public UploadResult upload(ObjectStorageConfig config, InputStream inputStream, long size, String objectKey) {
        TOSV2 client = clientFactory.create(config);
        PutObjectInput input = new PutObjectInput()
                .setBucket(config.getBucket())
                .setKey(objectKey)
                .setContent(inputStream);
        PutObjectOutput output = client.putObject(input);
        return new UploadResult(objectKey, output.getEtag(), size);
    }

    public StoredObject loadObject(ObjectStorageConfig config, String objectKey) {
        TOSV2 client = clientFactory.create(config);
        GetObjectV2Input input = new GetObjectV2Input()
                .setBucket(config.getBucket())
                .setKey(objectKey);
        GetObjectV2Output output = client.getObject(input);
        if (output.getContent() == null) {
            throw new IllegalStateException("火山 TOS 对象不存在: " + objectKey);
        }
        return new StoredObject(output, output.getContent());
    }

    public ObjectMetadata head(ObjectStorageConfig config, String objectKey) {
        TOSV2 client = clientFactory.create(config);
        HeadObjectV2Output output = client.headObject(new HeadObjectV2Input()
                .setBucket(config.getBucket())
                .setKey(objectKey));
        return new ObjectMetadata(output.getEtag(), output.getContentLength());
    }

    public boolean existsWithSize(ObjectStorageConfig config, String objectKey, long expectedSize) {
        try {
            ObjectMetadata metadata = head(config, objectKey);
            return metadata.size() == expectedSize;
        } catch (TosServerException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    public void testConnection(ObjectStorageConfig config) {
        validateConfigShape(config);
        String key = normalizePrefix(config.getPathPrefix()) + "_healthcheck/" + UUID.randomUUID() + ".txt";
        TOSV2 client = clientFactory.create(config);
        try {
            byte[] bytes = "artfetch-tos-healthcheck".getBytes();
            client.putObject(new PutObjectInput()
                    .setBucket(config.getBucket())
                    .setKey(key)
                    .setContent(new ByteArrayInputStream(bytes)));
            client.headObject(new HeadObjectV2Input().setBucket(config.getBucket()).setKey(key));
        } finally {
            try {
                client.deleteObject(new DeleteObjectInput().setBucket(config.getBucket()).setKey(key));
            } catch (TosException ignored) {
            }
        }
    }

    private void validateConfigShape(ObjectStorageConfig config) {
        String endpoint = config.getEndpoint() == null ? "" : config.getEndpoint().trim();
        String bucket = config.getBucket() == null ? "" : config.getBucket().trim();
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            throw new IllegalArgumentException("Endpoint 请按火山 TOS Java SDK 示例填写域名本身，不要带 http:// 或 https://，例如 tos-cn-beijing.volces.com");
        }
        if (endpoint.contains("/")) {
            throw new IllegalArgumentException("Endpoint 不能包含路径，只填写域名，例如 tos-cn-beijing.volces.com");
        }
        if (endpoint.startsWith(bucket + ".")) {
            throw new IllegalArgumentException("Endpoint 不要填写 Bucket 域名，请去掉桶名前缀，只填 tos-{region}.volces.com");
        }
        if (endpoint.startsWith("tos-s3-")) {
            throw new IllegalArgumentException("当前使用火山 TOS Java SDK，请填写普通 TOS Endpoint，不要填写 tos-s3-* S3 兼容 Endpoint");
        }
    }

    public String describeTosError(Exception e) {
        if (e instanceof TosServerException serverException) {
            return "火山 TOS 服务端错误"
                    + ", statusCode=" + serverException.getStatusCode()
                    + ", code=" + serverException.getCode()
                    + ", message=" + serverException.getMessage()
                    + ", requestId=" + serverException.getRequestID();
        }
        if (e instanceof TosClientException clientException) {
            return "火山 TOS 客户端错误: " + clientException.getMessage();
        }
        return e.getMessage();
    }

    public String buildObjectKey(ObjectStorageConfig config, Artwork artwork) {
        String externalId = artwork.getExternalId() != null && !artwork.getExternalId().isBlank()
                ? artwork.getExternalId().replaceAll("[^A-Za-z0-9_-]", "_")
                : "artwork_" + artwork.getId();
        return normalizePrefix(config.getPathPrefix())
                + "task-" + artwork.getTask().getId() + "/" + externalId + "/hd-lossless.png";
    }

    public String buildCanonicalObjectKey(String sourceProvider, String normalizedArtCode) {
        if (sourceProvider == null || sourceProvider.isBlank()) {
            throw new IllegalArgumentException("sourceProvider 不能为空");
        }
        if (normalizedArtCode == null || normalizedArtCode.isBlank()) {
            throw new IllegalArgumentException("normalizedArtCode 不能为空");
        }
        String source = sourceProvider.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        String artCode = normalizedArtCode.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        String hashHex = sha256Hex(source + ":" + artCode);
        return CANONICAL_PREFIX
                + "/" + CANONICAL_VERSION
                + "/source/" + source
                + "/art-code/" + hashHex.substring(0, 2)
                + "/" + hashHex.substring(0, 4)
                + "/" + artCode
                + "/" + CANONICAL_FILENAME;
    }

    private String normalizePrefix(String prefix) {
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

    private String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算高清大图 canonical key hash", e);
        }
    }

    private Path checkpointPath(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        Path checkpointDir = parent == null ? Paths.get("checkpoint") : parent.resolve("checkpoint");
        return checkpointDir.resolve(file.getFileName() + ".upload");
    }

    public record UploadResult(String objectKey, String etag, long size) {
    }

    public record ObjectMetadata(String etag, long size) {
    }

    public record StoredObject(GetObjectV2Output output, InputStream inputStream) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            output.close();
        }
    }
}
