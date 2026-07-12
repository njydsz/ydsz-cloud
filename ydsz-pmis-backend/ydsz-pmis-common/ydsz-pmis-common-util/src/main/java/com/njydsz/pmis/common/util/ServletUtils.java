package com.njydsz.pmis.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Servlet 工具类
 *
 * <p>提供获取当前 HTTP 请求、请求头等便捷方法。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class ServletUtils {

    private ServletUtils() {
    }

    /**
     * 获取当前 HttpServletRequest
     *
     * @return 当前请求，不存在时返回 null
     */
    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 获取请求头
     *
     * @param name 头名称
     * @return 头值，不存在时返回 null
     */
    public static String getHeader(String name) {
        HttpServletRequest request = getRequest();
        return request != null ? request.getHeader(name) : null;
    }

    /**
     * 获取客户端 IP
     *
     * @return IP 地址
     */
    public static String getClientIp() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
