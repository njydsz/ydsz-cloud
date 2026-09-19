package com.njydsz.common.feign.config;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Resilience4j 全局 CircuitBreaker 自动配置类（ydsz-common-feign 模块）。
 *
 * <p>基于 Resilience4j 2.4.0 注册全局 {@link CircuitBreakerRegistry}，提供统一的熔断器配置模板。
 * 下游 Feign 调用在启用熔断时，可按服务名从此注册表获取或创建对应的 CircuitBreaker。
 *
 * <p><b>生效条件：</b>
 *
 * <ul>
 *   <li>classpath 中存在 Resilience4j {@code CircuitBreakerConfig} 类
 *   <li>{@code ydsz.feign.circuit-breaker.enabled=true}（推荐）或 {@code ydsz.feign.resilience4j.enabled=true}（兼容）
 * </ul>
 *
 * <p>全局默认熔断配置：
 *
 * <ul>
 *   <li>failureRateThreshold: 50%
 *   <li>slowCallRateThreshold: 80%
 *   <li>slowCallDurationThreshold: 3s
 *   <li>waitDurationInOpenState: 10s
 *   <li>permittedNumberOfCallsInHalfOpenState: 10
 *   <li>slidingWindowSize: 20
 *   <li>minimumNumberOfCalls: 10
 * </ul>
 *
 * <p>配置路径：{@code ydsz.feign.circuit-breaker.*}（自 26.09.19 起统一入口）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FeignProperties.CircuitBreaker
 */
@Slf4j
@AutoConfiguration(after = FeignConfiguration.class)
@ConditionalOnClass(CircuitBreakerConfig.class)
@ConditionalOnProperty(
    prefix = "ydsz.feign.circuit-breaker",
    name = "enabled",
    havingValue = "true")
@EnableConfigurationProperties(FeignProperties.class)
public class FeignResilience4jAutoConfiguration {

  /**
   * 注册全局 Resilience4j CircuitBreakerRegistry。
   *
   * <p>使用 {@link FeignProperties.CircuitBreaker} 中的配置构建默认 CircuitBreakerConfig，
   * 作为所有 Feign 客户端熔断器的配置模板。运行时按服务名获取的 CircuitBreaker 继承此默认配置，
   * 可通过 {@code resilience4j.circuitbreaker.configs.<name>.*} 精细化覆盖。
   *
   * @param feignProperties Feign 配置属性
   * @return CircuitBreakerRegistry 实例
   */
  @Bean
  @ConditionalOnMissingBean(CircuitBreakerRegistry.class)
  public CircuitBreakerRegistry feignCircuitBreakerRegistry(FeignProperties feignProperties) {
    FeignProperties.CircuitBreaker config = feignProperties.getCircuitBreaker();
    CircuitBreakerConfig defaultConfig =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(config.getFailureRateThreshold())
            .slowCallRateThreshold(config.getSlowCallRateThreshold())
            .slowCallDurationThreshold(Duration.ofMillis(config.getSlowCallDurationMs()))
            .waitDurationInOpenState(Duration.ofMillis(config.getWaitDurationMs()))
            .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState())
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(config.getSlidingWindowSize())
            .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);
    log.info(
        "[FeignResilience4j] 全局 CircuitBreakerRegistry 已注册: failureRate={}, slowCallRate={}, "
            + "waitTime={}ms, slidingWindow={}, halfOpenPermits={}",
        config.getFailureRateThreshold(),
        config.getSlowCallRateThreshold(),
        config.getWaitDurationMs(),
        config.getSlidingWindowSize(),
        config.getPermittedNumberOfCallsInHalfOpenState());
    return registry;
  }

  /**
   * 注册 Resilience4jFeignPostProcessor（FeignClient 扫描并注入 CircuitBreaker 名称）。
   *
   * <p>仅在 Resilience4j 全局配置启用时注册，扫描所有 FeignClient Bean 并注入熔断器命名映射。
   *
   * @param feignProperties Feign 配置属性
   * @return Resilience4jFeignPostProcessor 实例
   */
  @Bean
  @ConditionalOnMissingBean(Resilience4jFeignPostProcessor.class)
  public Resilience4jFeignPostProcessor resilience4jFeignPostProcessor(FeignProperties feignProperties) {
    return new Resilience4jFeignPostProcessor(feignProperties);
  }
}
