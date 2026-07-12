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

/**
 * 链路追踪 ID 过滤器（P1-6 桥接 Brave/Micrometer Tracing）
 *
 * <p>traceId 来源优先级：
 * <ol>
 *   <li>MDC 中已有 traceId（由 Brave {@code Slf4jCurrentTraceContext} 或本过滤器写入）</li>
 *   <li>请求头 {@code X-Trace-Id}（仅当请求携带 {@code X-Internal-Sig} 签名头时信任，即来自网关）</li>
 *   <li>{@link TraceIdUtil#generate()} 自动生成（Brave 优先，降级雪花算法）</li>
 * </ol>
 *
 * <p>与 Brave 的协作：
 * <ul>
 *   <li>Brave 的 {@code TracingFilter}（Order=TraceWebServletFilter.ORDER）会先于本过滤器执行，
 *       自动从 {@code traceparent}/{@code b3} header 解析并创建 span，写入 MDC</li>
 *   <li>本过滤器读取 MDC 即可获取 Brave 的 traceId，无需重复创建</li>
 *   <li>响应头 {@code X-Trace-Id} 始终写入，便于前端/运维关联</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /**
     * 从 MDC/Header 读取或生成 traceId，写入响应头，并在请求结束后清理
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param chain    过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            // 1) 优先使用 Brave 已写入 MDC 的 traceId
            String traceId = TraceIdUtil.get();
            // 2) 来自网关的请求（携带 X-Internal-Sig 签名头）：信任网关注入的 X-Trace-Id
            if ((traceId == null || traceId.isEmpty())
                    && request.getHeader(CommonConstants.HEADER_INTERNAL_SIG) != null) {
                traceId = request.getHeader(CommonConstants.HEADER_TRACE_ID);
            }
            // 3) 其他情况（客户端直连/无签名）：强制重新生成，防止伪造
            if (traceId == null || traceId.isEmpty()) {
                traceId = TraceIdUtil.generate();
            }
            TraceIdUtil.set(traceId);
            response.setHeader(CommonConstants.HEADER_TRACE_ID, traceId);
            chain.doFilter(request, response);
        } finally {
            TraceIdUtil.clear();
        }
    }
}
