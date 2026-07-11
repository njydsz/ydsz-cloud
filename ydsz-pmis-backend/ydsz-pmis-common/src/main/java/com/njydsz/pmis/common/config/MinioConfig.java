package com.njydsz.pmis.common.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储统一配置（P2 架构优化：合并 system/project/cronjob 三份重复配置）。
 *
 * <p>通过 {@link ConditionalOnMissingBean} 注册，各业务模块自动继承，无需重复定义。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
@ConditionalOnMissingBean(name = "minioClient")
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