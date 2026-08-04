package com.remisoft.common.safe.filter;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import com.remisoft.common.safe.config.SecurityHeaderProperties;
import com.remisoft.common.util.http.UrlPathUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 安全响应头过滤器（Web / App 共享抽象基类）。
 *
 * <p>继承 {@link OncePerRequestFilter}，在 HTTP 响应中注入标准安全响应头，
 * 防止常见的 Web 安全攻击（XSS、点击劫持、MIME 嗅探等）。
 *
 * <h3>注入的安全头</h3>
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff}：禁止浏览器 MIME 嗅探</li>
 *   <li>{@code X-Frame-Options: DENY/SAMEORIGIN}：防止点击劫持（可配置）</li>
 *   <li>{@code X-XSS-Protection: 1; mode=block}：启用浏览器 XSS 过滤器</li>
 *   <li>{@code Strict-Transport-Security}：强制 HTTPS（仅 HTTPS 响应）</li>
 *   <li>{@code Content-Security-Policy}：内容安全策略（可配置）</li>
 * </ul>
 *
 * <h3>配置</h3>
 * <p>通过 {@link SecurityHeaderProperties} 配置开关、排除路径和各安全头值。
 *
 * @author remi-team
 * @since 1.0.0
 * @see SecurityHeaderProperties
 * @see OncePerRequestFilter
 */
@Slf4j
@RequiredArgsConstructor
public class BaseSecurityHeaderFilter extends OncePerRequestFilter {

    private final SecurityHeaderProperties properties;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled() || isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        addSecurityHeaders(response);
        filterChain.doFilter(request, response);
        log.debug("安全响应头已添加到请求 {} 的响应中", request.getRequestURI());
    }

    private void addSecurityHeaders(HttpServletResponse response) {
        addHeaderIfNotEmpty(response, "X-Frame-Options", properties.getFrameOptions());
        addHeaderIfNotEmpty(response, "X-Content-Type-Options", properties.getContentTypeOptions());
        addHeaderIfNotEmpty(response, "X-XSS-Protection", properties.getXssProtection());
        addHeaderIfNotEmpty(response, "Strict-Transport-Security", properties.getHsts());
        addHeaderIfNotEmpty(response, "Content-Security-Policy", properties.getCsp());
        addHeaderIfNotEmpty(response, "Referrer-Policy", properties.getReferrerPolicy());
        addHeaderIfNotEmpty(response, "Permissions-Policy", properties.getPermissionsPolicy());
    }

    private void addHeaderIfNotEmpty(HttpServletResponse response, String headerName, String headerValue) {
        if (headerValue != null && !headerValue.trim().isEmpty()) {
            response.setHeader(headerName, headerValue);
        }
    }

    private boolean isExcluded(HttpServletRequest request) {
        List<String> excludes = properties.getExcludes();
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        String servletPath = request.getServletPath();
        return UrlPathUtils.matchAny(excludes, servletPath);
    }
}
