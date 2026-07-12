package com.njydsz.pmis.common.base.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求结束清理拦截器（Web/App 共享）
 *
 * <p>作为默认的 {@link HandlerInterceptor} 实现，定义在拦截器链的最末端，
 * 用于在请求完成后执行清理动作。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class BaseHttpInterceptor implements HandlerInterceptor {

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, @Nullable Exception ex) {
        // 默认空实现，业务方可通过继承此拦截器并覆盖此方法实现自定义清理
    }
}
