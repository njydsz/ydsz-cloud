package com.njydsz.pmis.file.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /** MinIO Endpoint */
    private String endpoint;

    /** AccessKey */
    private String accessKey;

    /** SecretKey */
    private String secretKey;

    /** 默认 Bucket */
    private String defaultBucket;

    /** 预签名 URL 过期秒数 */
    private Integer urlExpireSeconds = 3600;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
