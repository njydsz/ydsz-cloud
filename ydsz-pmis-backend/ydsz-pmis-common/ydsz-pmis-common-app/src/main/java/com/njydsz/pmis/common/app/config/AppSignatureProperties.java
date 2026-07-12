package com.njydsz.pmis.common.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * App 端请求签名验证配置属性
 *
 * <p>控制签名验证的开关、密钥和时间戳容差。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "pmis.app.signature")
public class AppSignatureProperties {

    /**
     * 是否启用签名验证
     */
    private boolean enabled = false;

    /**
     * 签名密钥（用于 HMAC-SHA256 计算）
     */
    private String appSecret;

    /**
     * 时间戳容差（毫秒），默认 5 分钟
     */
    private long timestampTolerance = 5 * 60 * 1000L;

    /**
     * 过滤器执行顺序，默认在认证过滤器之前
     */
    private int order = 2;
}
