package com.njydsz.common.cache.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.cache.health.CacheHealthIndicator;
import com.njydsz.common.cache.health.SpringCacheHealthIndicator;
import com.njydsz.common.cache.support.CacheThreadPoolManager;

/**
 * YdszCache Spring Boot 自动配置
 *
 * <p>提供 YdszCache 的 Spring Boot 自动配置，支持通过 application.yml 配置缓存参数（全局默认 + per-cache 覆盖）。
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
 *         type: STRIPED
 *         maximum-size: 5000
 *       orders:
 *         type: TINYLFU
 *         maximum-size: 20000
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnClass(YdszCacheManager.class)
@EnableConfigurationProperties(YdszCacheProperties.class)
public class YdszCacheAutoConfiguration {

  /**
   * 创建本地（进程内）缓存管理器，作为 Spring Cache 抽象的主实现。
   *
   * <p>将 {@link YdszCacheProperties} 中的全局默认参数（类型、容量、TTL、是否允许 null 等） 一次性灌入 {@link
   * YdszCacheManager}，并叠加 {@code caches} 下的 per-cache 覆盖配置。 使用
   * {@code @ConditionalOnMissingBean}，允许用户自定义 {@code YdszCacheManager} 完全覆盖本默认实例。
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
    cacheManager.setNullValueTtl(props.getNullValueTtlMin(), props.getNullValueTtlMax());
    // 设置 per-cache 配置
    cacheManager.setPerCacheConfigs(props.getCaches());
    return cacheManager;
  }

  /**
   * 注册基于 Spring Boot Health 抽象的健康指示器（优先版本）。
   *
   * <p>当项目依赖 Spring Boot Actuator（classpath 存在 {@code HealthIndicator}）且健康检查开关开启时装配。 与下方 {@link
   * #cacheHealthIndicator()} 互斥：本 Bean 存在时后者因
   * {@code @ConditionalOnMissingBean(SpringCacheHealthIndicator.class)} 不再创建，确保只暴露一种健康端点，
   * 避免重复注册。{@code matchIfMissing = true} 表示默认启用。
   *
   * @param cacheManager 待监测的缓存管理器，由容器注入，不会为 null
   * @return Spring Boot 风格的健康指示器
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      name = "ydsz.cache.health-check.enabled",
      havingValue = "true",
      matchIfMissing = true)
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  public SpringCacheHealthIndicator springCacheHealthIndicator(YdszCacheManager cacheManager) {
    return new SpringCacheHealthIndicator(cacheManager);
  }

  /**
   * 注册降级版健康指示器（无 Actuator 依赖时的兜底）。
   *
   * <p>仅当 Spring Boot {@code HealthIndicator} 不存在（未引入 Actuator）时才装配： 通过
   * {@code @ConditionalOnMissingBean(SpringCacheHealthIndicator.class)} 让位给上方优先版本。 两者均受 {@code
   * ydsz.cache.health-check.enabled} 开关控制，默认开启。
   *
   * @return 自包含的健康指示器（不依赖 Spring Boot Health 抽象）
   */
  @Bean
  @ConditionalOnMissingBean(value = {SpringCacheHealthIndicator.class, CacheHealthIndicator.class})
  @ConditionalOnProperty(
      name = "ydsz.cache.health-check.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CacheHealthIndicator cacheHealthIndicator() {
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
    return new CacheHealthIndicator();
  // CHECKSTYLE.ON: RegexpSinglelineJava
  }

  /**
   * 创建缓存异步任务线程池管理器，并固定为全局单例。
   *
   * <p>缓存的异步刷新、过期清理等后台任务共用该线程池。此处通过 {@link
   * com.njydsz.common.cache.support.CacheThreadPoolManager#setInstance} 将其设为全局可达实例， 以便非 Spring
   * 托管的缓存内部代码也能取用，同时使其 {@code DisposableBean} 生命周期钩子由 Spring 统一回收。
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
}
