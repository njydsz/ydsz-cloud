package com.remisoft.common.util.http;

import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet 环境下的 HTTP 工具类
 *
 * <p>提供基于 Servlet API 的常用 Web 工具方法，
 * 仅在 Servlet 编程模型下可用。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @deprecated 自 2.0.0 起废弃，拆分为以下四个单一职责类（v3.0 移除）：
 *             <ul>
 *               <li>{@link ServletRequestUtils} - 请求头/参数/属性解析、可信代理判断</li>
 *               <li>{@link HttpResponseUtils} - 响应渲染（renderString/renderObject）</li>
 *               <li>{@link HttpTokenUtils} - Token 提取与前缀剥离</li>
 *               <li>{@link RequestContextUtils} - Spring RequestContextHolder 请求/响应获取</li>
 *             </ul>
 */
@Deprecated(since = "2.0.0", forRemoval = false)
public final class ServletUtils {

    private ServletUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 可信代理判断（委派） ====================

    /**
     * 配置可信代理 IP 集合（精确匹配，非 CIDR）。
     *
     * @deprecated 使用 {@link TrustedProxyConfiguration} Spring Bean 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static void setTrustedProxies(Set<String> proxies) {
        // 空操作：v2.0.0 起可信代理改为通过 Spring Bean 配置
    }

    /**
     * 判断 remoteAddr 是否为可信代理。
     *
     * @deprecated 使用 {@link ServletRequestUtils#isTrustedProxy(String)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    static boolean isTrustedProxy(String remoteAddr) {
        return ServletRequestUtils.isTrustedProxy(remoteAddr);
    }

    /**
     * 判断当前请求的直连对端是否为可信代理。
     *
     * @deprecated 使用 {@link ServletRequestUtils#isTrustedProxy(HttpServletRequest)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static boolean isTrustedProxy(HttpServletRequest request) {
        return ServletRequestUtils.isTrustedProxy(request);
    }

    // ==================== 请求上下文获取（委派） ====================

    /**
     * 获取当前请求的 HttpServletRequest。
     *
     * @deprecated 使用 {@link RequestContextUtils#getRequest()} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static HttpServletRequest getRequest() {
        return RequestContextUtils.getRequest();
    }

    /**
     * 获取当前请求的 HttpServletResponse。
     *
     * @deprecated 使用 {@link RequestContextUtils#getResponse()} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static HttpServletResponse getResponse() {
        return RequestContextUtils.getResponse();
    }

    // ==================== Token 提取（委派） ====================

    /**
     * 获取请求头中的 Token。
     *
     * @deprecated 使用 {@link HttpTokenUtils#getToken()} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static String getToken() {
        return HttpTokenUtils.getToken();
    }

    // ==================== 响应渲染（委派） ====================

    /**
     * 将字符串渲染到客户端。
     *
     * @deprecated 使用 {@link HttpResponseUtils#renderString(HttpServletResponse, String)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static void renderString(HttpServletResponse response, String string) {
        HttpResponseUtils.renderString(response, string);
    }

    /**
     * 将对象渲染到客户端。
     *
     * @deprecated 使用 {@link HttpResponseUtils#renderObject(HttpServletResponse, Object)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static void renderObject(HttpServletResponse response, Object object) {
        HttpResponseUtils.renderObject(response, object);
    }

    // ==================== 请求头/参数解析（委派） ====================

    /**
     * 获取所有请求头。
     *
     * @deprecated 使用 {@link ServletRequestUtils#getHeaders(HttpServletRequest)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static Map<String, String> getHeaders(HttpServletRequest request) {
        return ServletRequestUtils.getHeaders(request);
    }

    /**
     * 获取指定请求头。
     *
     * @deprecated 使用 {@link ServletRequestUtils#getHeader(HttpServletRequest, String)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static String getHeader(HttpServletRequest request, String name) {
        return ServletRequestUtils.getHeader(request, name);
    }

    /**
     * 获取所有请求参数。
     *
     * @deprecated 使用 {@link ServletRequestUtils#getParamMap(HttpServletRequest)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static Map<String, String> getParamMap(HttpServletRequest request) {
        return ServletRequestUtils.getParamMap(request);
    }

    /**
     * 获取指定请求参数。
     *
     * @deprecated 使用 {@link ServletRequestUtils#getParam(HttpServletRequest, String)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static String getParam(HttpServletRequest request, String name) {
        return ServletRequestUtils.getParam(request, name);
    }

    /**
     * 获取请求参数（带默认值）。
     *
     * @deprecated 使用 {@link ServletRequestUtils#getParam(HttpServletRequest, String, String)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static String getParam(HttpServletRequest request, String name, String defaultValue) {
        return ServletRequestUtils.getParam(request, name, defaultValue);
    }

    /**
     * 获取整数请求参数。
     *
     * @deprecated 使用 {@link ServletRequestUtils#getIntParam(HttpServletRequest, String)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static Integer getIntParam(HttpServletRequest request, String name) {
        return ServletRequestUtils.getIntParam(request, name);
    }

    /**
     * 获取整数请求参数（带默认值）。
     *
     * @deprecated 使用 {@link ServletRequestUtils#getIntParam(HttpServletRequest, String, Integer)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static Integer getIntParam(HttpServletRequest request, String name, Integer defaultValue) {
        return ServletRequestUtils.getIntParam(request, name, defaultValue);
    }

    /**
     * 获取长整型请求参数。
     *
     * @deprecated 使用 {@link ServletRequestUtils#getLongParam(HttpServletRequest, String)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static Long getLongParam(HttpServletRequest request, String name) {
        return ServletRequestUtils.getLongParam(request, name);
    }

    /**
     * 获取布尔型请求参数。
     *
     * @deprecated 使用 {@link ServletRequestUtils#getBooleanParam(HttpServletRequest, String)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static Boolean getBooleanParam(HttpServletRequest request, String name) {
        return ServletRequestUtils.getBooleanParam(request, name);
    }

    // ==================== 请求属性（委派） ====================

    /**
     * 获取请求方法。
     *
     * @deprecated 使用 {@link ServletRequestUtils#getMethod(HttpServletRequest)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static String getMethod(HttpServletRequest request) {
        return ServletRequestUtils.getMethod(request);
    }

    /**
     * 获取请求 URI。
     *
     * @deprecated 使用 {@link ServletRequestUtils#getUri(HttpServletRequest)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static String getUri(HttpServletRequest request) {
        return ServletRequestUtils.getUri(request);
    }

    /**
     * 获取完整请求 URL。
     *
     * @deprecated 使用 {@link ServletRequestUtils#getRequestUrl(HttpServletRequest)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static String getRequestUrl(HttpServletRequest request) {
        return ServletRequestUtils.getRequestUrl(request);
    }

    /**
     * 判断是否为 AJAX 请求。
     *
     * @deprecated 使用 {@link ServletRequestUtils#isAjaxRequest(HttpServletRequest)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static boolean isAjaxRequest(HttpServletRequest request) {
        return ServletRequestUtils.isAjaxRequest(request);
    }

    /**
     * 判断是否为 JSON 请求。
     *
     * @deprecated 使用 {@link ServletRequestUtils#isJsonRequest(HttpServletRequest)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static boolean isJsonRequest(HttpServletRequest request) {
        return ServletRequestUtils.isJsonRequest(request);
    }

    // ==================== URL 编解码（委派） ====================

    /**
     * URL 编码。
     *
     * @deprecated 使用 {@link ServletRequestUtils#urlEncode(String)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static String urlEncode(String str) {
        return ServletRequestUtils.urlEncode(str);
    }

    /**
     * URL 解码。
     *
     * @deprecated 使用 {@link ServletRequestUtils#urlDecode(String)} 替代
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static String urlDecode(String str) {
        return ServletRequestUtils.urlDecode(str);
    }
}
