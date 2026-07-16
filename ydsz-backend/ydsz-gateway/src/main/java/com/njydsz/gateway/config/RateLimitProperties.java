package com.njydsz.gateway.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * P2-15: 网关精细化限流配置属性
 *
 * <p>支持多维度限流策略：
 * <ul>
 *   <li>API 级限流（已有 SentinelApiLimitConfig 基础）</li>
 *   <li>用户级限流（按 userId 独立限流）</li>
 *   <li>IP 级限流（防止单 IP 暴力请求）</li>
 *   <li>租户级限流（多租户场景隔离）</li>
 *   <li>突发流量控制（令牌桶 + 突发容量）</li>
 * </ul>
 *
 * <p>配置示例（Nacos 推送或 application.yml）：
 * <pre>
 * ydsz:
 *   gateway:
 *     ratelimit:
 *       enabled: true
 *       # 用户级限流
 *       per-user:
 *         enabled: true
 *         default-qps: 50
 *         burst-capacity: 100
 *         # 按角色差异化限流
 *         role-limits:
 *           admin: 200
 *           manager: 100
 *           user: 50
 *       # IP 级限流
 *       per-ip:
 *         enabled: true
 *         default-qps: 30
 *         burst-capacity: 60
 *       # 租户级限流
 *       per-tenant:
 *         enabled: true
 *         default-qps: 500
 *         burst-capacity: 1000
 *       # 响应头
 *       response-headers:
 *         enabled: true
 *         retry-after: 5
 * </pre>
 *
 * @since 2.1.0
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "ydsz.gateway.ratelimit")
public class RateLimitProperties {

    /** 是否启用精细化限流 */
    private boolean enabled = true;

    /** 用户级限流配置 */
    private PerUserConfig perUser = new PerUserConfig();

    /** IP 级限流配置 */
    private PerIpConfig perIp = new PerIpConfig();

    /** 租户级限流配置 */
    private PerTenantConfig perTenant = new PerTenantConfig();

    /** 响应头配置 */
    private ResponseHeadersConfig responseHeaders = new ResponseHeadersConfig();

    /** 用户级限流配置 */
    @Data
    public static class PerUserConfig {
        private boolean enabled = true;
        /** 默认每秒请求数 */
        private int defaultQps = 50;
        /** 突发容量（令牌桶） */
        private int burstCapacity = 100;
        /** 按角色差异化 QPS */
        private Map<String, Integer> roleLimits;
    }

    /** IP 级限流配置 */
    @Data
    public static class PerIpConfig {
        private boolean enabled = true;
        /** 默认每秒请求数 */
        private int defaultQps = 30;
        /** 突发容量 */
        private int burstCapacity = 60;
        /** IP 白名单（不限流） */
        private List<String> whitelist;
    }

    /** 租户级限流配置 */
    @Data
    public static class PerTenantConfig {
        private boolean enabled = false;
        /** 默认每秒请求数 */
        private int defaultQps = 500;
        /** 突发容量 */
        private int burstCapacity = 1000;
    }

    /** 响应头配置 */
    @Data
    public static class ResponseHeadersConfig {
        private boolean enabled = true;
        /** Retry-After 头值（秒） */
        private int retryAfter = 5;
    }
}
