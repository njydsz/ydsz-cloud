package com.njydsz.pmis.common.cache.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.cache.health.CacheHealthIndicator;
import com.njydsz.pmis.common.cache.support.CacheThreadPoolManager;
import com.njydsz.pmis.common.cache.support.CacheWarmer;

/**
 * YdszCache Spring Boot 自动配置
 *
 * <p>提供 YdszCache 的 Spring Boot 自动配置， 支持通过 application.yml 配置缓存参数（全局默认 + per-cache 覆盖）。
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   cache:
 *     type: TINYLFU
 *     maximum-size: 1000
 *     expire-after-write: 30
 *     expire-time-unit: MINUTES
 *     allow-null-values: true
 *     caches:
 *       users:
 *         type: LRU
 *         maximum-size: 5000
 *       orders:
 *         type: TINYLFU
 *         maximum-size: 20000
 * </pre>
 *
 * @author Marvin Lee
 * @version 4.0.0
 */
@AutoConfiguration
@ConditionalOnClass(YdszCacheManager.class)
@EnableConfigurationProperties(YdszCacheProperties.class)
public class YdszCacheAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public YdszCacheManager springYdszCacheManager(YdszCacheProperties props) {
    YdszCacheManager cacheManager = new YdszCacheManager();
    cacheManager.setCacheType(props.getType());
    cacheManager.setCacheNames(props.getCacheNames());
    cacheManager.setMaximumSize(props.getMaximumSize());
    cacheManager.setExpireAfterWrite(props.getExpireAfterWrite(), props.getExpireTimeUnit());
    cacheManager.setAllowNullValues(props.isAllowNullValues());
    cacheManager.setInitialCapacity(props.getInitialCapacity());
    cacheManager.setExpireAfterAccess(props.getExpireAfterAccess(), props.getExpireTimeUnit());
    cacheManager.setRefreshAfterWrite(props.getRefreshAfterWrite(), props.getExpireTimeUnit());
    cacheManager.setRecordStats(props.isRecordStats());
    cacheManager.setWeakKeys(props.isWeakKeys());
    cacheManager.setWeakValues(props.isWeakValues());
    cacheManager.setSoftValues(props.isSoftValues());
    // 设置 per-cache 配置
    cacheManager.setPerCacheConfigs(props.getCaches());
    return cacheManager;
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "ydsz.cache.health-check.enabled", havingValue = "true", matchIfMissing = true)
  public CacheHealthIndicator cacheHealthIndicator() {
    return new CacheHealthIndicator();
  }

  @Bean
  @ConditionalOnMissingBean
  public CacheThreadPoolManager cacheThreadPoolManager() {
    return new CacheThreadPoolManager();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "ydsz.cache.warmup.enabled", havingValue = "true")
  public CacheWarmer cacheWarmer() {
    return new CacheWarmer();
  }
}
