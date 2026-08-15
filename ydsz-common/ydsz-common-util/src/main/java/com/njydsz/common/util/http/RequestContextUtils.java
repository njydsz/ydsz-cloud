package com.njydsz.common.util.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Spring RequestContext 工具类
 *
 * <p>封装从 Spring {@link RequestContextHolder} 获取当前 HTTP 请求/响应的能力，
 * 统一处理 Servlet 环境检测与异常降级。
 *
 * <p>仅在 Spring Web MVC 环境（存在 RequestContextHolder 绑定时）可用，
 * 其他环境返回 {@code null}。
 *
 * @author ydsz-team
 * @since 2.0.0
 */
public final class RequestContextUtils {

    private RequestContextUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 获取当前线程绑定的 HttpServletRequest。
     *
     * <p>通过 Spring RequestContextHolder 获取，仅在 Servlet 请求上下文中可用。
     * 非 Web 环境或未绑定请求上下文时返回 null。
     *
     * @return 当前 HTTP 请求，或 null
     */
    public static HttpServletRequest getRequest() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        } catch (IllegalStateException | NullPointerException e) {
            // IllegalStateException: RequestContextHolder 未绑定；NullPointerException: 无请求上下文
            return null;
        }
    }

    /**
     * 获取当前线程绑定的 HttpServletResponse。
     *
     * <p>通过 Spring RequestContextHolder 获取，仅在 Servlet 请求上下文中可用。
     * 非 Web 环境或未绑定请求上下文时返回 null。
     *
     * @return 当前 HTTP 响应，或 null
     */
    public static HttpServletResponse getResponse() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
        } catch (IllegalStateException | NullPointerException e) {
            return null;
        }
    }

    /**
     * 判断当前线程是否绑定 Servlet 请求上下文。
     *
     * @return true 表示当前处于 Servlet 请求上下文中
     */
    public static boolean hasRequestContext() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes;
    }
}
