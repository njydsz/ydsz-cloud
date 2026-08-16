package com.njydsz.common.web.version;

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
 * ydsz:
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
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.api.version")
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

    /**
     * 是否在启动时校验所有 @ApiVersion 注解的合法性。
     *
     * <p>校验项包括 since/deprecatedAt 版本格式、sunsetAt 日期格式、版本逻辑一致性。
     * 默认 true。设置为 false 可关闭校验（不推荐）。
     *
     * @since 1.2.0
     */
    private boolean validate = true;

    /**
     * 是否启用灵活匹配模式。
     *
     * <p>开启后支持主版本双向前缀匹配（"1" 匹配 "1.0"）。
     * 默认 false，使用精确匹配 + 主版本前缀匹配即可覆盖大多数场景。
     *
     * <p><b>灵活匹配示例：</b>
     * <ul>
     *   <li>请求版本 "1" 匹配接口版本 "1.0" → true</li>
     *   <li>请求版本 "1.0" 匹配接口版本 "1" → true</li>
     * </ul>
     *
     * @since 1.0.0
     */
    private boolean flexibleMatching = false;
