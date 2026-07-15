package com.njydsz.pmis.common.safe.filter;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.pmis.common.safe.config.SecurityHeaderProperties;
import com.njydsz.pmis.common.util.url.UrlPathUtils;
/**
 * 安全响应头过滤器
 *
 * <p>为 HTTP 响应添加安全相关的头部，防止常见 Web 安全威胁：
 * <ul>
 *   <li>XSS 攻击防护</li>
 *   <li>MIME 类型嗅探防护</li>
 *   <li>点击劫持防护</li>
 *   <li>中间人攻击防护（HSTS）</li>
 *   <li>内容安全策略（CSP）</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <ul>
 *   <li>默认启用，自动注册到 Filter 链</li>
 *   <li>可通过配置排除特定路径</li>
 *   <li>建议尽可能早地执行，以确保安全头部被正确设置</li>
 * </ul>
 *
 * <p><b>安全头部说明：</b>
 * <pre>
 * X-Frame-Options: 防止点击劫持
 * X-Content-Type-Options: 防止 MIME 嗅探
 * X-XSS-Protection: XSS 过滤器（现代浏览器已支持 CSP）
 * Strict-Transport-Security: 强制 HTTPS
 * Content-Security-Policy: 内容安全策略
 * Referrer-Policy: Referer 头控制
 * Permissions-Policy: 浏览器功能策略
 * </pre>
 *
 * @since 1.0.0
 * 
 * @see SecurityHeaderProperties
 */
public class SecurityHeaderFilter implements Filter {

    private static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String HEADER_X_XSS_PROTECTION = "X-XSS-Protection";
    private static final String HEADER_STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    private static final String HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String HEADER_REFERRER_POLICY = "Referrer-Policy";
    private static final String HEADER_PERMISSIONS_POLICY = "Permissions-Policy";

    private final SecurityHeaderProperties properties;

    public SecurityHeaderFilter(SecurityHeaderProperties properties) {
        this.properties = properties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (isExcluded(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        addSecurityHeaders(httpResponse);
        chain.doFilter(request, response);
    }

    /**
     * 添加安全响应头
     *
     * @param response HTTP 响应对象
     */
    private void addSecurityHeaders(HttpServletResponse response) {
        addHeaderIfNotEmpty(response, HEADER_X_FRAME_OPTIONS, properties.getFrameOptions());
        addHeaderIfNotEmpty(response, HEADER_X_CONTENT_TYPE_OPTIONS, properties.getContentTypeOptions());
        addHeaderIfNotEmpty(response, HEADER_X_XSS_PROTECTION, properties.getXssProtection());
        addHeaderIfNotEmpty(response, HEADER_STRICT_TRANSPORT_SECURITY, properties.getHsts());
        addHeaderIfNotEmpty(response, HEADER_CONTENT_SECURITY_POLICY, properties.getCsp());
        addHeaderIfNotEmpty(response, HEADER_REFERRER_POLICY, properties.getReferrerPolicy());
        addHeaderIfNotEmpty(response, HEADER_PERMISSIONS_POLICY, properties.getPermissionsPolicy());
    }

    private void addHeaderIfNotEmpty(HttpServletResponse response, String headerName, String headerValue) {
        if (headerValue != null && !headerValue.trim().isEmpty()) {
            response.setHeader(headerName, headerValue);
        }
    }

    /**
     * 判断请求路径是否需要排除安全头部
     *
     * @param request HTTP 请求
     * @return 是否需要排除
     */
    private boolean isExcluded(HttpServletRequest request) {
        List<String> excludes = properties.getExcludes();
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        String servletPath = request.getServletPath();
        return UrlPathUtils.matchAny(excludes, servletPath);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
    }
}
