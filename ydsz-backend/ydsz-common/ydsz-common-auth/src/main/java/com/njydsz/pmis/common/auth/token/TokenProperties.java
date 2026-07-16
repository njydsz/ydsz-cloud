package com.njydsz.common.auth.token;

import java.nio.charset.StandardCharsets;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Token 配置属性
 *
 * @since 1.0.0
 * 
 */
@Data
@ConfigurationProperties(prefix = "ydsz.auth.token")
public class TokenProperties {

    /**
     * 是否启用 Token 服务，默认 true
     */
    private boolean enabled = true;

    /**
     * JWT 签名密钥（HMAC-SHA256）
     * <p>生产环境必须配置，建议使用 256 位（32 字节）以上的随机字符串
     */
    private String secretKey;

    /**
     * Access Token 有效期（秒），默认 2 小时
     */
    private long accessTokenExpireSeconds = 7200;

    /**
     * Refresh Token 有效期（秒），默认 7 天
     */
    private long refreshTokenExpireSeconds = 604800;

    /**
     * Token 签发者（issuer）
     */
    private String issuer = "ydsz-common";

    /**
     * Token 主题（subject）
     */
    private String subject = "ydsz-user";

    /**
     * 校验密钥配置
     * <p>启动时检查 secretKey 是否已配置且长度 >= 32 字节
     *
     * @throws IllegalStateException 若密钥未配置或长度不足
     */
    @PostConstruct
    public void validate() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "JWT secretKey 未配置，请在配置文件中设置 ydsz.auth.token.secret-key（建议 32 字节以上的随机字符串）");
        }
        if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT secretKey 长度不足 32 字节，当前长度: " + secretKey.getBytes(StandardCharsets.UTF_8).length +
                            "，请使用更安全的密钥");
        }
    }
}
