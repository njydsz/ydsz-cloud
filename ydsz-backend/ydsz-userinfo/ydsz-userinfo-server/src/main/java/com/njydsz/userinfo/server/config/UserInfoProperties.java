package com.njydsz.userinfo.server.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 用户信息中心配置属性。
 *
 * <p>集中管理安全参数，替代硬编码常量。
 * 通过 {@link UserInfoConfiguration} 的 {@code @EnableConfigurationProperties} 注册。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo")
public class UserInfoProperties {

    /** Token 有效期（秒） */
    private long tokenTtlSeconds = 7200;

    /** 最大登录失败次数 */
    private int maxLoginFailCount = 5;

    /** 账号锁定时长（分钟） */
    private int lockDurationMinutes = 30;

    /** 验证码是否启用 */
    private boolean captchaEnabled = true;

    /** 验证码有效期（秒） */
    private long captchaTtlSeconds = 300;

    /** 健康检查是否启用 */
    private boolean healthEnabled = true;

    /** 密码最小长度 */
    private int passwordMinLength = 8;

    /** 密码最大长度 */
    private int passwordMaxLength = 64;

    /** 密码最少字符种类数（大写/小写/数字/特殊字符） */
    private int passwordMinCategoryCount = 3;

    /** BCrypt 加密强度（4-31） */
    private int bcryptStrength = 10;

    /** OAuth2 客户端注册表（clientId → 客户端配置） */
    private Map<String, OAuth2Client> oauth2Clients = new HashMap<>();

    /**
     * OAuth2 客户端配置。
     */
    @Data
    public static class OAuth2Client {
        /** 客户端密钥 */
        private String clientSecret;
        /** 允许的回调地址白名单 */
        private List<String> redirectUris;
    }

    /**
     * OAuth2 客户端密钥校验。
     *
     * @param clientId     客户端 ID
     * @param clientSecret 客户端密钥
     * @return true 校验通过
     */
    public boolean validateOAuth2Client(String clientId, String clientSecret) {
        if (clientId == null || clientSecret == null) {
            return false;
        }
        OAuth2Client client = oauth2Clients.get(clientId);
        return client != null && clientSecret.equals(client.getClientSecret());
    }
}
