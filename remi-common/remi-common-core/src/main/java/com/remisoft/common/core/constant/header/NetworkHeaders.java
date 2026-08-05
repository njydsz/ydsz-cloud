package com.remisoft.common.core.constant.header;

/**
 * 网络信息相关 HTTP 请求头常量
 *
 * <p>定义客户端 IP、请求来源等网络层面的 header。
 *
 * <p>对应模块：remi-common-web（解析）、AutoConfig 中 XFF 处理
 *
 * @author remi-team
 * @since 1.8.0
 */
public final class NetworkHeaders {

    private NetworkHeaders() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 请求来源标识
     *
     * <p>用于标识请求的来源渠道（如 PC Web / H5 / APP / 小程序）。
     */
    public static final String X_REQUEST_SOURCE = "X-Request-Source";

    /**
     * 请求来源 IP
     *
     * <p>用于服务间透传客户端真实 IP。通常由网关/负载均衡写入；
     * 若不存在，可由服务端根据 HttpServletRequest 获取并补齐。
     *
     * <p>区别于标准的 {@code X-Forwarded-For}（支持多段链路 IP），
     * 本系统约定使用单值，作为"客户端 IP"的透传载体。
     */
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * 标准的 X-Forwarded-For header 名称（多段链路）
     */
    public static final String X_FORWARDED_FOR_STANDARD = "X-Forwarded-For";

    /**
     * 标准的 X-Real-IP header 名称（Nginx 等 LB 常用）
     */
    public static final String X_REAL_IP = "X-Real-IP";

    /**
     * User-Agent header 名称
     */
    public static final String USER_AGENT = "User-Agent";

    /**
     * Referer header 名称
     */
    public static final String REFERER = "Referer";
}
