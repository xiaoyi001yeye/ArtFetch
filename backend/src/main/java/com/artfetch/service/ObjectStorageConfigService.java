package com.artfetch.service;

import cn.dev33.satoken.stp.StpUtil;
import com.artfetch.dto.ObjectStorageConfigDto;
import com.artfetch.dto.ObjectStorageTestResultDto;
import com.artfetch.dto.SaveObjectStorageConfigRequest;
import com.artfetch.entity.ObjectStorageConfig;
import com.artfetch.repository.ObjectStorageConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ObjectStorageConfigService {

    private final ObjectStorageConfigRepository repository;
    private final ObjectStorageSecretService secretService;
    private final HdImageObjectStorageService objectStorageService;

    public List<ObjectStorageConfigDto> listConfigs() {
        return repository.findAll().stream().map(ObjectStorageConfigDto::from).toList();
    }

    public ObjectStorageConfigDto getConfigForEdit(Long id) {
        ObjectStorageConfig config = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("对象存储配置不存在: " + id));
        ObjectStorageConfigDto dto = ObjectStorageConfigDto.from(config);
        dto.setAccessKey(config.getAccessKey());
        dto.setSecretKey(secretService.decrypt(config.getSecretKeyEncrypted()));
        return dto;
    }

    @Transactional
    public ObjectStorageConfigDto create(SaveObjectStorageConfigRequest request) {
        ObjectStorageConfig config = new ObjectStorageConfig();
        apply(config, request, true);
        config.setCreatedBy(currentUserId());
        return ObjectStorageConfigDto.from(repository.save(config));
    }

    @Transactional
    public ObjectStorageConfigDto update(Long id, SaveObjectStorageConfigRequest request) {
        ObjectStorageConfig config = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("对象存储配置不存在: " + id));
        apply(config, request, false);
        config.setUpdatedBy(currentUserId());
        return ObjectStorageConfigDto.from(repository.save(config));
    }

    @Transactional
    public ObjectStorageTestResultDto test(Long id) {
        ObjectStorageConfig config = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("对象存储配置不存在: " + id));
        try {
            objectStorageService.testConnection(config);
            config.setLastTestStatus("SUCCESS");
            config.setLastTestMessage("连接成功，bucket 可写");
            config.setLastTestAt(LocalDateTime.now());
            repository.save(config);
            return new ObjectStorageTestResultDto(true, config.getLastTestMessage());
        } catch (Exception e) {
            String message = objectStorageService.describeTosError(e);
            config.setLastTestStatus("FAILED");
            config.setLastTestMessage(message);
            config.setLastTestAt(LocalDateTime.now());
            repository.save(config);
            return new ObjectStorageTestResultDto(false, message);
        }
    }

    @Transactional
    public ObjectStorageConfigDto enable(Long id) {
        ObjectStorageConfig target = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("对象存储配置不存在: " + id));
        if (!"SUCCESS".equals(target.getLastTestStatus())) {
            throw new IllegalStateException("启用前请先测试连接成功");
        }
        repository.findAll().forEach(config -> {
            config.setEnabled(config.getId().equals(id));
            repository.save(config);
        });
        return ObjectStorageConfigDto.from(repository.findById(id).orElseThrow());
    }

    @Transactional
    public ObjectStorageConfigDto disable(Long id) {
        ObjectStorageConfig config = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("对象存储配置不存在: " + id));
        config.setEnabled(false);
        return ObjectStorageConfigDto.from(repository.save(config));
    }

    private void apply(ObjectStorageConfig config, SaveObjectStorageConfigRequest request, boolean create) {
        config.setName(request.getName().trim());
        config.setProvider(ObjectStorageConfig.Provider.VOLCENGINE_TOS);
        config.setEndpoint(request.getEndpoint().trim());
        config.setRegion(request.getRegion().trim());
        config.setBucket(request.getBucket().trim());
        config.setPathPrefix(blankToNull(request.getPathPrefix()));
        if (request.getAccessKey() != null && !request.getAccessKey().isBlank()) {
            config.setAccessKey(request.getAccessKey().trim());
        } else if (create || config.getAccessKey() == null || config.getAccessKey().isBlank()) {
            throw new IllegalArgumentException("Access Key 不能为空");
        }
        if (create || (request.getSecretKey() != null && !request.getSecretKey().isBlank())) {
            config.setSecretKeyEncrypted(secretService.encrypt(request.getSecretKey()));
        }
        if (config.getSecretKeyEncrypted() == null || config.getSecretKeyEncrypted().isBlank()) {
            throw new IllegalArgumentException("Secret Key 不能为空");
        }
        config.setPublicBaseUrl(blankToNull(request.getPublicBaseUrl()));
        config.setSdkMode(ObjectStorageConfig.SdkMode.VOLCENGINE_TOS_SDK);
        config.setNetworkType(parseNetworkType(request.getNetworkType()));
        config.setUploadEnabled(request.isUploadEnabled());
        config.setMigrateEnabled(request.isMigrateEnabled());
    }

    private ObjectStorageConfig.NetworkType parseNetworkType(String value) {
        if (value == null || value.isBlank()) {
            return ObjectStorageConfig.NetworkType.PUBLIC;
        }
        return ObjectStorageConfig.NetworkType.valueOf(value.trim().toUpperCase());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long currentUserId() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
