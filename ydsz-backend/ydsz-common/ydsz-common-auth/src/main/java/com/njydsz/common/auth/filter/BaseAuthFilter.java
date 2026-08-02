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
 * 认证过滤器抽象基类。
 *
 * <p>提取 Web 端和 App 端认证过滤器的公共逻辑，包括：
 * <ul>
 *   <li>请求路径排除判断（白名单路径直接放行）</li>
 *   <li>限流检查（通过 {@link RateLimiter} 防止暴力请求）</li>
 *   <li>CSRF Token 校验（通过 {@link CsrfTokenValidator}）</li>
 *   <li>认证上下文 {@link AuthContext} 初始化和清理</li>
 * </ul>
 *
 * <p>子类需实现 {@code doAuthFilter} 方法完成具体的 Token 解析和认证逻辑。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see OncePerRequestFilter
 * @see AuthFilterConfiguration
 * @see RateLimiter
 * @see CsrfTokenValidator
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

    /**
     * 判断当前请求是否应跳过认证。
     *
     * <p>先检查服务级跳过开关（{@link #shouldSkipService}），再比对合并后的忽略路径白名单
     * （通用/网关/自定义）。命中任一即视为无需认证、直接放行。
     * {@code request} 由过滤器保证非空，此处不再做空校验。</p>
     *
     * @param request 当前 HTTP 请求，非空
     * @return 是否跳过认证（{@code true} 表示放行）
     */
    protected boolean shouldSkipAuth(HttpServletRequest request) {
        if (shouldSkipService()) {
            return true;
        }
        Set<String> ignoreUrl = authFilterConfiguration.getAllIgnoreUrls();
        return UrlPathUtils.isIgnoreUrl(ignoreUrl, request.getServletPath());
    }

    /**
     * 解析并构造认证信息（由子类实现）。
     *
     * <p>子类需从请求中抽取凭证（如 Token 解析、签名验签）并构建 {@link AuthInfo}。
     * 返回实例随后被写入 {@link RequestHolder} 与 {@link AuthContext}，供下游拦截器与切面使用。
     * 解析失败应直接抛出认证相关异常以中断请求。</p>
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @return 解析得到的认证信息，不应为 {@code null}
     */
    protected abstract AuthInfo resolveAuthInfo(HttpServletRequest request, HttpServletResponse response);

    /**
     * 由子类决定当前服务是否整体跳过认证。
     *
     * <p>返回 {@code true} 时，本过滤器对所有请求直接放行（如纯静态资源服务或内部免鉴权环境）。
     * 子类通常结合配置开关或应用角色实现该判定。</p>
     *
     * @return 是否整体跳过认证
     */
    protected abstract boolean shouldSkipService();

    /**
     * 返回日志前缀（由子类提供）。
     *
     * <p>用于在过滤器各阶段日志前统一附加应用名/端点标识，便于在多服务混合日志中快速定位来源。
     * 子类应返回稳定的简短前缀字符串。</p>
     *
     * @return 日志前缀，不应为 {@code null}
     */
    protected abstract String getLogPrefix();

    /**
     * 认证前扩展钩子，默认空实现。
     *
     * <p>在路径白名单判定与 CSRF/限流之前调用；子类可重写以注入前置逻辑
     * （如请求改写、审计埋点、上下文预热）。无特殊需求时无需重写。</p>
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     */
    protected void doPreAuth(HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 认证后扩展钩子，默认空实现。
     *
     * <p>在过滤器 {@code finally} 块中、清理认证上下文之前调用；{@code duration} 为本请求认证耗时（毫秒）。
     * 子类可重写用于指标上报、审计或链路追踪。</p>
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param duration 本次认证耗时（毫秒）
     */
    protected void doPostAuth(HttpServletRequest request, HttpServletResponse response, long duration) {
    }

    /**
     * 判断指定应用名是否被配置为整体跳过认证。
     *
     * <p>匹配 {@link FilterIgnoreConstant#getAuthFilterIgnoreServiceNames()} 中的服务名白名单。
     * {@code appName} 为 {@code null} 时直接返回 {@code false}（不跳过）。</p>
     *
     * @param appName 应用名，允许为 {@code null}
     * @return 是否整体跳过认证
     */
    protected boolean isServiceIgnored(String appName) {
        if (appName == null) {
            return false;
        }
        return FilterIgnoreConstant.getAuthFilterIgnoreServiceNames().contains(appName);
    }
}
