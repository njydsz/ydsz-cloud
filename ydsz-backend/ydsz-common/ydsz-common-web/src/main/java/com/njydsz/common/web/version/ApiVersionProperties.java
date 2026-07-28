package com.njydsz.common.web.version;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * API 版本管理配置属性。
 *
 * <p>P3-1: API 版本管理策略 — 支持版本演进、废弃管理和兼容性控制。
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * ydsz:
 *   api:
 *     version:
 *       current-version: v1
 *       deprecated-versions:
 *         - v0
 *       sunset-headers: true
 *       sunset-duration-days: 90
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.api.version")
public class ApiVersionProperties {

    /**
     * 当前 API 版本（如 "v1"）。
     */
    private String currentVersion = "v1";

    /**
     * 已废弃的版本列表（这些版本的请求将返回 410 Gone）。
     */
    private java.util.List<String> deprecatedVersions = java.util.List.of();

    /**
     * 是否在响应头中添加 Deprecation/Sunset 头（RFC 8594）。
     */
    private boolean sunsetHeaders = true;

    /**
     * 废弃过渡期天数（超过此天数后版本将被移除）。
     */
    private int sunsetDurationDays = 90;
}
