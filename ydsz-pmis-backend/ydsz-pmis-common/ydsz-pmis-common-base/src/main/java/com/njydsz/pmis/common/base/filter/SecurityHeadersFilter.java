package com.njydsz.pmis.common.base.filter;

import com.njydsz.pmis.common.base.config.BaseSecurityHeadersProperties;
import com.njydsz.pmis.common.util.url.UrlPathUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 安全响应头过滤器
 *
 * <p>为 HTTP 响应添加安全相关的头部，防止常见 Web 安全威胁：
 * <ul>
 *   <li>X-Content-Type-Options: nosniff - 防止 MIME 类型嗅探</li>
 *   <li>X-Frame-Options: DENY - 防止点击劫持</li>
 *   <li>X-XSS-Protection: 1; mode=block - 启用浏览器 XSS 过滤</li>
 *   <li>Strict-Transport-Security - 强制 HTTPS</li>
 *   <li>Content-Security-Policy - 内容安全策略</li>
 *   <li>Referrer-Policy - 控制 Referer 头</li>
 * </ul>
 *
 * <p>所有头部值均通过 {@link BaseSecurityHeadersProperties} 配置，支持排除特定路径。
 *
 * <p>执行顺序：{@code Ordered.HIGHEST_PRECEDENCE + 20}，确保在业务逻辑之前执行。
 *
 * <p><b>与 safe 模块的关系：</b>
 * 本过滤器为 base 模块的兜底实现，仅在未引入 safe/web/app 模块时生效。
 * 当项目中存在 web/app 模块时，安全响应头由 safe 模块的 {@code SecurityHeaderFilter} 统一管理。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String HEADER_X_XSS_PROTECTION = "X-XSS-Protection";
    private static final String HEADER_STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    private static final String HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String HEADER_REFERRER_POLICY = "Referrer-Policy";

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

    /**
     * 添加安全响应头
     *
     * @param response HTTP 响应对象
     */
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
}
