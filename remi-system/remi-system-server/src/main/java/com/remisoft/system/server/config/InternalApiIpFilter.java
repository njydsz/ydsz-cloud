package com.remisoft.system.server.config;

import java.io.IOException;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.remisoft.common.util.http.ServletUtils;

/**
 * 内部 API IP 白名单过滤器
 *
 * <p>对 {@code /api/internal/**} 路径的请求进行 IP 白名单校验，防御未授权的跨服务调用。
 * 当 {@code remi.system.internal-api-ip-whitelist} 配置为空时<b>不限制</b>（允许所有 IP 访问）。
 *
 * <p><b>为什么需要 IP 白名单：</b>
 * <ul>
 *   <li>内部 API（{@code /api/internal/**}）仅供后端服务通过 Feign 调用，<b>不对前端暴露</b></li>
 *   <li>仅依赖 Gateway 路由不足以防御：恶意用户可能通过猜测路径直接访问</li>
 *   <li>白名单从网络层限制「只有 K8s 集群内 / 已知办公网 IP」可访问，提升纵深防御</li>
 * </ul>
 *
 * <p><b>典型配置（生产）：</b>
 * <pre>
 * remi:
 *   system:
 *     internal-api-ip-whitelist:
 *       - 10.0.0.0/8      # K8s 集群 Pod 网段
 *       - 172.16.0.0/12   # Docker bridge 网段
 *       - 192.168.1.100   # 运维办公网
 * </pre>
 *
 * <p><b>客户端 IP 解析：</b>委托 {@link com.remisoft.common.util.http.ServletUtils#getClientIp(HttpServletRequest)}
 * 统一解析（含 X-Forwarded-For / X-Real-IP / RemoteAddr 多级回退与防伪造校验）。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see SystemProperties 系统模块配置属性
 * @see com.remisoft.system.server.controller.InternalApiController 内部 API Controller
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class InternalApiIpFilter {

    private final SystemProperties properties;

    /**
     * 注册 IP 白名单过滤器
     *
     * <p><b>配置要点：</b>
     * <ul>
     *   <li><b>URL 模式</b>：{@code /api/internal/*}（单星号通配一层，匹配 {@code /api/internal/xxx}）</li>
     *   <li><b>优先级</b>：{@link Ordered#HIGHEST_PRECEDENCE} + 10，确保在鉴权 Filter 之前执行</li>
     *   <li><b>Bean 名称</b>：{@code internalApiIpFilter}，便于在监控平台识别</li>
     * </ul>
     *
     * @return FilterRegistrationBean 实例
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

    /**
     * 执行 IP 白名单校验
     *
     * <p><b>流程：</b>
     * <ol>
     *   <li>读取白名单列表</li>
     *   <li>列表为空 → 直接放行（不限制）</li>
     *   <li>列表非空 → 解析客户端真实 IP，<b>不在白名单则返回 403</b></li>
     *   <li>通过则继续 Filter 链</li>
     * </ol>
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param chain    Filter 链
     * @throws IOException      IO 异常
     * @throws ServletException Servlet 异常
     */
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
     * 获取客户端真实 IP（考虑反向代理）
     *
     * <p>委托 {@link ServletUtils#getClientIp(HttpServletRequest)} 统一解析。
     * <p><b>注意：</b>当前白名单仅支持<b>精确匹配</b>，不支持 CIDR / 通配符。
     * 若需要网段匹配，建议引入 {@code IPAddressString} 等工具库扩展。
     *
     * @param request HTTP 请求
     * @return 客户端 IP 字符串
     */
    private String getClientIp(HttpServletRequest request) {
        return ServletUtils.getClientIp(request);
    }
}
