package com.njydsz.pmis.common.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * App 端请求签名验证配置属性
 *
 * <p>控制签名验证的开关、密钥和时间戳容差。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "remi.app.signature")
public class AppSignatureProperties {

    /**
     * 是否启用签名验证
     *
     * <p>默认关闭（false），需业务方显式开启。开启时必须同时配置
     * {@link #appSecret}，否则启动会抛出 {@link IllegalStateException}。
     */
    private boolean enabled = false;

    /**
     * 签名密钥（必填，用于 HMAC-SHA256 计算）
     */
    private String appSecret;

    /**
     * 时间戳容差（毫秒），默认 5 分钟
     *
     * <p>客户端请求时间与服务端本地时间之差若超过此值，则视为过期请求并拒绝。
     */
    private long timestampTolerance = 5 * 60 * 1000L;

    /**
     * 过滤器执行顺序，默认在认证过滤器之前
     */
    private int order = 2;

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
     * 获取签名密钥
     *
     * @return 密钥字符串
     */
    public String getAppSecret() {
        return appSecret;
    }

    /**
     * 设置签名密钥
     *
     * @param appSecret 密钥字符串
     */
    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
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
}
