package com.njydsz.pmis.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局 XSS 过滤器（P0-2：XSS 防护）
 *
 * <p>对请求参数和请求头中的值进行 HTML 转义，防止存储型/反射型 XSS 攻击。
 * 不处理 JSON 请求体（JSON 序列化层由 Jackson 负责），仅处理 query/form 参数和请求头。
 *
 * <p>注意：本过滤器在 TraceIdFilter 之后执行，确保 traceId 已写入 MDC。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class XssFilter extends OncePerRequestFilter {

    /** 需要过滤的请求头名称 */
    private static final String[] FILTERED_HEADERS = {
            "Referer", "User-Agent", "Origin", "X-Forwarded-For",
            "X-Requested-With", "X-Real-IP"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        chain.doFilter(new XssRequestWrapper(request), response);
    }

    /**
     * XSS 安全的请求包装器
     */
    private static class XssRequestWrapper extends jakarta.servlet.http.HttpServletRequestWrapper {

        XssRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return sanitize(value);
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) return null;
            String[] sanitized = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                sanitized[i] = sanitize(values[i]);
            }
            return sanitized;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> original = super.getParameterMap();
            Map<String, String[]> sanitized = new HashMap<>();
            for (Map.Entry<String, String[]> entry : original.entrySet()) {
                String[] values = entry.getValue();
                String[] clean = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    clean[i] = sanitize(values[i]);
                }
                sanitized.put(entry.getKey(), clean);
            }
            return Collections.unmodifiableMap(sanitized);
        }

        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            if (shouldFilterHeader(name)) {
                return sanitize(value);
            }
            return value;
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (shouldFilterHeader(name)) {
                return sanitizedHeaders(super.getHeaders(name));
            }
            return super.getHeaders(name);
        }

        private boolean shouldFilterHeader(String name) {
            if (name == null) return false;
            for (String h : FILTERED_HEADERS) {
                if (name.equalsIgnoreCase(h)) return true;
            }
            return false;
        }

        private Enumeration<String> sanitizedHeaders(Enumeration<String> headers) {
            if (headers == null) return null;
            List<String> list = new ArrayList<>();
            while (headers.hasMoreElements()) {
                list.add(sanitize(headers.nextElement()));
            }
            return Collections.enumeration(list);
        }

        /**
         * 对输入值进行 HTML 转义，防止 XSS 注入
         */
        private String sanitize(String value) {
            if (value == null || value.isEmpty()) {
                return value;
            }
            // 移除 <script> 标签
            String cleaned = value.replaceAll("(?i)<script.*?>.*?</script>", "");
            // 移除 on* 事件处理器
            cleaned = cleaned.replaceAll("(?i)\\s+on\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]*)", "");
            // 移除 javascript: 协议
            cleaned = cleaned.replaceAll("(?i)javascript\\s*:", "");
            // 移除 vbscript: 协议
            cleaned = cleaned.replaceAll("(?i)vbscript\\s*:", "");
            // HTML 转义
            cleaned = HtmlUtils.htmlEscape(cleaned);
            return cleaned;
        }
    }
}