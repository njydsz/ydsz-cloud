package com.njydsz.pmis.common.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * MinIO 配置属性。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "pmis.file.minio")
public class MinioConfig {

    /** MinIO 服务端点 */
    private String endpoint = "http://localhost:9000";

    /** 访问密钥 */
    private String accessKey = "minioadmin";

    /** 秘密密钥 */
    private String secretKey = "minioadmin";

    /** 默认 bucket 名称 */
    private String defaultBucket = "pmis";

    /** 预签名 URL 过期时间（秒） */
    private Integer urlExpireSeconds = 3600;

    /** 是否启用安全连接 */
    private boolean secure = false;
}
