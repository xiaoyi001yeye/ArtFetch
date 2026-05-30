package com.artfetch.service;

import com.artfetch.entity.ObjectStorageConfig;
import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TOSV2ClientBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ObjectStorageClientFactory {

    private final ObjectStorageSecretService secretService;

    public TOSV2 create(ObjectStorageConfig config) {
        if (config == null) {
            throw new IllegalStateException("对象存储配置不存在");
        }
        String secretKey = secretService.decrypt(config.getSecretKeyEncrypted());
        return new TOSV2ClientBuilder().build(
                require(config.getRegion(), "Region"),
                require(config.getEndpoint(), "Endpoint"),
                require(config.getAccessKey(), "Access Key"),
                require(secretKey, "Secret Key")
        );
    }

    private String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("对象存储配置缺少 " + label);
        }
        return value.trim();
    }
}
