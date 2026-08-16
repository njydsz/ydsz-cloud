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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.safe.ip.IpAccessService;
import com.njydsz.common.safe.util.ClientIpResolver;

/**
 * 内部 API IP 白名单过滤器（P0-3 升级：委托 ydsz-common-safe 的 {@link IpAccessService}）。
 *
 * <p>对 {@code /api/internal/**} 路径的请求进行 IP 白名单校验，防御未授权的跨服务调用。
 * 当 {@code ydsz.system.internal-api-ip-whitelist} 配置为空时<b>不限制</b>（允许所有 IP 访问）。
 *
 * <p><b>升级说明（P0-3）：</b>
 * <ul>
 *   <li>原实现使用自建精确匹配白名单，不支持 CIDR 网段</li>
 *   <li>现委托 {@link IpAccessService}（common-safe 模块），获得 CIDR 网段匹配能力</li>
 *   <li>白名单通过 {@code ydsz.safe.ip-access.static-whitelist} 配置（支持 CIDR 格式）</li>
 *   <li>同时具备 Redis 持久化能力（动态白名单运维接口），满足集群环境一致性</li>
 * </ul>
 *
 * <p><b>典型配置（生产）：</b>
 * <pre>
 * ydsz:
 *   safe:
 *     ip-access:
 *       enabled: true
 *       mode: WHITELIST
 *       static-whitelist:
 *         - 10.0.0.0/8      # K8s 集群 Pod 网段
 *         - 172.16.0.0/12   # Docker bridge 网段
 *         - 192.168.1.100   # 运维办公网（单个 IP 也支持）
 * </pre>
 *
 * <p><b>客户端 IP 解析：</b>委托 {@link ClientIpResolver#getClientIp(HttpServletRequest)}
 * 统一解析（含 X-Forwarded-For / RemoteAddr 多级回退与防伪造校验）。
 *
 * <p><b>过滤器优先级：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 10，确保在鉴权 Filter 之前执行。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see IpAccessService IP 访问控制服务
 * @see ClientIpResolver 客户端 IP 解析工具
 * @see com.njydsz.system.server.controller.InternalApiController 内部 API Controller
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class InternalApiIpFilter {

    private final IpAccessService ipAccessService;
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
     *   <li>读取旧版白名单配置（兼容期），若配置了旧版白名单则使用精确匹配降级逻辑</li>
     *   <li>未配置旧版白名单时，委托 {@link IpAccessService#isAllowed(String)} 执行校验</li>
     *   <li>校验不通过则返回 403</li>
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
        // 兼容期：如果旧版白名单配置了精确 IP，使用降级匹配（平滑迁移）
        List<String> legacyWhitelist = properties.getInternalApiIpWhitelist();
        String clientIp = ClientIpResolver.getClientIp(request);

        if (legacyWhitelist != null && !legacyWhitelist.isEmpty()) {
            // 旧版精确匹配逻辑（迁移兼容期保留，建议尽快升级到 IpAccessService）
            if (!legacyWhitelist.contains(clientIp)) {
                log.warn("Internal API access denied for IP: {} (legacy whitelist)", clientIp);
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Access denied: IP not in whitelist");
                return;
            }
        } else {
            // P0-3: 委托 common-safe IpAccessService（支持 CIDR + Redis 动态白名单）
            if (!ipAccessService.isAllowed(clientIp)) {
                log.warn("Internal API access denied for IP: {} (IpAccessService)", clientIp);
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Access denied: IP not in whitelist");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
