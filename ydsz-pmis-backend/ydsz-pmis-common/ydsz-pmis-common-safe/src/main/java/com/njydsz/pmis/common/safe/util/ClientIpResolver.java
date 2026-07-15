package com.njydsz.pmis.common.safe.util;

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
 * @since 1.3.0
 */
public final class ClientIpResolver {

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

        String directIp = request.getRemoteAddr();

        // 如果直连 IP 是可信代理（本地回环或内网私有地址），才信任 X-Forwarded-For
        if (directIp != null && isTrustedProxy(directIp)) {
            String ip = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
                int index = ip.indexOf(',');
                if (index != -1) {
                    return ip.substring(0, index).trim();
                }
                return ip.trim();
            }
            ip = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
                return ip.trim();
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
