package com.njydsz.pmis.common.context;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 请求上下文持有者
 *
 * <p>使用 ThreadLocal 存储当前线程的认证信息和 HTTP 请求对象。
 * 放置在 common-core 模块以避免循环依赖。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class RequestHolder {

    private static final ThreadLocal<AuthInfo> authInfoHolder = new ThreadLocal<>();
    private static final ThreadLocal<HttpServletRequest> requestHolder = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, String>> extraHeadersHolder = new ThreadLocal<>();

    private RequestHolder() {
    }

    public static void add(AuthInfo authInfo) {
        authInfoHolder.set(authInfo);
    }

    public static void add(HttpServletRequest request) {
        requestHolder.set(request);
    }

    public static AuthInfo getAuthInfo() {
        return authInfoHolder.get();
    }

    public static HttpServletRequest getRequest() {
        return requestHolder.get();
    }

    public static void addExtraHeader(String key, String value) {
        Map<String, String> headers = extraHeadersHolder.get();
        if (headers == null) {
            headers = new HashMap<>();
            extraHeadersHolder.set(headers);
        }
        headers.put(key, value);
    }

    public static String getExtraHeader(String key) {
        Map<String, String> headers = extraHeadersHolder.get();
        if (headers == null) {
            return null;
        }
        return headers.get(key);
    }

    public static Map<String, String> getExtraHeaders() {
        Map<String, String> headers = extraHeadersHolder.get();
        return headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
    }

    public static String getHeader(String name) {
        HttpServletRequest request = getRequest();
        if (request != null) {
            String value = request.getHeader(name);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return getExtraHeader(name);
    }

    public static void remove() {
        authInfoHolder.remove();
        requestHolder.remove();
        extraHeadersHolder.remove();
    }
}
