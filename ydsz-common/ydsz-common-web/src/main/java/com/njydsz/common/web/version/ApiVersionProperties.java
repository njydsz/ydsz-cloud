package com.njydsz.common.web.version;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API 版本管理配置属性。
 *
 * <p>支持基于 URL 路径的 API 版本路由（如 {@code /v1/api/users}）。
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * ydsz:
 *   api:
 *     version:
 *       enabled: true
 *       default-version: "1"
 *       current-version: v1
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
     *
     * <p>禁用后所有请求不进行版本匹配，直接放行。
     * 默认 false，需显式开启。
     */
    private boolean enabled = false;

    /**
     * 默认版本号（请求未携带版本信息时使用）。
     */
    private String defaultVersion = "1";

    /**
     * 当前 API 版本（如 "v1"）。
     */
    private String currentVersion = "v1";

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

}
