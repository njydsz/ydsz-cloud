package com.njydsz.pmis.common.auth.filter;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.pmis.common.auth.config.AuthFilterConfiguration;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.auth.context.PermissionContextHolder;
import com.njydsz.pmis.common.core.constant.FilterIgnoreConstant;
import com.njydsz.pmis.common.util.auth.AuthInfo;
import com.njydsz.pmis.common.util.auth.RequestHolder;
import com.njydsz.pmis.common.util.url.UrlPathUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 认证过滤器抽象基类
 *
 * <p>提取 Web 端和 App 端认证过滤器的公共逻辑。</p>
 *
 * @author ydsz-pmis-team
 * 
 */
@Slf4j
public abstract class BaseAuthFilter extends OncePerRequestFilter {

    protected final String applicationName;
    protected final AuthFilterConfiguration authFilterConfiguration;

    public BaseAuthFilter(String applicationName, AuthFilterConfiguration authFilterConfiguration) {
        this.applicationName = applicationName;
        this.authFilterConfiguration = authFilterConfiguration;
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
            PermissionContextHolder.clear();
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
