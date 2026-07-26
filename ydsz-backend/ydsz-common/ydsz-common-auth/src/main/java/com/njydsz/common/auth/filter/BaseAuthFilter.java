package com.njydsz.common.auth.filter;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.auth.config.AuthFilterConfiguration;
import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.auth.security.CsrfTokenValidator;
import com.njydsz.common.auth.security.RateLimiter;
import com.njydsz.common.core.constant.FilterIgnoreConstant;
import com.njydsz.common.util.auth.AuthInfo;
import com.njydsz.common.util.auth.RequestHolder;
import com.njydsz.common.util.url.UrlPathUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 认证过滤器抽象基类
 *
 * <p>提取 Web 端和 App 端认证过滤器的公共逻辑。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Slf4j
public abstract class BaseAuthFilter extends OncePerRequestFilter {

    protected final String applicationName;
    protected final AuthFilterConfiguration authFilterConfiguration;
    protected final RateLimiter rateLimiter;
    protected final CsrfTokenValidator csrfTokenValidator;

    public BaseAuthFilter(String applicationName, AuthFilterConfiguration authFilterConfiguration) {
        this(applicationName, authFilterConfiguration, null, null);
    }

    public BaseAuthFilter(String applicationName, AuthFilterConfiguration authFilterConfiguration,
                          RateLimiter rateLimiter, CsrfTokenValidator csrfTokenValidator) {
        this.applicationName = applicationName;
        this.authFilterConfiguration = authFilterConfiguration;
        this.rateLimiter = rateLimiter;
        this.csrfTokenValidator = csrfTokenValidator;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String servletPath = request.getServletPath();
        doPreAuth(request, response);
        if (shouldSkipAuth(request)) {
            log.debug("{}[跳过认证] 请求路径: {}", getLogPrefix(), servletPath);
            filterChain.doFilter(request, response);
            return;
        }
        // CSRF 校验（如果启用）
        if (csrfTokenValidator != null && !csrfTokenValidator.validate(request)) {
            log.warn("{}[CSRF 校验失败] 请求路径: {}", getLogPrefix(), servletPath);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF Token validation failed");
            return;
        }
        // 限流检查（如果启用）
        if (rateLimiter != null) {
            String clientIp = request.getRemoteAddr();
            if (!rateLimiter.tryAcquire(clientIp)) {
                log.warn("{}[限流] IP: {}, 请求路径: {}", getLogPrefix(), clientIp, servletPath);
                response.sendError(429, "Rate limit exceeded");
                return;
            }
        }
        long startTime = System.currentTimeMillis();
        AuthInfo authInfo = resolveAuthInfo(request, response);
        log.debug("{}请求路径: {}, 认证信息已写入上下文", getLogPrefix(), servletPath);
        RequestHolder.add(authInfo);
        RequestHolder.add(request);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 清理所有 ThreadLocal 变量，防止在异步线程池场景下的上下文泄漏
            RequestHolder.remove();
            AuthContext.clear();
            AuthContext.clear();
            doPostAuth(request, response, System.currentTimeMillis() - startTime);
        }
    }

    protected boolean shouldSkipAuth(HttpServletRequest request) {
        if (shouldSkipService()) {
            return true;
        }
        Set<String> ignoreUrl = authFilterConfiguration.getAllIgnoreUrls();
        return UrlPathUtils.isIgnoreUrl(ignoreUrl, request.getServletPath());
    }

    protected abstract AuthInfo resolveAuthInfo(HttpServletRequest request, HttpServletResponse response);

    protected abstract boolean shouldSkipService();

    protected abstract String getLogPrefix();

    protected void doPreAuth(HttpServletRequest request, HttpServletResponse response) {
    }

    protected void doPostAuth(HttpServletRequest request, HttpServletResponse response, long duration) {
    }

    protected boolean isServiceIgnored(String appName) {
        if (appName == null) {
            return false;
        }
        return FilterIgnoreConstant.getAuthFilterIgnoreServiceNames().contains(appName);
    }
}
