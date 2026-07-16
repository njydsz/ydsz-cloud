package com.njydsz.common.util.auth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.alibaba.ttl.TransmittableThreadLocal;

import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 *
 *
 * <p>除 AuthInfo 与 HttpServletRequest 外，还支持维护"额外请求头（virtual headers）"：
 * 当业务代码并不处于真实 HTTP 请求线程（如异步线程、AOP 注入的数据范围等）时，
 * 可以将数据权限相关 header 写入此处，供下游链路（如 SQL 拦截器、Feign 透传）读取。
 *
 * <p><b>线程池场景说明：</b>
 * 本类使用阿里的 {@link TransmittableThreadLocal}（TTL），可安全支持线程池场景下的上下文透传，
 * 解决了 {@link InheritableThreadLocal} 在线程池复用时上下文泄露的问题。
 *
 * <p><b>强制约定：</b>所有 Filter / Interceptor 必须在 finally 块中调用 {@link #remove()}。
 *
 * <p><b>正确用法：</b>
 * <pre>{@code
 * try {
 *     RequestHolder.add(authInfo);
 *     RequestHolder.add(request);
 *     chain.doFilter(req, resp);
 * } finally {
 *     RequestHolder.remove();
 * }
 * }</pre>
 */
@Slf4j
public class RequestHolder {

    /**
     * 使用 TransmittableThreadLocal 以安全支持线程池场景下的上下文透传
     */
    private static final ThreadLocal<AuthInfo> authInfoHolder = new TransmittableThreadLocal<>();
    private static final ThreadLocal<HttpServletRequest> requestHolder = new TransmittableThreadLocal<>();
    /**
     * 额外 header（虚拟请求头）。
     *
     * <p>用途：补齐真实 HttpServletRequest.getHeader(...) 不可用或不完整时的上下文透传。
     * 典型用于数据权限：X-Data-Scope/X-Company-Ids/X-Dept-Ids/X-Visible-Columns 等。
     */
    private static final ThreadLocal<Map<String, String>> extraHeadersHolder = new TransmittableThreadLocal<>();

    /**
     * 请求初始化标记：add() 时置为 true，remove() 时置为 false。
     * 用于在线程复用时检测上一个请求是否正确清理。
     */
    private static final ThreadLocal<Boolean> initialized = new TransmittableThreadLocal<>();

    /**
     * 添加认证信息
     */
    public static void add(AuthInfo authInfo) {
        if (initialized.get() != null && initialized.get()) {
            log.warn("RequestHolder -> 线程复用检测: 上一个请求的上下文未被清理，已强制清理");
            remove();
        }
        initialized.set(true);
        authInfoHolder.set(authInfo);
    }

    /**
     * 添加请求对象
     */
    public static void add(HttpServletRequest request) {
        requestHolder.set(request);
    }

    /**
     * 获取认证信息
     */
    public static AuthInfo getAuthInfo() {
        AuthInfo authInfo = authInfoHolder.get();
        if (authInfo == null) {
            log.warn("RequestHolder -> 当前线程 AuthInfo 为空，请检查拦截器配置");
        }
        return authInfo;
    }

    /**
     * 获取指定类型的认证信息 (泛型增强)
     */
    public static <T extends AuthInfo> T getAuthInfo(Class<T> clazz) {
        AuthInfo authInfo = getAuthInfo();
        if (clazz.isInstance(authInfo)) {
            return clazz.cast(authInfo);
        }
        return null;
    }

    /**
     * 获取ydsz统一认证上下文信息。
     */
    public static YdszAuthInfo getYdszAuthInfo() {
        return getAuthInfo(YdszAuthInfo.class);
    }

    /**
     * 获取当前请求对象
     */
    public static HttpServletRequest getCurrentRequest() {
        return requestHolder.get();
    }

    public static Map<String, String> getExtraHeaders() {
        Map<String, String> map = extraHeadersHolder.get();
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(map);
    }

    public static String getExtraHeader(String name) {
        if (name == null) {
            return null;
        }
        Map<String, String> map = extraHeadersHolder.get();
        if (map == null || map.isEmpty()) {
            return null;
        }
        return map.get(name);
    }

    public static void putExtraHeader(String name, String value) {
        if (name == null) {
            return;
        }
        if (value == null) {
            removeExtraHeader(name);
            return;
        }
        Map<String, String> map = extraHeadersHolder.get();
        if (map == null) {
            map = new HashMap<>();
            extraHeadersHolder.set(map);
        }
        map.put(name, value);
    }

    public static void removeExtraHeader(String name) {
        if (name == null) {
            return;
        }
        Map<String, String> map = extraHeadersHolder.get();
        if (map == null || map.isEmpty()) {
            return;
        }
        map.remove(name);
    }

    public static Map<String, String> snapshotExtraHeaders() {
        Map<String, String> map = extraHeadersHolder.get();
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }
        return new HashMap<>(map);
    }

    /**
     * 恢复 extra headers 快照。
     *
     * <p>用于 AOP/拦截器在本线程临时写入上下文后，确保调用链结束不泄露到后续逻辑。
     */
    public static void restoreExtraHeaders(Map<String, String> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            extraHeadersHolder.remove();
            return;
        }
        extraHeadersHolder.set(new HashMap<>(snapshot));
    }

    /**
     * 释放资源 (必须在请求结束时调用，防止内存泄漏)
     */
    public static void remove() {
        authInfoHolder.remove();
        requestHolder.remove();
        extraHeadersHolder.remove();
        initialized.remove();
    }
}
