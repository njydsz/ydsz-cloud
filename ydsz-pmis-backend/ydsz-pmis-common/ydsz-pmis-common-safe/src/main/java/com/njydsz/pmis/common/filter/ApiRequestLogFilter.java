package com.njydsz.pmis.common.filter;

import com.njydsz.pmis.common.constant.CommonConstants;
import com.njydsz.pmis.common.util.TraceIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * API 请求日志统一记录 Filter（P3-15 落地）。
 *
 * <p>对所有 HTTP 请求记录结构化日志，便于 ELK/Loki 采集和分析：
 * <ul>
 *   <li>请求方法、路径、查询参数</li>
 *   <li>响应状态码、耗时（毫秒）</li>
 *   <li>traceId、userId（从请求头提取）</li>
 *   <li>慢请求 WARN（超过 1s 的请求单独标记）</li>
 * </ul>
 *
 * <p>日志格式为 key=value 结构，Logstash JSON encoder 自动转为 JSON。
 * 配合 logback-spring.xml 中的 LOGSTASH_JSON appender 输出到 ELK。
 *
 * <p>排除路径：actuator、druid、swagger 等非业务请求不记录。
 *
 * @author ydsz-pmis-team
 * @since 1.3.1 (P3-15)
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRequestLogFilter extends OncePerRequestFilter {

    /** 不记录日志的路径前缀 */
    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
            "/actuator",
            "/druid",
            "/swagger",
            "/v3/api-docs",
            "/webjars",
            "/favicon.ico"
    );

    /** 慢请求阈值（毫秒） */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 1000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 跳过非业务路径
        if (isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            recordRequestLog(request, response, duration);
        }
    }

    /**
     * 记录请求日志。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param duration 请求耗时（毫秒）
     */
    private void recordRequestLog(HttpServletRequest request,
                                   HttpServletResponse response,
                                   long duration) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();
        int status = response.getStatus();
        String traceId = request.getHeader(CommonConstants.HEADER_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceIdUtil.get();
        }
        String userId = request.getHeader(CommonConstants.HEADER_USER_ID);
        String clientIp = getClientIp(request);

        // 构建结构化日志
        String logMessage = String.format(
                "API_REQUEST method=%s path=%s query=%s status=%d duration=%dms " +
                "traceId=%s userId=%s clientIp=%s",
                method, path,
                query != null ? query : "-",
                status, duration,
                traceId != null ? traceId : "-",
                userId != null ? userId : "-",
                clientIp
        );

        if (duration > SLOW_REQUEST_THRESHOLD_MS) {
            log.warn("[SLOW] {}", logMessage);
        } else if (status >= 500) {
            log.error("[ERROR] {}", logMessage);
        } else if (status >= 400) {
            log.warn("[WARN] {}", logMessage);
        } else {
            log.info("[OK] {}", logMessage);
        }
    }

    /**
     * 获取客户端真实 IP（穿透代理）。
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    /**
     * 判断路径是否在排除列表中。
     */
    private boolean isExcluded(String path) {
        if (path == null || path.isEmpty()) {
            return true;
        }
        for (String prefix : EXCLUDED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
