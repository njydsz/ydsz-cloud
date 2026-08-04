package com.remisoft.common.web.version;

/**
 * API 版本提取策略枚举。
 *
 * <p>定义从 HTTP 请求中提取 API 版本号的方式：
 * <ul>
 *   <li>{@link #URL} — 从 URL 路径提取（如 {@code /v1/api/users}）</li>
 *   <li>{@link #HEADER} — 从请求头提取（如 {@code X-API-Version: 1}）</li>
 *   <li>{@link #ACCEPT} — 从 Accept 头提取（如 {@code application/vnd.remi.v1+json}）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public enum VersionStrategy {

    /** 从 URL 路径提取版本号。 */
    URL,

    /** 从请求头提取版本号。 */
    HEADER,

    /** 从 Accept 头提取版本号。 */
    ACCEPT
}
