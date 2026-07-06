package com.njydsz.pmis.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * SameSite Cookie 过滤器(CSRF 防御纵深)
 *
 * <p>对所有 Set-Cookie 响应头注入 {@code SameSite=Lax; Secure} 属性,
 * 防御跨站请求伪造(CSRF)与跨站信息泄漏。
 *
 * <p>仅在生产 profile 启用: dev 环境通常使用 HTTP,Secure 属性会导致 Cookie 不被浏览器保存。
 *
 * <p>SameSite 语义:
 * <ul>
 *   <li>Strict: 完全禁止跨站携带(过于严格,影响用户体验)</li>
 *   <li>Lax: 顶层导航的 GET 请求允许携带,其他跨站请求禁止(推荐默认值)</li>
 *   <li>None: 无限制(必须配合 Secure,不推荐)</li>
 * </ul>
 *
 * <p>顺序: 在 {@link XssFilter}(HIGHEST_PRECEDENCE + 1)之后执行。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class SameSiteCookieFilter extends OncePerRequestFilter {

    /** 注入的 SameSite 属性片段 */
    private static final String SAMESITE_ATTRIBUTES = "; SameSite=Lax; Secure";

    /** Set-Cookie 响应头名称 */
    private static final String HEADER_SET_COOKIE = "Set-Cookie";

    /** SameSite 属性关键字(用于判断是否已存在,避免重复注入) */
    private static final String SAMESITE_KEYWORD = "SameSite=";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        SameSiteResponseWrapper wrapped = new SameSiteResponseWrapper(response);
        chain.doFilter(request, wrapped);
    }

    /**
     * 对 Set-Cookie 头值注入 SameSite=Lax; Secure
     *
     * <p>若值已包含 SameSite 属性则不重复注入,避免冲突。
     *
     * @param cookieValue 原始 Set-Cookie 头值
     * @return 注入属性后的值
     */
    private static String applySameSite(String cookieValue) {
        if (cookieValue == null || cookieValue.isEmpty()) {
            return cookieValue;
        }
        // 已包含 SameSite 属性,不重复注入
        if (cookieValue.toLowerCase().contains(SAMESITE_KEYWORD.toLowerCase())) {
            return cookieValue;
        }
        return cookieValue + SAMESITE_ATTRIBUTES;
    }

    /**
     * 响应包装器: 拦截 Set-Cookie 头注入 SameSite 属性
     *
     * <p>同时覆写 addHeader / setHeader / addCookie 三条路径:
     * <ul>
     *   <li>addHeader / setHeader: 拦截直接通过头添加 Set-Cookie 的场景</li>
     *   <li>addCookie: 容器的 addCookie 会绕过包装器的 addHeader,
     *       因此手动序列化 Cookie 为 Set-Cookie 字符串后注入 SameSite,
     *       避免容器直接写底层导致属性丢失</li>
     * </ul>
     */
    private static class SameSiteResponseWrapper extends HttpServletResponseWrapper {

        SameSiteResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void addHeader(String name, String value) {
            if (HEADER_SET_COOKIE.equalsIgnoreCase(name)) {
                super.addHeader(name, applySameSite(value));
            } else {
                super.addHeader(name, value);
            }
        }

        @Override
        public void setHeader(String name, String value) {
            if (HEADER_SET_COOKIE.equalsIgnoreCase(name)) {
                super.setHeader(name, applySameSite(value));
            } else {
                super.setHeader(name, value);
            }
        }

        @Override
        public void addCookie(Cookie cookie) {
            // 不委托给容器的 addCookie(它会绕过本包装器的 addHeader),
            // 而是手动序列化 Cookie 为 Set-Cookie 头值,确保 SameSite 被注入
            String cookieHeader = serializeCookie(cookie);
            super.addHeader(HEADER_SET_COOKIE, applySameSite(cookieHeader));
        }

        /**
         * 将 Cookie 对象序列化为 Set-Cookie 头值
         *
         * <p>遵循 RFC 6265 格式: name=value; 属性对...
         *
         * @param cookie Cookie 对象
         * @return Set-Cookie 头值字符串
         */
        private static String serializeCookie(Cookie cookie) {
            StringBuilder sb = new StringBuilder();
            sb.append(cookie.getName()).append('=');
            String value = cookie.getValue();
            if (value != null) {
                sb.append(value);
            }
            String path = cookie.getPath();
            if (path != null && !path.isEmpty()) {
                sb.append("; Path=").append(path);
            }
            String domain = cookie.getDomain();
            if (domain != null && !domain.isEmpty()) {
                sb.append("; Domain=").append(domain);
            }
            int maxAge = cookie.getMaxAge();
            if (maxAge >= 0) {
                sb.append("; Max-Age=").append(maxAge);
            }
            if (cookie.getSecure()) {
                sb.append("; Secure");
            }
            if (cookie.isHttpOnly()) {
                sb.append("; HttpOnly");
            }
            return sb.toString();
        }
    }
}
