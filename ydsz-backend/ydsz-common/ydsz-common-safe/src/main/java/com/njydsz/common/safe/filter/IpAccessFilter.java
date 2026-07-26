package com.njydsz.common.safe.filter;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.safe.alert.SecurityEvent;
import com.njydsz.common.safe.alert.SecurityEventPublisher;
import com.njydsz.common.safe.alert.SecurityEventType;
import com.njydsz.common.safe.ip.IpAccessService;
import com.njydsz.common.safe.util.ClientIpResolver;
import com.njydsz.common.util.url.UrlPathUtils;

/**
 * IP 黑白名单过滤器
 *
 * <p>基于 {@link IpAccessService} 实现 IP 访问控制，在请求进入安全过滤器链之前
 * 执行 IP 黑白名单检查。命中黑名单的 IP 返回 403 Forbidden。
 *
 * <p><b>过滤器优先级：</b>IP 访问控制过滤器优先级最高（HIGHEST_PRECEDENCE），
 * 确保恶意 IP 在进入 XSS/SQL 注入/限流等过滤器之前就被拦截。
 *
 * <p><b>降级策略：</b>Redis 异常时不阻断服务（fail-open），仅记录日志。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see IpAccessService
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IpAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IpAccessFilter.class);

    private final IpAccessService ipAccessService;
    private final SecurityEventPublisher eventPublisher;
    private final List<String> excludes;

    /**
     * @param ipAccessService IP 访问控制服务
     * @param eventPublisher   安全事件发布器
     * @param excludes         排除路径列表
     */
    public IpAccessFilter(IpAccessService ipAccessService,
                           SecurityEventPublisher eventPublisher,
                           List<String> excludes) {
        this.ipAccessService = ipAccessService;
        this.eventPublisher = eventPublisher;
        this.excludes = excludes != null ? excludes : new java.util.ArrayList<>();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = ClientIpResolver.getClientIp(request);

        try {
            if (!ipAccessService.isAllowed(clientIp)) {
                log.warn("【IP访问控制】IP 被拒绝 | ip={}, uri={}", clientIp, request.getRequestURI());

                if (eventPublisher != null) {
                    SecurityEvent event = new SecurityEvent(
                            SecurityEventType.ILLEGAL_ACCESS,
                            request.getRequestURI(),
                            clientIp,
                            request.getHeader("User-Agent"),
                            "IP blocked by access control",
                            SecurityEvent.Severity.HIGH
                    );
                    eventPublisher.publish(event);
                }

                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"A04053\",\"msg\":\"访问被拒绝\"}");
                return;
            }
        } catch (Exception e) {
            log.warn("【IP访问控制】检查异常，放行请求 | ip={}, error={}", clientIp, e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExcluded(HttpServletRequest request) {
        if (excludes.isEmpty()) {
            return false;
        }
        return UrlPathUtils.matchAny(excludes, request.getServletPath());
    }

}
