package com.remisoft.common.cache.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

import com.remisoft.common.cache.annotation.CacheAnnotationAspect;
import com.remisoft.common.cache.health.CacheHealthIndicator;
import com.remisoft.common.cache.health.SpringCacheHealthIndicator;
import com.remisoft.common.cache.multilevel.DistributedRebuildLock;
import com.remisoft.common.cache.support.CacheThreadPoolManager;
import com.remisoft.common.cache.support.CacheWarmer;

/**
 * YdszCache Spring Boot 自动配置
 *
 * <p>提供 YdszCache 的 Spring Boot 自动配置， 支持通过 application.yml 配置缓存参数（全局默认 + per-cache 覆盖）。
 *
 * <p>配置示例：
 *
 * <pre>
 * remi:
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
 *
 * @author remi-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(YdszCacheManager.class)
@EnableConfigurationProperties(YdszCacheProperties.class)
public class YdszCacheAutoConfiguration {

  /**
   * 创建本地（进程内）缓存管理器，作为 Spring Cache 抽象的主实现。
   *
   * <p>将 {@link YdszCacheProperties} 中的全局默认参数（类型、容量、TTL、是否允许 null、弱引用开关等）
   * 一次性灌入 {@link YdszCacheManager}，并叠加 {@code caches} 下的 per-cache 覆盖配置。
   * 使用 {@code @ConditionalOnMissingBean}，允许用户自定义 {@code YdszCacheManager} 完全覆盖本默认实例。
   *
   * @param props 缓存全局配置，由 {@code @EnableConfigurationProperties} 绑定，不会为 null
   * @return 已按配置初始化的本地缓存管理器
   */
  @Bean
  @ConditionalOnMissingBean
  public YdszCacheManager springLocalCacheManager(YdszCacheProperties props) {
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

  /**
   * 注册基于 Spring Boot Health 抽象的健康指示器（优先版本）。
   *
   * <p>当项目依赖 Spring Boot Actuator（classpath 存在 {@code HealthIndicator}）且健康检查开关开启时装配。
   * 与下方 {@link #cacheHealthIndicator()} 互斥：本 Bean 存在时后者因
   * {@code @ConditionalOnMissingBean(SpringCacheHealthIndicator.class)} 不再创建，确保只暴露一种健康端点，
   * 避免重复注册。{@code matchIfMissing = true} 表示默认启用。
   *
   * @param cacheManager 待监测的缓存管理器，由容器注入，不会为 null
   * @return Spring Boot 风格的健康指示器
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "remi.cache.health-check.enabled", havingValue = "true", matchIfMissing = true)
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  public SpringCacheHealthIndicator springCacheHealthIndicator(YdszCacheManager cacheManager) {
    return new SpringCacheHealthIndicator(cacheManager);
  }

  /**
   * 注册降级版健康指示器（无 Actuator 依赖时的兜底）。
   *
   * <p>仅当 Spring Boot {@code HealthIndicator} 不存在（未引入 Actuator）时才装配：
   * 通过 {@code @ConditionalOnMissingBean(SpringCacheHealthIndicator.class)} 让位给上方优先版本。
   * 两者均受 {@code remi.cache.health-check.enabled} 开关控制，默认开启。
   *
   * @return 自包含的健康指示器（不依赖 Spring Boot Health 抽象）
   */
  @Bean
  @ConditionalOnMissingBean(value = {SpringCacheHealthIndicator.class, CacheHealthIndicator.class})
  @ConditionalOnProperty(name = "remi.cache.health-check.enabled", havingValue = "true", matchIfMissing = true)
  public CacheHealthIndicator cacheHealthIndicator() {
    return new CacheHealthIndicator();
  }

  /**
   * 创建缓存异步任务线程池管理器，并固定为全局单例。
   *
   * <p>缓存的预热、异步刷新、分布式重建等后台任务共用该线程池。此处通过
   * {@link com.remisoft.common.cache.support.CacheThreadPoolManager#setInstance} 将其设为全局可达实例，
   * 以便非 Spring 托管的缓存内部代码也能取用，同时使其 {@code DisposableBean} 生命周期钩子由 Spring 统一回收。
   * {@code @ConditionalOnMissingBean} 允许外部自定义线程池策略。
   *
   * @return 缓存线程池管理器
   */
  @Bean
  @ConditionalOnMissingBean
  public CacheThreadPoolManager cacheThreadPoolManager() {
    CacheThreadPoolManager manager = new CacheThreadPoolManager();
    // 将 Spring 管理的实例设置为全局单例，使 DisposableBean 生命周期管理生效
    CacheThreadPoolManager.setInstance(manager);
    return manager;
  }

  /**
   * 注册缓存预热器，在应用启动后主动加载热点数据以降低冷启动命中率。
   *
   * <p>仅当 {@code remi.cache.warmup.enabled=true} 时装配（默认不开启），避免无预热需求的场景占用启动时间。
   * 预热任务运行于 {@link #cacheThreadPoolManager()} 提供的线程池，由 {@code SmartInitializingSingleton}
   * 在所有单例就绪后触发。{@code @ConditionalOnMissingBean} 允许自定义预热逻辑覆盖。
   *
   * @return 缓存预热器
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "remi.cache.warmup.enabled", havingValue = "true")
  public CacheWarmer cacheWarmer() {
    return new CacheWarmer();
  }

  /**
   * 注册分布式缓存重建锁（防止多节点同时重建缓存）
   *
   * <p>需要 classpath 中存在 RedisTemplate。当使用多级缓存的分布式重建功能时自动生效。
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
  @ConditionalOnProperty(
      name = "remi.cache.multilevel.rebuild-lock.enabled",
      havingValue = "true",
      matchIfMissing = false)
  public DistributedRebuildLock distributedRebuildLock(
      RedisTemplate<String, Object> redisTemplate) {
    return new DistributedRebuildLock(redisTemplate);
  }

  /**
   * 注册缓存注解 AOP 切面（@Cached / @CacheInvalidate）
   *
   * <p>需要 classpath 中存在 AspectJ Weaver。当使用 @Cached 注解时自动生效。
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
  @ConditionalOnProperty(
      name = "remi.cache.annotation.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CacheAnnotationAspect cacheAnnotationAspect(CacheManager cacheManager) {
    return new CacheAnnotationAspect(cacheManager);
  }
}
