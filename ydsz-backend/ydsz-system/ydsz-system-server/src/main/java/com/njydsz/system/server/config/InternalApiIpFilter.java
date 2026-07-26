package com.njydsz.system.server.config;

import java.io.IOException;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 内部 API IP 白名单过滤器。
 *
 * <p>对 {@code /api/internal/**} 路径的请求进行 IP 白名单校验。
 * 当 {@code ydsz.system.internal-api-ip-whitelist} 配置为空时不限制（允许所有 IP）。
 *
 * @author ydsz-team
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class InternalApiIpFilter {

    private final SystemProperties properties;

    /**
     * 注册 IP 白名单过滤器。
     *
     * @return FilterRegistrationBean
     */
    @Bean
    public FilterRegistrationBean<Filter> internalApiIpFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter((request, response, chain) -> doFilter(
                (HttpServletRequest) request, (HttpServletResponse) response, chain));
        registration.addUrlPatterns("/api/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("internalApiIpFilter");
        return registration;
    }

    private void doFilter(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        List<String> whitelist = properties.getInternalApiIpWhitelist();
        if (whitelist != null && !whitelist.isEmpty()) {
            String clientIp = getClientIp(request);
            if (!whitelist.contains(clientIp)) {
                log.warn("Internal API access denied for IP: {}", clientIp);
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Access denied: IP not in whitelist");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 获取客户端真实 IP（考虑反向代理）。
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            // X-Forwarded-For 可能包含多个 IP，取第一个（最原始的客户端 IP）
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
