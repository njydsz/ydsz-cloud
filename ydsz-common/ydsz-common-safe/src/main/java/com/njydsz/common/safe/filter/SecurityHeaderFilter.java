package com.njydsz.common.safe.filter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.common.safe.config.SecurityHeaderConfigurer;
import com.njydsz.common.safe.config.SecurityHeaderProperties;
import com.njydsz.common.util.http.UrlPathUtils;
/**
 * 安全响应头过滤器（P1-1：委托 SecurityHeaderConfigurer 计算头策略）。
 *
 * <p>为 HTTP 响应添加安全相关的头部，防止常见 Web 安全威胁：
 * <ul>
 *   <li>XSS 攻击防护</li>
 *   <li>MIME 类型嗅探防护</li>
 *   <li>点击劫持防护</li>
 *   <li>中间人攻击防护（HSTS）</li>
 *   <li>内容安全策略（CSP）</li>
 *   <li>跨源隔离（COOP/COEP/CORP）</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <ul>
 *   <li>默认启用，自动注册到 Filter 链</li>
 *   <li>可通过配置排除特定路径</li>
 *   <li>建议尽可能早地执行，以确保安全头部被正确设置</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see SecurityHeaderProperties
 * @see SecurityHeaderConfigurer
 */
public class SecurityHeaderFilter implements Filter {

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
     * 添加安全响应头。
     *
     * <p>P1-1：委托 {@link SecurityHeaderConfigurer#computeHeaders(SecurityHeaderProperties)} 计算头策略，
     * 确保 Servlet 栈与 WebFlux 栈（Gateway）使用同一套逻辑。
     *
     * @param response HTTP 响应对象
     */
    private void addSecurityHeaders(HttpServletResponse response) {
        Map<String, String> headers = SecurityHeaderConfigurer.computeHeaders(properties);
        headers.forEach(response::setHeader);
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
