package com.remisoft.userinfo.server.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 用户信息中心配置属性
 *
 * <p>集中管理用户中心（remi-userinfo）的安全参数与会话策略，
 * 替代分散的 {@code @Value} 硬编码常量。
 * 通过 {@link UserInfoConfiguration} 的 {@code @EnableConfigurationProperties} 注册。
 *
 * <p><b>配置前缀：</b>{@code remi.userinfo}
 *
 * <p><b>配置分组：</b>
 * <ul>
 *   <li><b>Token 会话</b>：{@link #tokenTtlSeconds}（access_token 有效期）</li>
 *   <li><b>登录安全</b>：{@link #maxLoginFailCount}、{@link #lockDurationMinutes}、{@link #captchaEnabled}、{@link #captchaTtlSeconds}</li>
 *   <li><b>密码策略</b>：{@link #passwordMinLength}、{@link #passwordMaxLength}、{@link #passwordMinCategoryCount}、{@link #bcryptStrength}</li>
 *   <li><b>健康检查</b>：{@link #healthEnabled}</li>
 *   <li><b>OAuth2</b>：{@link #oauth2Clients}（clientId → 客户端配置）</li>
 * </ul>
 *
 * <p><b>application.yml 示例：</b>
 * <pre>
 * remi:
 *   userinfo:
 *     token-ttl-seconds: 7200
 *     max-login-fail-count: 5
 *     lock-duration-minutes: 30
 *     captcha-enabled: true
 *     captcha-ttl-seconds: 300
 *     password-min-length: 8
 *     password-max-length: 64
 *     password-min-category-count: 3
 *     bcrypt-strength: 10
 *     oauth2-clients:
 *       third-party-app:
 *         client-secret: ${OAUTH2_CLIENT_SECRET:default-secret}
 *         redirect-uris:
 *           - https://example.com/callback
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "remi.userinfo")
public class UserInfoProperties {

    /** access_token 有效期（秒），默认 2 小时；超过此时间后客户端需使用 refresh_token 换发新 token */
    private long tokenTtlSeconds = 7200;

    /** 最大登录失败次数：超过该次数自动锁定账号，默认 5 次 */
    private int maxLoginFailCount = 5;

    /** 账号锁定时长（分钟），默认 30 分钟；锁定期间内即使密码正确也拒绝登录 */
    private int lockDurationMinutes = 30;

    /** 登录时是否强制要求图形验证码，默认启用（生产环境建议保持 true） */
    private boolean captchaEnabled = true;

    /** 图形验证码有效期（秒），默认 5 分钟；过期后需重新生成 */
    private long captchaTtlSeconds = 300;

    /** 健康检查是否启用，默认启用（影响 {@code /actuator/health} 中 userinfo 节点的可见性） */
    private boolean healthEnabled = true;

    /** 密码最小长度，默认 8 位（参考 NIST SP 800-63B 建议） */
    private int passwordMinLength = 8;

    /** 密码最大长度，默认 64 位（防止 DoS：BCrypt 72 字节截断） */
    private int passwordMaxLength = 64;

    /** 密码最少字符种类数（大写/小写/数字/特殊字符），默认 3 类（推荐 ≥ 3） */
    private int passwordMinCategoryCount = 3;

    /** BCrypt 加密强度（4-31），默认 10；值越大越慢越安全（每 +1 耗时约翻倍） */
    private int bcryptStrength = 10;

    /** OAuth2 客户端注册表（clientId → 客户端配置），用于第三方应用接入 */
    private Map<String, OAuth2Client> oauth2Clients = new HashMap<>();

    /**
     * OAuth2 客户端配置
     *
     * <p>由 {@link com.remisoft.userinfo.web.controller.OAuth2Controller} 在 {@code /authorize} 和
     * {@code /token} 端点校验 clientId / clientSecret / redirectUri。
     *
     * @author remi-team
     * @since 1.0.0
     */
    @Data
    public static class OAuth2Client {
        /** 客户端密钥：与 clientId 配对，在 /token 端点强制校验，建议存储在密钥管理服务 */
        private String clientSecret;
        /** 允许的回调地址白名单（RFC 6749 §3.1.2.3）：防止开放重定向攻击 */
        private List<String> redirectUris;
    }

    /**
     * OAuth2 客户端密钥校验
     *
     * <p>同时校验 clientId 是否注册 + clientSecret 是否匹配。
     * 任意参数为 null 时直接返回 false（防御性编程）。
     *
     * @param clientId     客户端 ID
     * @param clientSecret 客户端密钥
     * @return true 校验通过；false 客户端未注册或密钥不匹配
     */
    public boolean validateOAuth2Client(String clientId, String clientSecret) {
        if (clientId == null || clientSecret == null) {
            return false;
        }
        OAuth2Client client = oauth2Clients.get(clientId);
        return client != null && clientSecret.equals(client.getClientSecret());
    }
}
