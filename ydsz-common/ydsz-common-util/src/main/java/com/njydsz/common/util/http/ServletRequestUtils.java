package com.njydsz.common.util.http;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;

import com.njydsz.common.util.ip.IpValidator;
import com.njydsz.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Servlet HTTP 请求解析工具类
 *
 * <p>封装 HTTP 请求头、请求参数、请求属性解析以及可信代理判断等能力。
 *
 * <p>本类为无状态工具类，所有方法均为纯操作传入的 {@link HttpServletRequest}，
 * * * 不涉及静态全局配置（可信代理集合使用 Spring Bean 方式管理）。
 *
 * <h2>可信代理配置</h2>
 * <p>2.0.0 起，可信代理 IP 集合可通过 Spring 容器注入：
 * <pre>{@code
 * &#64;Configuration
 * public class ProxyConfig {
 *     &#64;Bean
 *     public TrustedProxyConfiguration trustedProxyConfiguration() {
 *         return new TrustedProxyConfiguration(Set.of("10.0.0.1"));
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 2.0.0
 */
@Slf4j
public final class ServletRequestUtils {

    /** 由 AutoConfiguration 设置的 TrustedProxyConfiguration Supplier */
    private static volatile Supplier<TrustedProxyConfiguration> trustedProxyConfigSupplier;

    /**
     * 注册 TrustedProxyConfiguration 的 Supplier。
     *
     * @param supplier TrustedProxyConfiguration 提供者，非空
     */
    public static void setTrustedProxyConfigSupplier(Supplier<TrustedProxyConfiguration> supplier) {
        trustedProxyConfigSupplier = supplier;
    }

    private ServletRequestUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 可信代理判断 ====================

    /**
     * 判断指定 remoteAddr 是否为可信代理。
     *
     * <p>可信条件：内网/回环地址（RFC 1918 + 127.0.0.0/8 + ::1），
     * 或通过 {@link TrustedProxyConfiguration} 显式配置的代理 IP。
     *
     * @param remoteAddr 远程地址
     * @return true 表示可信代理
     */
    public static boolean isTrustedProxy(String remoteAddr) {
        if (StringUtils.isBlank(remoteAddr)) {
            return false;
        }
        // 内网/回环地址始终可信
        if (IpValidator.isInternalIp(remoteAddr)) {
            return true;
        }
        // 尝试从注册的 Supplier 获取配置（未注册时返回 false）
        Supplier<TrustedProxyConfiguration> supplier = trustedProxyConfigSupplier;
        if (supplier != null) {
            try {
                TrustedProxyConfiguration config = supplier.get();
                if (config != null) {
                    return config.isTrusted(remoteAddr);
                }
            } catch (Exception e) {
                // Bean 不存在，回退到仅内网/回环判断
            }
        }
        return false;
    }

    /**
     * 判断当前请求的直连对端是否为可信代理。
     *
     * @param request HTTP 请求
     * @return true 表示直连对端为可信代理；request 为 null 时返回 false
     */
    public static boolean isTrustedProxy(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        return isTrustedProxy(request.getRemoteAddr());
    }

    // ==================== 请求头解析 ====================

    /**
     * 获取所有请求头。
     *
     * @param request HTTP 请求
     * @return 请求头 Map（key 为头名，value 为头值）；request 为 null 返回空 Map
     */
    public static Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        if (request == null) {
            return map;
        }
        Enumeration<String> enumeration = request.getHeaderNames();
        if (enumeration != null) {
            while (enumeration.hasMoreElements()) {
                String key = enumeration.nextElement();
                String value = request.getHeader(key);
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * 获取指定请求头。
     *
     * @param request HTTP 请求
     * @param name    头名
     * @return 头值；若 request 为 null 或 name 为空返回 null
     */
    public static String getHeader(HttpServletRequest request, String name) {
        if (request == null || StringUtils.isEmpty(name)) {
            return null;
        }
        return request.getHeader(name);
    }

    // ==================== 请求参数解析 ====================

    /**
     * 获取所有请求参数。
     *
     * @param request HTTP 请求
     * @return 参数 Map；request 为 null 返回空 Map
     */
    public static Map<String, String> getParamMap(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        if (request == null) {
            return params;
        }
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String name = parameterNames.nextElement();
            params.put(name, request.getParameter(name));
        }
        return params;
    }

    /**
     * 获取指定请求参数。
     *
     * @param request HTTP 请求
     * @param name    参数名
     * @return 参数值；若 request 为 null 或 name 为空返回 null
     */
    public static String getParam(HttpServletRequest request, String name) {
        if (request == null || StringUtils.isEmpty(name)) {
            return null;
        }
        return request.getParameter(name);
    }

    /**
     * 获取请求参数（带默认值）。
     */
    public static String getParam(HttpServletRequest request, String name, String defaultValue) {
        String value = getParam(request, name);
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    /**
     * 获取整数请求参数。
     *
     * @param request HTTP 请求
     * @param name    参数名
     * @return 参数值；解析失败或缺失返回 null
     */
    public static Integer getIntParam(HttpServletRequest request, String name) {
        String value = getParam(request, name);
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("ServletRequestUtils -> getIntParam error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取整数请求参数（带默认值）。
     */
    public static Integer getIntParam(HttpServletRequest request, String name, Integer defaultValue) {
        Integer value = getIntParam(request, name);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取长整型请求参数。
     *
     * @param request HTTP 请求
     * @param name    参数名
     * @return 参数值；解析失败或缺失返回 null
     */
    public static Long getLongParam(HttpServletRequest request, String name) {
        String value = getParam(request, name);
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("ServletRequestUtils -> getLongParam error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取布尔型请求参数。
     */
    public static Boolean getBooleanParam(HttpServletRequest request, String name) {
        String value = getParam(request, name);
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        return Boolean.parseBoolean(value);
    }

    // ==================== 请求属性判断 ====================

    /**
     * 获取请求方法（GET/POST/PUT/DELETE 等）。
     */
    public static String getMethod(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getMethod();
    }

    /**
     * 获取请求 URI（不含 QueryString）。
     */
    public static String getUri(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getRequestURI();
    }

    /**
     * 获取完整请求 URL（含协议、域名、端口、路径，不含 QueryString）。
     */
    public static String getRequestUrl(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getRequestURL().toString();
    }

    /**
     * 判断是否为 AJAX 请求（X-Requested-With: XMLHttpRequest）。
     */
    public static boolean isAjaxRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String header = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equals(header);
    }

    /**
     * 判断是否为 JSON 请求（Content-Type 包含 application/json）。
     */
    public static boolean isJsonRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String contentType = request.getContentType();
        return StringUtils.isNotEmpty(contentType)
                && contentType.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE);
    }

    // ==================== URL 编码/解码 ====================

    /**
     * URL 编码（UTF-8）。
     *
     * <p>使用 JDK 标准 {@link URLEncoder}，适用于 QueryString、application/x-www-form-urlencoded 场景。
     */
    public static String urlEncode(String str) {
        if (str == null) {
            return null;
        }
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }

    /**
     * URL 解码（UTF-8）。
     *
     * <p>使用 JDK 标准 {@link URLDecoder}，适用于 QueryString 解析场景。
     */
    public static String urlDecode(String str) {
        if (str == null) {
            return null;
        }
        return URLDecoder.decode(str, StandardCharsets.UTF_8);
    }
}
