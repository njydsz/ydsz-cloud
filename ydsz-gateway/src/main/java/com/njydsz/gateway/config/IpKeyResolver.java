package com.njydsz.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 基于客户端 IP 的限流 Key 解析器。
 *
 * <p>从 {@link ServerHttpRequest} 的 remote address 中提取客户端 IP 作为限流 Key，
 * 用于按 IP 维度控制请求频率。
 *
 * <p>Bean 名称 {@code ipKeyResolver}，与 {@code application-resilience4j.yml} 中的 {@code
 * key-resolver: "#{@ipKeyResolver}"} 对应。
 *
 * <p>使用场景：防御单 IP 暴力请求或爬虫。   *
 * @since 26.09.01
 * @author ydsz-team
 * @see KeyResolver
 * @see UserKeyResolver
 */
@Slf4j
@Component("ipKeyResolver")
public class IpKeyResolver implements KeyResolver {

  /**
   * 解析限流 Key。
   *
   * <p>从请求 remote address 提取 IPv4/IPv6 地址字符串。当无法获取时返回 {@code unknown}，
   * 不抛出异常，避免限流组件因异常阻塞请求。
   *
   * @param exchange 服务器 Web 交换上下文
   * @return 限流 Key 的 Mono
   */
  @Override
  public Mono<String> resolve(ServerWebExchange exchange) {
    String clientIp = GatewayIpUtils.getClientIp(exchange.getRequest());
    return Mono.just("ip:" + clientIp);
  }
}
