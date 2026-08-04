package com.remisoft.common.web.version;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * API 版本管理配置属性。
 *
 * <p>P3-1: API 版本管理策略 — 支持版本演进、废弃管理和兼容性控制。
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * remi:
 *   api:
 *     version:
 *       enabled: true
 *       strategy: URL
 *       default-version: "1"
 *       header-name: X-API-Version
 *       current-version: v1
 *       deprecated-versions:
 *         - v0
 *       sunset-headers: true
 *       sunset-duration-days: 90
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "remi.api.version")
public class ApiVersionProperties {

    /**
     * 是否启用 API 版本路由。
     * <p>禁用后所有请求不进行版本匹配，直接放行。
     */
    private boolean enabled = true;

    /**
     * 版本提取策略（URL / HEADER / ACCEPT）。
     */
    private VersionStrategy strategy = VersionStrategy.URL;

    /**
     * 默认版本号（请求未携带版本信息时使用）。
     */
    private String defaultVersion = "1";

    /**
     * 请求头名称（strategy=HEADER 时生效）。
     */
    private String headerName = "X-API-Version";

    /**
     * 当前 API 版本（如 "v1"）。
     */
    private String currentVersion = "v1";

    /**
     * 已废弃的版本列表（这些版本的请求将返回 410 Gone）。
     */
    private List<String> deprecatedVersions = List.of();

    /**
     * 是否在响应头中添加 Deprecation/Sunset 头（RFC 8594）。
     */
    private boolean sunsetHeaders = true;

    /**
     * 废弃过渡期天数（超过此天数后版本将被移除）。
     */
    private int sunsetDurationDays = 90;
}
