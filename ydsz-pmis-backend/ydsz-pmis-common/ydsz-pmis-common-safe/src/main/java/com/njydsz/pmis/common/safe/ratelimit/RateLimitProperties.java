package com.njydsz.pmis.common.safe.ratelimit;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 限流配置属性。
 *
 * <p>配置前缀 {@code ydsz.safe.ratelimit}，支持按 IP/用户/全局维度进行限流。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Validated
@ConfigurationProperties(prefix = "ydsz.safe.ratelimit")
public class RateLimitProperties {

    /**
     * 是否启用限流。
     */
    private boolean enabled = false;

    /**
     * 每秒请求限制数。
     */
    @Min(1)
    private int limitPerSecond = 100;

    /**
     * 突发容量（允许瞬时超过 limitPerSecond 的最大请求数）。
     */
    @Min(1)
    private int burstCapacity = 200;

    /**
     * 限流维度：IP / USER / GLOBAL。
     */
    private Dimension dimension = Dimension.IP;

    /**
     * IP 限流时的 Redis Key 前缀。
     */
    private String ipKey = "ratelimit:ip:";

    /**
     * 用户限流时的 Redis Key 前缀。
     */
    private String userKey = "ratelimit:user:";

    /**
     * 全局限流时的 Redis Key。
     */
    private String globalKey = "ratelimit:global";

    /**
     * 排除限流的路径列表（Ant 风格）。
     */
    private List<String> excludes = new ArrayList<>();

    /**
     * 限流拒绝时的提示消息。
     */
    private String message = "请求过于频繁，请稍后重试";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLimitPerSecond() {
        return limitPerSecond;
    }

    public void setLimitPerSecond(int limitPerSecond) {
        this.limitPerSecond = limitPerSecond;
    }

    public int getBurstCapacity() {
        return burstCapacity;
    }

    public void setBurstCapacity(int burstCapacity) {
        this.burstCapacity = burstCapacity;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public void setDimension(Dimension dimension) {
        this.dimension = dimension;
    }

    public String getIpKey() {
        return ipKey;
    }

    public void setIpKey(String ipKey) {
        this.ipKey = ipKey;
    }

    public String getUserKey() {
        return userKey;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public String getGlobalKey() {
        return globalKey;
    }

    public void setGlobalKey(String globalKey) {
        this.globalKey = globalKey;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 限流维度枚举。
     */
    public enum Dimension {
        /**
         * 按客户端 IP 限流。
         */
        IP,

        /**
         * 按登录用户限流。
         */
        USER,

        /**
         * 全局限流（所有请求共享一个令牌桶）。
         */
        GLOBAL
    }
}
