package com.njydsz.gateway.filter;


import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.gateway.config.GatewayConstants;

import reactor.core.publisher.Mono;

/**
 * W3C Trace Context 注入过滤器（P3-13）
 *
 * <p>在网关入口注入 W3C 标准 Trace Context 头，使下游服务可通过
 * OpenTelemetry / SkyWalking / Jaeger / Zipkin 自动采集全链路追踪。
 *
 * <h3>W3C Trace Context 格式</h3>
 * <pre>
 *   traceparent: 00-{traceId(32hex)}-{spanId(16hex)}-{flags(2hex)}
 *   示例: 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01
 * </pre>
 *
 * <h3>兼容性</h3>
 * <ul>
 *   <li>保留现有 {@code X-Trace-Id} 头，向后兼容</li>
 *   <li>新增 {@code traceparent} 头，符合 W3C Recommendation</li>
 *   <li>下游服务若部署了 OTel Agent，会自动解析 traceparent</li>
 * </ul>
 *
 * <h3>执行顺序</h3>
 * <p>{@code HIGHEST_PRECEDENCE + 2}，在 {@link AccessLogGlobalFilter}(+1) 之后、
 * {@link IpBlacklistFilter}(+3) 之前，确保所有下游请求都携带 trace context。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Component
public class W3CTraceContextFilter implements GlobalFilter, Ordered {

    /** W3C Trace Context 版本 */
    private static final String TRACE_VERSION = "00";

    /** W3C Trace Context 采样标志（01=sampled） */
    private static final String TRACE_FLAGS = "01";

    /** traceparent 请求头名 */
    private static final String HEADER_TRACEPARENT = "traceparent";

    /** 分布式 ID 生成器（traceId/spanId 生成） */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /**
     * 构造 W3C 追踪上下文过滤器。
     *
     * @param snowflakeIdGenerator 分布式 ID 生成器
     */
    public W3CTraceContextFilter(SnowflakeIdGenerator snowflakeIdGenerator) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /**
     * 注入 W3C Trace Context，建立全链路追踪上下文。
     *
     * <p>遵循 W3C Trace Context 规范：优先继承上游 traceparent 的 traceId，
     * 仅当上游无 traceparent 或格式非法时才生成新的 traceId。
     * 每跳生成新的 spanId，确保 span 层级正确。
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
     * @return 完成信号 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String traceId;
        String spanId;
        String traceparent;

        // P0-3 修复：优先继承上游 traceparent（W3C 规范），不存在才生成新的
        String upstreamTraceparent = request.getHeaders().getFirst(HEADER_TRACEPARENT);
        if (isValidTraceparent(upstreamTraceparent)) {
            // 延续上游 traceId，生成新 spanId（每跳新 span）
            String[] parts = upstreamTraceparent.split("-");
            traceId = parts[1];
            spanId = generateSpanId();
            traceparent = TRACE_VERSION + "-" + traceId + "-" + spanId + "-" + TRACE_FLAGS;
        } else {
            // 上游无 traceparent，生成新的 traceId + spanId
            traceId = generateTraceId();
            spanId = generateSpanId();
            traceparent = TRACE_VERSION + "-" + traceId + "-" + spanId + "-" + TRACE_FLAGS;
        }

        // 注入 traceparent 和 X-Trace-Id（兼容）
        ServerHttpRequest mutated = request.mutate()
                .header(HEADER_TRACEPARENT, traceparent)
                .header(GatewayConstants.HEADER_TRACE_ID, traceId)
                .build();

        // 同时写入响应头
        exchange.getResponse().getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().add(HEADER_TRACEPARENT, traceparent);

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 校验 traceparent 格式是否符合 W3C 规范。
     *
     * <p>格式：00-{traceId(32hex)}-{spanId(16hex)}-{flags(2hex)}
     * traceId 和 spanId 不能全为 0。
     *
     * @param traceparent 待校验的 traceparent 头值
     * @return 合法返回 true
     */
    private boolean isValidTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isEmpty()) {
            return false;
        }
        String[] parts = traceparent.split("-");
        if (parts.length != 4) {
            return false;
        }
        // traceId: 32 hex，不能全 0
        if (parts[1].length() != 32 || !isHex(parts[1])
                || parts[1].equals("00000000000000000000000000000000")) {
            return false;
        }
        // spanId: 16 hex，不能全 0
        if (parts[2].length() != 16 || !isHex(parts[2])
                || parts[2].equals("0000000000000000")) {
            return false;
        }
        return true;
    }

    /**
     * 判断字符串是否全为十六进制字符。
     *
     * @param s 待检查字符串
     * @return 全 hex 返回 true
     */
    private boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 生成 W3C 格式的 traceId（32 位 hex，去除 UUID 的短横线）
     *
     * @return 32 位 hex 字符串
     */
    private String generateTraceId() {
        return String.valueOf(snowflakeIdGenerator.nextId()).replace("-", "");
    }

    /**
     * 生成 W3C 格式的 spanId（16 位 hex，取 UUID 前 16 位）
     *
     * @return 16 位 hex 字符串
     */
    private String generateSpanId() {
        return String.valueOf(snowflakeIdGenerator.nextId()).replace("-", "").substring(0, 16);
    }

    /**
     * P0-2: 过滤器执行顺序调整为 HIGHEST_PRECEDENCE + 0
     *
     * <p>作为最早期执行的过滤器，统一生成 traceId 并注入 traceparent 头。
     * 后续所有过滤器（AccessLog +1、IpBlacklist +3、Auth +10 等）直接读取已注入的 traceId，
     * 不再各自生成新的 traceId，避免 traceId 被覆盖导致链路追踪断裂。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
