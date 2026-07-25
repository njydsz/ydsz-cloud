package com.njydsz.userinfo.server.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 用户信息中心配置属性。
 *
 * <p>集中管理安全参数，替代硬编码常量。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
@Component
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

    /** OAuth2 客户端密钥注册表（clientId → clientSecret） */
    private Map<String, String> oauth2Clients = new HashMap<>();

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
        String secret = oauth2Clients.get(clientId);
        return clientSecret.equals(secret);
    }
}
