package com.njydsz.pmis.common.util.http;

import com.njydsz.pmis.common.core.constant.TokenConstants;
import com.njydsz.pmis.common.util.string.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import com.njydsz.pmis.common.util.json.JsonUtils;

/**
 * Servlet 环境下的 HTTP 工具类
 *
 * <p>提供基于 Servlet API 的常用 Web 工具方法，
 * 仅在 Servlet 编程模型下可用。WebFlux 场景请使用 {@link WebFluxUtils}。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
public final class ServletUtils {

    private ServletUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 获取当前请求的 HttpServletRequest (仅限 Servlet 环境)
     */
    public static HttpServletRequest getRequest() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取当前请求的 HttpServletResponse (仅限 Servlet 环境)
     */
    public static HttpServletResponse getResponse() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取请求头中的 Token
     */
    public static String getToken() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        String token = request.getHeader(TokenConstants.SUPPLY_AUTHORIZATION);
        if (StringUtils.isEmpty(token)) {
            token = request.getHeader(TokenConstants.AUTHENTICATION);
        }
        return replaceTokenPrefix(token);
    }

    private static String replaceTokenPrefix(String token) {
        if (StringUtils.isNotEmpty(token) && token.startsWith(TokenConstants.PREFIX)) {
            return token.replaceFirst(TokenConstants.PREFIX, "").trim();
        }
        return token;
    }

    /**
     * 将字符串渲染到客户端 (Servlet)
     */
    public static void renderString(HttpServletResponse response, String string) {
        try {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().print(string);
        } catch (IOException e) {
            log.error("ServletUtils -> renderString error: {}", e.getMessage());
        }
    }

    /**
     * 将对象渲染到客户端 (Servlet)
     */
    public static void renderObject(HttpServletResponse response, Object object) {
        if (object == null) {
            return;
        }
        renderString(response, JsonUtils.toJson(object));
    }

    /**
     * 获取所有请求头
     */
    public static Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
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
     * 获取指定请求头
     */
    public static String getHeader(HttpServletRequest request, String name) {
        if (request == null || StringUtils.isEmpty(name)) {
            return null;
        }
        return request.getHeader(name);
    }

    /**
     * 获取所有请求参数 (Servlet)
     */
    public static Map<String, String> getParamMap(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String name = parameterNames.nextElement();
            params.put(name, request.getParameter(name));
        }
        return params;
    }

    /**
     * 获取指定请求参数
     */
    public static String getParam(HttpServletRequest request, String name) {
        if (request == null || StringUtils.isEmpty(name)) {
            return null;
        }
        return request.getParameter(name);
    }

    /**
     * 获取请求参数（带默认值）
     */
    public static String getParam(HttpServletRequest request, String name, String defaultValue) {
        String value = getParam(request, name);
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    /**
     * 获取请求参数（转换为整数）
     */
    public static Integer getIntParam(HttpServletRequest request, String name) {
        String value = getParam(request, name);
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("ServletUtils -> getIntParam error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取请求参数（转换为整数，带默认值）
     */
    public static Integer getIntParam(HttpServletRequest request, String name, Integer defaultValue) {
        Integer value = getIntParam(request, name);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取请求参数（转换为长整型）
     */
    public static Long getLongParam(HttpServletRequest request, String name) {
        String value = getParam(request, name);
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("ServletUtils -> getLongParam error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取请求参数（转换为布尔型）
     */
    public static Boolean getBooleanParam(HttpServletRequest request, String name) {
        String value = getParam(request, name);
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * 获取客户端真实 IP 地址
     *
     * <p>支持多层代理场景，按优先级依次检查以下 Header：
     * X-Real-IP → X-Forwarded-For → Proxy-Client-IP → WL-Proxy-Client-IP
     * → HTTP_CLIENT_IP → HTTP_X_FORWARDED_FOR → RemoteAddr
     *
     * <p>当 X-Forwarded-For 包含多个 IP 时，取第一个（最左侧为真实客户端 IP）。
     * 当所有 Header 均无效时，返回 RemoteAddr。
     *
     * @param request HTTP 请求
     * @return 客户端 IP，不会返回 null
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "0.0.0.0";
        }

        String ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) {
            return ip;
        }

        ip = request.getHeader("X-Forwarded-For");
        if (isValidIp(ip)) {
            int index = ip.indexOf(',');
            return index != -1 ? ip.substring(0, index).trim() : ip.trim();
        }

        ip = request.getHeader("Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip;
        }

        ip = request.getHeader("WL-Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip;
        }

        ip = request.getHeader("HTTP_CLIENT_IP");
        if (isValidIp(ip)) {
            return ip;
        }

        ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (isValidIp(ip)) {
            return ip;
        }

        return request.getRemoteAddr();
    }

    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
    }

    /**
     * 获取请求方法
     */
    public static String getMethod(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getMethod();
    }

    /**
     * 获取请求 URI
     */
    public static String getUri(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getRequestURI();
    }

    /**
     * 获取完整请求 URL
     */
    public static String getRequestUrl(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getRequestURL().toString();
    }

    /**
     * 判断是否为 AJAX 请求
     */
    public static boolean isAjaxRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String header = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equals(header);
    }

    /**
     * 判断是否为 JSON 请求
     */
    public static boolean isJsonRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String contentType = request.getContentType();
        return StringUtils.isNotEmpty(contentType) &&
               contentType.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE);
    }

    /**
     * URL 编码
     */
    public static String urlEncode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }

    /**
     * URL 解码
     */
    public static String urlDecode(String str) {
        return URLDecoder.decode(str, StandardCharsets.UTF_8);
    }
}
