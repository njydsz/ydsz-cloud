package com.njydsz.common.app.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import com.njydsz.common.base.constant.BaseFilterOrders;

/**
 * App 端请求签名验证配置属性
 *
 * <p>控制签名验证的开关、密钥映射、时间戳容差、Nonce 防重放、路径白名单等。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   app:
 *     signature:
 *       enabled: true
 *       algorithm: HMAC-SHA256
 *       timestamp-tolerance: 300000          # 毫秒，默认 5 分钟
 *       nonce-cache-ttl: 300                 # 秒，默认 5 分钟
 *       app-secrets:                         # 多 App 密钥映射
 *         android-prod: ${ANDROID_APP_SECRET}
 *         ios-prod: ${IOS_APP_SECRET}
 *       ignore-urls:                         # 签名验证白名单
 *         - /api/app/public/**
 *         - /api/app/login
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "ydsz.app.signature")
public class AppSignatureProperties {

    /**
     * 是否启用签名验证
     *
     * <p>默认关闭（false），需业务方显式开启。开启时必须同时配置
     * {@code appSecret} 或 {@code appSecrets}，否则启动会抛出 {@link IllegalStateException}。
     */
    private boolean enabled = false;

    /**
     * 签名算法，默认 HMAC-SHA256
     *
     * <p>目前仅支持 HMAC-SHA256，预留扩展字段。
     */
    @NotBlank
    private String algorithm = "HMAC-SHA256";

    /**
     * 默认签名密钥（单 App 场景使用）
     *
     * <p>与 {@code appSecrets} 二选一：若配置了 {@code appSecrets}，则优先从 Map 中
     * 按 {@code appId} 查找密钥；未找到时降级到此默认密钥。
     */
    private String appSecret;

    /**
     * 多 App 密钥映射（多 App 场景使用）
     *
     * <p>Key 为 App ID（对应请求头 {@code X-App-Id}），Value 为对应的签名密钥。
     */
    private Map<String, String> appSecrets = new LinkedHashMap<>();

    /**
     * 时间戳容差（毫秒），默认 5 分钟（300000 毫秒）
     *
     * <p>客户端请求时间戳与服务端本地时间之差若超过此值，则视为过期请求并拒绝。
     */
    @Min(1000)
    private long timestampTolerance = 5 * 60 * 1000L;

    /**
     * Nonce 缓存 TTL（秒），默认 5 分钟（300 秒）
     *
     * <p>用于 Redis SETNX 防重放，应与 {@code timestampTolerance} 的秒值保持一致或略大。
     */
    @Min(10)
    private long nonceCacheTtl = 300;

    /**
     * App ID 请求头名称，默认 {@code X-App-Id}
     *
     * <p>客户端在请求头中携带此字段以标识 App 身份，服务端据此从 {@code appSecrets}
     * 中查找对应的签名密钥。
     */
    private String appIdHeader = "X-App-Id";

    /**
     * 签名验证白名单路径列表
     *
     * <p>支持 Ant 风格路径匹配（如 {@code /api/app/public/**}）。
     * 白名单中的请求跳过签名验证，适用于公开接口（登录、注册等）。
     */
    private List<String> ignoreUrls = new ArrayList<>();

    /**
     * 过滤器执行顺序
     *
     * <p>默认为 {@code HIGHEST_PRECEDENCE + 25}，在内容缓存过滤器之后、
     * 安全头过滤器之前执行，确保请求完整性校验优先于业务处理。
     */
    @Min(0)
    private int order = BaseFilterOrders.CONTENT_CACHING_FILTER + 5;

    /**
     * 判断是否启用签名验证
     *
     * @return true 表示启用，false 表示关闭
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用签名验证
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取签名算法
     *
     * @return 签名算法名称
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * 设置签名算法
     *
     * @param algorithm 签名算法名称
     */
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * 获取默认签名密钥
     *
     * @return 密钥字符串
     */
    public String getAppSecret() {
        return appSecret;
    }

    /**
     * 设置默认签名密钥
     *
     * @param appSecret 密钥字符串
     */
    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    /**
     * 获取多 App 密钥映射
     *
     * @return App ID 到密钥的映射
     */
    public Map<String, String> getAppSecrets() {
        return appSecrets;
    }

    /**
     * 设置多 App 密钥映射
     *
     * @param appSecrets App ID 到密钥的映射
     */
    public void setAppSecrets(Map<String, String> appSecrets) {
        this.appSecrets = appSecrets != null ? appSecrets : new LinkedHashMap<>();
    }

    /**
     * 获取时间戳容差（毫秒）
     *
     * @return 时间戳容差
     */
    public long getTimestampTolerance() {
        return timestampTolerance;
    }

    /**
     * 设置时间戳容差（毫秒）
     *
     * @param timestampTolerance 时间戳容差
     */
    public void setTimestampTolerance(long timestampTolerance) {
        this.timestampTolerance = timestampTolerance;
    }

    /**
     * 获取 Nonce 缓存 TTL（秒）
     *
     * @return Nonce 缓存 TTL
     */
    public long getNonceCacheTtl() {
        return nonceCacheTtl;
    }

    /**
     * 设置 Nonce 缓存 TTL（秒）
     *
     * @param nonceCacheTtl Nonce 缓存 TTL
     */
    public void setNonceCacheTtl(long nonceCacheTtl) {
        this.nonceCacheTtl = nonceCacheTtl;
    }

    /**
     * 获取 App ID 请求头名称
     *
     * @return 请求头名称
     */
    public String getAppIdHeader() {
        return appIdHeader;
    }

    /**
     * 设置 App ID 请求头名称
     *
     * @param appIdHeader 请求头名称
     */
    public void setAppIdHeader(String appIdHeader) {
        this.appIdHeader = appIdHeader;
    }

    /**
     * 获取签名验证白名单路径列表
     *
     * @return 白名单路径列表
     */
    public List<String> getIgnoreUrls() {
        return ignoreUrls;
    }

    /**
     * 设置签名验证白名单路径列表
     *
     * @param ignoreUrls 白名单路径列表
     */
    public void setIgnoreUrls(List<String> ignoreUrls) {
        this.ignoreUrls = ignoreUrls != null ? ignoreUrls : new ArrayList<>();
    }

    /**
     * 获取过滤器执行顺序
     *
     * @return 过滤器顺序值
     */
    public int getOrder() {
        return order;
    }

    /**
     * 设置过滤器执行顺序
     *
     * @param order 过滤器顺序值
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * 根据 App ID 查找签名密钥
     *
     * <p>优先从 {@code appSecrets} Map 中查找，未找到时降级到默认 {@code appSecret}。
     *
     * @param appId App ID（可为 null）
     * @return 对应的签名密钥，未找到时返回 null
     */
    public String resolveSecret(String appId) {
        if (appId != null && !appId.isBlank()) {
            String secret = appSecrets.get(appId);
            if (secret != null && !secret.isBlank()) {
                return secret;
            }
        }
        return appSecret;
    }

    /**
     * 检查是否已配置至少一个有效密钥
     *
     * @return true 表示至少有一个密钥可用
     */
    public boolean hasAnySecretConfigured() {
        return (appSecret != null && !appSecret.isBlank())
                || (!appSecrets.isEmpty());
    }
}
