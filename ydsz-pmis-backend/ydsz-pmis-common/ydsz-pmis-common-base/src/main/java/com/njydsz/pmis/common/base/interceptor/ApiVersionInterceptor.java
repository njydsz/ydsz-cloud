package com.njydsz.pmis.common.base.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class ApiVersionInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        String path = req.getRequestURI();
        if (path != null && path.startsWith(q+slash+q+q+q+q+slash+q+q+q)) {
            String[] parts = path.split(q+slash+q);
            if (parts.length > 2) req.setAttribute(q+q.replace(q,q)+q+q, parts[2]);
        }
        return true;
    }
}