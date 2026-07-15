package com.njydsz.pmis.common.safe.util;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;

/**
 * 客户端 IP 解析工具
 *
 * <p>统一从 HTTP 请求中解析客户端真实 IP，支持多级反向代理场景。
 * 所有安全 Filter 统一使用此类获取客户端 IP，避免逻辑不一致导致的安全漏洞。
 *
 * <p><b>解析顺序：</b>
 * <ol>
 *   <li>X-Forwarded-For（取第一个非 unknown 的值）</li>
 *   <li>X-Real-IP</li>
 *   <li>Proxy-Client-IP</li>
 *   <li>WL-Proxy-Client-IP</li>
 *   <li>HTTP_CLIENT_IP</li>
 *   <li>HTTP_X_FORWARDED_FOR</li>
 *   <li>request.getRemoteAddr()</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public final class ClientIpResolver {

    private static final String UNKNOWN = "unknown";
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    private ClientIpResolver() {
    }

    /**
     * 从 HTTP 请求中解析客户端真实 IP
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
                int commaIndex = ip.indexOf(',');
                if (commaIndex > 0) {
                    ip = ip.substring(0, commaIndex).trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * 判断 IP 是否为内网地址
     *
     * @param ip IP 地址
     * @return true 为内网地址
     */
    public static boolean isInternalIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        return ip.startsWith("10.")
                || ip.startsWith("172.16.")
                || ip.startsWith("172.17.")
                || ip.startsWith("172.18.")
                || ip.startsWith("172.19.")
                || ip.startsWith("172.20.")
                || ip.startsWith("172.21.")
                || ip.startsWith("172.22.")
                || ip.startsWith("172.23.")
                || ip.startsWith("172.24.")
                || ip.startsWith("172.25.")
                || ip.startsWith("172.26.")
                || ip.startsWith("172.27.")
                || ip.startsWith("172.28.")
                || ip.startsWith("172.29.")
                || ip.startsWith("172.30.")
                || ip.startsWith("172.31.")
                || ip.startsWith("192.168.")
                || ip.startsWith("127.")
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "::1".equals(ip);
    }
}
