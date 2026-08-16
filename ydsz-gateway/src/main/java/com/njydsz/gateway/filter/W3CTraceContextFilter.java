package com.njydsz.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayFilterOrder;

/**
 * W3C Trace Context 注入过滤器（P3-13 + Q3 修复）
 *
 * <p>在网关入口注入 W3C 标准 Trace Context 头，使下游服务可通过 OpenTelemetry / SkyWalking / Jaeger / Zipkin
 * 自动采集全链路追踪。
 *
 * <h3>W3C Trace Context 格式</h3>
 *
 * <pre>
 *   traceparent: 00-{traceId(32hex)}-{spanId(16hex)}-{flags(2hex)}
 *   示例: 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01
 * </pre>
 *
 * <h3>Q3 修复说明</h3>
 *
 * <p>历史版本使用 {@code SnowflakeIdGenerator} 生成十进制 traceId/spanId， 不符合 W3C 要求的 32/16
 * 位十六进制格式，导致下游链路追踪系统无法解析。 本版本统一委托 {@link TraceIdGenerator} 的 W3C 专用方法 （{@code generateW3CTraceId()}
 * 32-hex + {@code generateW3CSpanId()} 16-hex）， 使用密码学级熵源确保跨组织传播时的唯一性保障。
 *
 * <h3>兼容性</h3>
 *
 * <ul>
 *   <li>保留现有 {@code X-Trace-Id} 头，向后兼容
 *   <li>新增 {@code traceparent} 头，符合 W3C Recommendation
 *   <li>下游服务若部署了 OTel Agent，会自动解析 traceparent
 * </ul>
 *
 * <h3>执行顺序</h3>
 *
 * <p>{@code HIGHEST_PRECEDENCE}，最早执行，确保所有下游请求都携带 trace context。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ydsz.gateway.filter",
    name = "w3c-trace",
    havingValue = "true",
    matchIfMissing = true)
public class W3CTraceContextFilter implements GlobalFilter, Ordered {

  /** traceparent 请求头名 */
  private static final String HEADER_TRACEPARENT = "traceparent";

  /**
   * 注入 W3C Trace Context，建立全链路追踪上下文。
   *
   * <p>遵循 W3C Trace Context 规范：优先继承上游 traceparent 的 traceId， 仅当上游无 traceparent 或格式非法时才生成新的 traceId。
   * 每跳生成新的 spanId，确保 span 层级正确。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @return 完成信号 Mono
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();

    String traceId;
    String spanId;

    // 优先继承上游 traceparent（W3C 规范），不存在或非法时生成新的 traceId
    String upstreamTraceparent = request.getHeaders().getFirst(HEADER_TRACEPARENT);
    TraceIdGenerator.ParsedTraceparent parsed =
        TraceIdGenerator.parseTraceparent(upstreamTraceparent);
    if (parsed != null) {
      // 延续上游 traceId，生成新 spanId（每跳新 span）
      traceId = parsed.traceId();
      spanId = TraceIdGenerator.generateW3CSpanId();
    } else {
      // 上游无合法 traceparent，使用 SecureRandom 生成新的 32 位 hex traceId（W3C 规范）
      traceId = TraceIdGenerator.generateW3CTraceId();
      spanId = TraceIdGenerator.generateW3CSpanId();
    }
    String traceparent = TraceIdGenerator.traceparentHeader(traceId, spanId);

    // 注入 traceparent 和 X-Trace-Id（兼容）
    ServerHttpRequest mutated =
        request
            .mutate()
            .header(HEADER_TRACEPARENT, traceparent)
            .header(GatewayConstants.HEADER_TRACE_ID, traceId)
            .build();

    // 同时写入响应头
    exchange.getResponse().getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);
    exchange.getResponse().getHeaders().add(HEADER_TRACEPARENT, traceparent);

    return chain.filter(exchange.mutate().request(mutated).build());
  }

  /**
   * 过滤器执行顺序：{@code HIGHEST_PRECEDENCE}。
   *
   * <p>作为最早期执行的过滤器，统一生成 traceId 并注入 traceparent 头。 后续所有过滤器（AccessLog +1、IpBlacklist +3、Auth +10
   * 等）直接读取已注入的 traceId， 不再各自生成新的 traceId，避免 traceId 被覆盖导致链路追踪断裂。
   *
   * @return 顺序值
   */
  @Override
  public int getOrder() {
    return GatewayFilterOrder.W3C_TRACE.getOrder();
  }
}
