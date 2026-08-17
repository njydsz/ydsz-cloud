package com.njydsz.gateway.filter;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.config.GatewayMetrics;
import com.njydsz.gateway.loadbalancer.GrayLoadBalancer;

/**
 * 灰度路由响应头过滤器（P2-E4 可观测性）。
 *
 * <p>在响应返回客户端前，读取负载均衡选中的实例 metadata，向响应头注入 {@code X-Gray-Hit} 标记，
 * 便于客户端和运维监控区分灰度 / 稳定路由结果：
 *
 * <ul>
 *   <li>{@code X-Gray-Hit: true} — 请求被路由到灰度实例（metadata.version=gray）
 *   <li>{@code X-Gray-Hit: false} — 请求被路由到稳定实例
 * </ul>
 *
 * <p>执行顺序：{@code HIGHEST_PRECEDENCE + 150}，在所有业务过滤器之后、API 版本头之前执行，
 * 确保在响应提交前写入响应头。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.gateway.filter",
    name = "gray-loadbalancer",
    havingValue = "true",
    matchIfMissing = true)
public class GrayResponseHeaderFilter implements GlobalFilter, Ordered {

  /** 网关指标组件（可选） */
  private final ObjectProvider<GatewayMetrics> gatewayMetricsProvider;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return chain.filter(exchange)
        .then(Mono.fromRunnable(() -> addGrayHitHeader(exchange)));
  }

  /**
   * 向响应头注入 X-Gray-Hit 标记。
   *
   * <p>从 {@link ServerWebExchangeUtils#GATEWAY_LOADBALANCER_RESPONSE_ATTR} 读取负载均衡选中的
   * {@link Response}，检查其 ServiceInstance 的 metadata.version 是否为 "gray"， 将结果写入响应头。
   * 若无法获取选中实例（如直连 URL 路由）则跳过。
   *
   * @param exchange 服务器 Web 交换上下文
   */
  private void addGrayHitHeader(ServerWebExchange exchange) {
    ServerHttpResponse response = exchange.getResponse();
    // 响应已提交则无法修改响应头
    if (response.isCommitted()) {
      return;
    }

    Object lbResponse =
        exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR);
    if (lbResponse instanceof Response<?> loadBalancerResponse
        && loadBalancerResponse.getServer() instanceof ServiceInstance instance) {
      Map<String, String> metadata = instance.getMetadata();
      boolean isGray = metadata != null && "gray".equalsIgnoreCase(metadata.get("version"));
      response.getHeaders().add("X-Gray-Hit", String.valueOf(isGray));
      // E4: 记录灰度路由 Prometheus 指标
      GatewayMetrics metrics = gatewayMetricsProvider.getIfAvailable();
      if (metrics != null) {
        metrics.incrementGrayHit(isGray);
      }
      if (log.isDebugEnabled()) {
        log.debug("[GrayHeader] 响应头注入 X-Gray-Hit={} instance={}", isGray, instance.getInstanceId());
      }
    }
  }

  /**
   * 过滤器顺序：响应阶段（业务过滤器之后、API 版本头之前）。
   *
   * @return 顺序值
   */
  @Override
  public int getOrder() {
    return GatewayFilterOrder.GRAY_RESPONSE_HEADER.getOrder();
  }
}
