package com.njydsz.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * 网关 Resilience4j 限流兜底配置。
 *
 * <p>当 {@code ydsz.gateway.rate-limiter.enabled=true} 时，初始化全局限流参数绑定与 KeyResolver，
 * 作为粗粒度限流兜底（与已有的 RateLimitFilter 二维度令牌桶互补）。
 *
 * <p><b>生效条件：</b>
 *
 * <ul>
 *   <li>{@code ydsz.gateway.rate-limiter.enabled=true}</li>
 * </ul>
 *
 * <p><b>推荐使用方式：</b>在 Nacos shared-configs 或通过 {@code spring.profiles.include=resilience4j}
 * 引入 {@code application-resilience4j.yml} 中的 {@code default-filters.RequestRateLimiter} 配置，
 * 本配置类提供限流参数属性绑定与 KeyResolver Bean。
 *
 * @since 26.09.01
 * @author ydsz-team
 * @see GatewayRateLimiterProperties
 * @see UserKeyResolver
 * @see IpKeyResolver
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
    prefix = "ydsz.gateway.rate-limiter",
    name = "enabled",
    havingValue = "true")
@EnableConfigurationProperties(GatewayRateLimiterProperties.class)
public class GatewayRateLimiterConfig {

  /**
   * 注册限流 Key 解析器 Bean，根据配置的 {@code keyType} 返回对应的策略实现。
   *
   * <p>当 {@code keyType=IP} 时使用 IpKeyResolver，默认或 {@code keyType=USER} 时使用 UserKeyResolver。
   *
   * @param properties 限流配置属性
   * @param userKeyResolver 用户维度 Key 解析器
   * @param ipKeyResolver IP 维度 Key 解析器
   * @return KeyResolver 实例
   */
  @Bean
  public KeyResolver defaultKeyResolver(
      GatewayRateLimiterProperties properties,
      UserKeyResolver userKeyResolver,
      IpKeyResolver ipKeyResolver) {
    GatewayRateLimiterProperties.KeyType keyType =
        properties.getKeyType() != null
            ? properties.getKeyType()
            : GatewayRateLimiterProperties.KeyType.USER;
    KeyResolver resolver =
        switch (keyType) {
          case IP -> ipKeyResolver;
          case URL -> exchange -> Mono.just(
              "url:" + exchange.getRequest().getURI().getPath());
          default -> userKeyResolver;
        };
    log.info("[GatewayRateLimiter] 限流 Key 解析策略: keyType={}", keyType);
    return resolver;
  }
}
