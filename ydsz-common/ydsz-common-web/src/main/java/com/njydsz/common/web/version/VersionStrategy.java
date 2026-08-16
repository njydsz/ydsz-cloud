package com.njydsz.common.web.version;

/**
 * API 版本提取策略枚举。
 *
 * <p>定义从 HTTP 请求中提取 API 版本号的方式。
 * 当前仅支持 URL 路径模式（如 {@code /v1/api/users}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum VersionStrategy {

    /** 从 URL 路径提取版本号。 */
    URL
}
