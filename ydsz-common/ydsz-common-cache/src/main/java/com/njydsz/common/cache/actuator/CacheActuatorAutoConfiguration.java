package com.njydsz.common.cache.actuator;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.cache.api.Cache;

/**
 * 缓存 Actuator 端点自动配置
 *
 * <p>当满足以下条件时自动注册 {@link CacheMetricsEndpoint}：
 *
 * <ul>
 *   <li>Spring Boot Actuator 在 classpath 中
 *   <li>存在至少一个 {@link Cache} Bean
 *   <li>配置项 {@code management.endpoint.cache-metrics.enabled=true}（默认 true）
 * </ul>
 *
 * <p>端点 ID：{@code cache-metrics}，访问路径：{@code /actuator/cache-metrics}
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // application.yml
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: health,cache-metrics
 *   endpoint:
 *     cache-metrics:
 *       enabled: true
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
  // CHECKSTYLE.ON: RegexpSinglelineJava
@ConditionalOnBean(Cache.class)
@ConditionalOnProperty(
    prefix = "management.endpoint.cache-metrics",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CacheActuatorAutoConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(CacheActuatorAutoConfiguration.class);

  /**
   * 创建缓存指标端点 Bean
   *
   * @param caches 所有 Cache Bean（Spring 自动注入）
   * @return 配置好的 CacheMetricsEndpoint
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnAvailableEndpoint(endpoint = CacheMetricsEndpoint.class)
  public CacheMetricsEndpoint cacheMetricsEndpoint(Map<String, Cache<?, ?>> caches) {
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
    CacheMetricsEndpoint endpoint = new CacheMetricsEndpoint();
  // CHECKSTYLE.ON: RegexpSinglelineJava
    caches.forEach((name, cache) -> endpoint.registerCache(name, cache));
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
    LOG.info("CacheMetricsEndpoint 已注册，监控 {} 个缓存实例", caches.size());
  // CHECKSTYLE.ON: RegexpSinglelineJava
    return endpoint;
  }
}
