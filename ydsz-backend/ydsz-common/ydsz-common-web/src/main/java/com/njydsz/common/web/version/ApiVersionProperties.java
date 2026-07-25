package com.njydsz.common.web.version;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * API 版本路由配置属性
 *
 * <p>控制 API 版本路由的行为，支持 URL 路径模式和 Accept 头模式。
 *
 * <p><b>配置示例：</b>
 * <pre>
 * ydsz:
 *   web:
 *     api-version:
 *       enabled: true
 *       default-version: "1.0"
 *       header-name: "X-API-Version"
 *       url-pattern: "/v{version}/**"
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.web.api-version")
public class ApiVersionProperties {

    /**
     * 是否启用 API 版本路由（默认 true）
     */
    private boolean enabled = true;

    /**
     * 默认 API 版本（当请求未指定版本时使用）
     */
    private String defaultVersion = "1.0";

    /**
     * 版本请求头名称（用于 Accept 头模式）
     */
    private String headerName = "X-API-Version";

    /**
     * URL 路径模式（用于 URL 路径模式）
     * <p>支持占位符 {version}，如 /v{version}/**
     */
    private String urlPattern = "/v{version}/**";

    /**
     * 版本路由策略
     * <ul>
     *   <li>URL - 基于 URL 路径（/v1/api/users）</li>
     *   <li>HEADER - 基于请求头（X-API-Version: 1.0）</li>
     *   <li>ACCEPT - 基于 Accept 头（application/vnd.ydsz.v1+json）</li>
     * </ul>
     */
    private VersionStrategy strategy = VersionStrategy.URL;

    /**
     * 版本路由策略枚举
     */
    public enum VersionStrategy {
        /**
         * 基于 URL 路径（/v1/api/users）
         */
        URL,

        /**
         * 基于请求头（X-API-Version: 1.0）
         */
        HEADER,

        /**
         * 基于 Accept 头（application/vnd.ydsz.v1+json）
         */
        ACCEPT
    }
}
