package com.remisoft.common.safe.util;

import com.remisoft.common.core.constant.HeaderConstants;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;

/**
 * 客户端 IP 解析工具
 *
 * <p>统一从 HTTP 请求中解析客户端真实 IP，支持多级反向代理场景。
 * 所有安全 Filter 统一使用此类获取客户端 IP，避免逻辑不一致导致的安全漏洞。
 *
 * <p><b>安全策略（可信代理校验）：</b>
 * <ol>
 *   <li>获取直连 IP（{@code request.getRemoteAddr()}，不可伪造）</li>
 *   <li>如果直连 IP 是可信代理（本地回环或内网私有地址），才信任 {@code X-Forwarded-For} / {@code X-Real-IP}</li>
 *   <li>否则直接使用直连 IP</li>
 * </ol>
 * 这样可以防止外部客户端伪造 {@code X-Forwarded-For} 绕过 IP 限流或 IP 黑白名单。
 *
 * <p><b>可信代理范围：</b>
 * <ul>
 *   <li>本地回环：127.0.0.0/8, ::1</li>
 *   <li>内网私有：10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16</li>
 *   <li>Docker 默认网段：172.17.0.0/16</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class ClientIpResolver {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ClientIpResolver.class);
    private static final String UNKNOWN = "unknown";
    private static final String DEFAULT_IP = "0.0.0.0";

    private ClientIpResolver() {
    }

    /**
     * 从 HTTP 请求中解析客户端真实 IP（含可信代理校验）
     *
     * <p>判断逻辑：
     * <ol>
     *   <li>获取直连 IP（request.getRemoteAddr()，不可伪造）</li>
     *   <li>如果直连 IP 是可信代理（本地回环或内网私有地址），才信任 X-Forwarded-For / X-Real-IP</li>
     *   <li>否则直接使用直连 IP</li>
     * </ol>
     * 这样可以防止外部客户端伪造 X-Forwarded-For 绕过 IP 限流。
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        return resolveFromHeaders(
                request.getRemoteAddr(),
                request.getHeader(HeaderConstants.X_FORWARDED_FOR),
                request.getHeader("X-Real-IP"));
    }

    /**
     * 从直连 IP 和代理头中解析客户端真实 IP（框架无关版本）。
     *
     * <p>本方法供 Servlet 和 WebFlux 两种栈共用，消除重复的 X-Forwarded-For 解析逻辑。
     * 判断逻辑与 {@link #getClientIp(HttpServletRequest)} 完全一致。
     *
     * @param directIp       直连 IP（不可伪造，由 TCP 连接获得）
     * @param xForwardedFor  X-Forwarded-For 头值（可为 null）
     * @param xRealIp        X-Real-IP 头值（可为 null）
     * @return 客户端真实 IP
     * @since 1.1.0
     */
    public static String resolveFromHeaders(String directIp, String xForwardedFor, String xRealIp) {
        if (directIp != null && isTrustedProxy(directIp)) {
            if (StringUtils.hasText(xForwardedFor) && !UNKNOWN.equalsIgnoreCase(xForwardedFor)) {
                int index = xForwardedFor.indexOf(',');
                if (index != -1) {
                    return xForwardedFor.substring(0, index).trim();
                }
                return xForwardedFor.trim();
            }
            if (StringUtils.hasText(xRealIp) && !UNKNOWN.equalsIgnoreCase(xRealIp)) {
                return xRealIp.trim();
            }
        }
        return directIp != null && !directIp.isEmpty() ? directIp : DEFAULT_IP;
    }

    /**
     * 判断 IP 是否为可信代理
     *
     * <p>可信代理包括：
     * <ul>
     *   <li>本地回环：127.0.0.0/8, ::1</li>
     *   <li>内网私有：10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16</li>
     *   <li>Docker 默认网段：172.17.0.0/16</li>
     * </ul>
     *
     * @param ip IP 地址
     * @return true 为可信代理
     */
    public static boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return true;
        }
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            try {
                int secondOctet = Integer.parseInt(ip.split("\\.")[1]);
                if (secondOctet >= 16 && secondOctet <= 31) {
                    return true;
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
                log.debug("Caught exception (ignored): {}", ignored.getMessage());
            }
        }
        return false;
    }

    /**
     * 判断 IP 是否为内网地址
     *
     * @param ip IP 地址
     * @return true 为内网地址
     */
    public static boolean isInternalIp(String ip) {
        return isTrustedProxy(ip);
    }
}
