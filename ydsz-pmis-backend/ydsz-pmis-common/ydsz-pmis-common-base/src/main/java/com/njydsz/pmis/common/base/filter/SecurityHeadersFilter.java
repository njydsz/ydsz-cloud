package com.njydsz.pmis.common.base.filter;

import com.njydsz.pmis.common.base.config.BaseSecurityHeadersProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 安全响应头过滤器
 *
 * <p>为 HTTP 响应添加安全相关的头部，防止常见 Web 安全威胁。
 * <ul>
 *   <li>X-Content-Type-Options: nosniff - 防止 MIME 类型嗅探</li>
 *   <li>X-Frame-Options: DENY - 防止点击劫持</li>
 *   <li>X-XSS-Protection: 1; mode=block - 启用浏览器 XSS 过滤</li>
 *   <li>Strict-Transport-Security - 强制 HTTPS</li>
 *   <li>Content-Security-Policy - 内容安全策略</li>
 *   <li>Referrer-Policy - 控制 Referer 头</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String HEADER_X_XSS_PROTECTION = "X-XSS-Protection";
    private static final String HEADER_STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    private static final String HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String HEADER_REFERRER_POLICY = "Referrer-Policy";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final BaseSecurityHeadersProperties properties;

    /**
     * 构造安全响应头过滤器
     *
     * @param properties 安全头部配置属性
     */
    public SecurityHeadersFilter(BaseSecurityHeadersProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!isExcluded(request)) {
            addSecurityHeaders(response);
        }
        filterChain.doFilter(request, response);
    }

    private void addSecurityHeaders(HttpServletResponse response) {
        addHeaderIfNotEmpty(response, HEADER_X_CONTENT_TYPE_OPTIONS, properties.getContentTypeOptions());
        addHeaderIfNotEmpty(response, HEADER_X_FRAME_OPTIONS, properties.getFrameOptions());
        addHeaderIfNotEmpty(response, HEADER_X_XSS_PROTECTION, properties.getXssProtection());
        addHeaderIfNotEmpty(response, HEADER_STRICT_TRANSPORT_SECURITY, properties.getHsts());
        addHeaderIfNotEmpty(response, HEADER_CONTENT_SECURITY_POLICY, properties.getCsp());
        addHeaderIfNotEmpty(response, HEADER_REFERRER_POLICY, properties.getReferrerPolicy());
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
        for (String pattern : excludes) {
            if (PATH_MATCHER.match(pattern, servletPath)) {
                return true;
            }
        }
        return false;
    }
}
