package com.njydsz.pmis.common.base.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求结束清理拦截器（Web/App 共享）
 *
 * <p>作为默认的 {@link HandlerInterceptor} 实现，定义在拦截器链的最末端，
 * 用于在请求完成后执行清理动作。
 *
 * <p><b>职责说明：</b>
 * <ul>
 *   <li>{@link RequestHolder#remove()} 等 ThreadLocal 清理由
 *       {@code BaseAuthFilter.doFilterInternal()} 的 finally 块统一负责</li>
 *   <li>此类仅作为占位拦截器，可由业务方通过覆盖
 *       {@link #afterCompletion(HttpServletRequest, HttpServletResponse, Object, Exception)}
 *       扩展自定义清理逻辑</li>
 * </ul>
 *
 * <p>拦截器执行顺序参考 {@code docs/BASE_INTERCEPTOR_ORDER.md}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.5.0
 */
public class BaseHttpInterceptor implements HandlerInterceptor {

    /**
     * 请求完成后回调
     *
     * <p>默认空实现，业务方可通过继承此拦截器并覆盖此方法实现自定义清理。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @param ex       处理过程中抛出的异常（无异常时为 null）
     */
    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, @Nullable Exception ex) {
        // RequestHolder.remove() 由 BaseAuthFilter.doFilterInternal() 的 finally 块负责清理，此处不再重复调用
    }
}
