package com.njydsz.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 基于用户标识的限流 Key 解析器。
 *
 * <p>从请求头 {@code X-User-Id} 中提取当前用户标识作为限流 Key。
 * 未登录用户使用 {@code anonymous} 作为 Key（按 IP 限流效果，但会共享配额）。
 *
 * <p>Bean 名称 {@code userKeyResolver}，与 {@code application-resilience4j.yml} 中的 {@code
 * key-resolver: "#{@userKeyResolver}"} 对应。
 *
 * <p>使用场景：需按用户维度控制请求频率，防止单用户过度调用。
 *
 * @since 26.09.01
 * @author ydsz-team
 * @see KeyResolver
 * @see IpKeyResolver
 */
@Slf4j
@Component("userKeyResolver")
public class UserKeyResolver implements KeyResolver {

  /** 匿名用户标识 */
  private static final String ANONYMOUS = "anonymous";

  /**
   * 解析限流 Key。   *
   * <p>优先取请求头 {@code X-User-Id}，未登录时返回 {@code anonymous}。   *
   * @param exchange 服务器 Web 交换上下文
   * @return 限流 Key 的 Mono
   */
  @Override
  public Mono<String> resolve(org.springframework.web.server.ServerWebExchange exchange) {
    ServerHttpRequest request = exchange.getRequest();
    String userId = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);
    if (userId != null && !userId.isBlank()) {
      return Mono.just("user:" + userId);
    }
    return Mono.just(ANONYMOUS);
  }
}
