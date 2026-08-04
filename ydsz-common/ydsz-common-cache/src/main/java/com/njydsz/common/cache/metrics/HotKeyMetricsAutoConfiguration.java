package com.njydsz.common.cache.metrics;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.cache.api.Cache;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * HotKeyMetrics 自动配置。
 *
 * <p>当满足以下条件时激活：
 * <ul>
 *   <li>ydsz.cache.hot-key-tracking.enabled=true（默认 false，需显式开启）</li>
 *   <li>Micrometer MeterRegistry Bean 可用</li>
 *   <li>存在目标缓存 Bean</li>
 * </ul>
 *
 * <p>配置示例：
 * <pre>{@code
 * ydsz:
 *   cache:
 *     hot-key-tracking:
 *       enabled: true
 *       top-k: 10
 *       snapshot-interval-seconds: 30
 *       max-local-keys: 10000
 *       cache-name-patterns: user_cache,product_cache
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(
    prefix = "ydsz.cache.hot-key-tracking",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
@EnableConfigurationProperties(HotKeyMetricsProperties.class)
public class HotKeyMetricsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public HotKeyMetricsRegistry hotKeyMetricsRegistry(MeterRegistry meterRegistry,
      HotKeyMetricsProperties properties) {
    return new HotKeyMetricsRegistry(meterRegistry, properties);
  }

  /**
   * 桥接器：按需收集已有 Cache Bean 并创建 HotKeyMetrics。
   */
  @Bean
  @ConditionalOnBean({Cache.class, HotKeyMetricsRegistry.class})
  public DefaultHotKeyCacheBinder defaultHotKeyCacheBinder(HotKeyMetricsRegistry registry,
      ApplicationContext applicationContext) {
    return new DefaultHotKeyCacheBinder(registry, applicationContext);
  }
}
