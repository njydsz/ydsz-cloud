package com.njydsz.pmis.common.safe.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * IP 黑白名单配置属性
 *
 * <p>配置前缀 {@code ydsz.safe.ip-access}，用于控制 IP 访问控制行为。
 *
 * <p><b>工作模式：</b>
 * <ul>
 *   <li>BLACKLIST（黑名单模式）：黑名单中的 IP 被拒绝，其他 IP 放行</li>
 *   <li>WHITELIST（白名单模式）：白名单中的 IP 放行，其他 IP 被拒绝</li>
 * </ul>
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   safe:
 *     ip-access:
 *       enabled: true
 *       mode: BLACKLIST
 *       redis-key-prefix: "safe:ip:blacklist"
 *       default-block-seconds: 3600
 *       static-blacklist:
 *         - 192.168.1.100
 *         - 10.0.0.0/8
 *       static-whitelist:
 *         - 127.0.0.1
 *         - 10.0.0.0/8
 *       excludes:
 *         - /actuator/**
 * }</pre>
 *
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.ip-access")
public class IpAccessProperties {

    /**
     * 是否启用 IP 访问控制
     */
    private boolean enabled = false;

    /**
     * 访问控制模式
     */
    private AccessMode mode = AccessMode.BLACKLIST;

    /**
     * Redis Key 前缀（黑名单/白名单共用，通过后缀区分）
     */
    private String redisKeyPrefix = "safe:ip:";

    /**
     * 默认封禁时长（秒），自动封禁触发时使用
     */
    private long defaultBlockSeconds = 3600;

    /**
     * 本地缓存大小，缓存 Redis 查询结果降低延迟
     */
    private int localCacheSize = 10000;

    /**
     * 本地缓存 TTL（秒），过期后重新查询 Redis
     */
    private long localCacheTtlSeconds = 10;

    /**
     * 静态黑名单（启动时加载，支持 IP 和 CIDR 网段）
     */
    private List<String> staticBlacklist = new ArrayList<>();

    /**
     * 静态白名单（启动时加载，支持 IP 和 CIDR 网段）
     */
    private List<String> staticWhitelist = new ArrayList<>();

    /**
     * 排除路径列表（Ant 风格）
     */
    private List<String> excludes = new ArrayList<>();

    /**
     * 访问控制模式枚举
     */
    public enum AccessMode {
        /**
         * 黑名单模式：黑名单中的 IP 被拒绝
         */
        BLACKLIST,
        /**
         * 白名单模式：白名单中的 IP 放行
         */
        WHITELIST
    }
}
