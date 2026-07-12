package com.njydsz.pmis.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Content-Security-Policy 安全响应头过滤器（P2-11 安全闭环）
 *
 * <p>为所有 HTTP 响应注入以下安全头：
 * <ul>
 *   <li>Content-Security-Policy: 限制资源加载来源,防 XSS / 数据注入</li>
 *   <li>X-Content-Type-Options: nosniff,防 MIME 嗅探</li>
 *   <li>X-Frame-Options: DENY,防点击劫持</li>
 *   <li>Referrer-Policy: strict-origin-when-cross-origin</li>
 *   <li>Permissions-Policy: 限制浏览器 API 访问</li>
 * </ul>
 *
 * <p>CSP 策略说明:
 * <ul>
 *   <li>default-src 'self': 仅允许同源资源</li>
 *   <li>script-src 'self' 'unsafe-inline' 'unsafe-eval': 允许内联脚本(Vue 运行时需要)</li>
 *   <li>style-src 'self' 'unsafe-inline': 允许内联样式(Element Plus 动态注入)</li>
 *   <li>img-src 'self' data: blob: https: : 允许 data URI 和 blob 图片,以及 HTTPS 图片</li>
 *   <li>connect-src 'self' https: wss: : 允许 HTTPS API 和 WebSocket 连接</li>
 *   <li>font-src 'self' data: : 允许字体文件</li>
 *   <li>frame-ancestors 'none': 等价于 X-Frame-Options: DENY</li>
 *   <li>base-uri 'self': 限制 base 标签</li>
 *   <li>form-action 'self': 限制表单提交目标</li>
 * </ul>
 *
 * <p>生产环境可通过 {@code pmis.security.csp-policy} 配置覆盖默认策略。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class ContentSecurityPolicyFilter extends OncePerRequestFilter {

    /** 默认 CSP 策略 */
    private static final String DEFAULT_CSP =
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: blob: https:; " +
            "connect-src 'self' https: wss:; " +
            "font-src 'self' data:; " +
            "object-src 'none'; " +
            "media-src 'self'; " +
            "frame-src 'self'; " +
            "frame-ancestors 'none'; " +
            "base-uri 'self'; " +
            "form-action 'self'";

    /** 可配置的 CSP 策略（优先从配置读取） */
    @Value("${pmis.security.csp-policy:}")
    private String cspPolicy;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        // 注入 CSP 头
        String csp = (cspPolicy != null && !cspPolicy.isBlank()) ? cspPolicy : DEFAULT_CSP;
        response.setHeader("Content-Security-Policy", csp);

        // X-Content-Type-Options: 防 MIME 嗅探
        response.setHeader("X-Content-Type-Options", "nosniff");

        // X-Frame-Options: 防点击劫持（与 CSP frame-ancestors 双重防御）
        response.setHeader("X-Frame-Options", "DENY");

        // Referrer-Policy: 仅在同源请求中发送完整 Referrer
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions-Policy: 禁用不需要的浏览器 API
        response.setHeader("Permissions-Policy",
                "geolocation=(), microphone=(), camera=(), " +
                "payment=(), usb=(), magnetometer=(), gyroscope=(), accelerometer=()");

        // X-XSS-Protection: 旧版浏览器 XSS 过滤（已废弃但部分浏览器仍支持）
        response.setHeader("X-XSS-Protection", "1; mode=block");

        chain.doFilter(request, response);
    }
}
