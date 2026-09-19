package com.njydsz.common.auth.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.auth.aspect.AuthColPermissionAspect;
import com.njydsz.common.auth.aspect.AuthPermissionAspect;
import com.njydsz.common.auth.aspect.AuthRowPermissionAspect;
import com.njydsz.common.auth.desensitize.ColumnDesensitizationService;
import com.njydsz.common.auth.event.PermissionCacheInvalidationListener;
import com.njydsz.common.auth.event.PermissionChangeNotifier;
import com.njydsz.common.auth.hierarchy.PermissionHierarchyService;
import com.njydsz.common.auth.metrics.AuthMetricsCollector;
import com.njydsz.common.auth.service.ColumnPermissionResolver;
import com.njydsz.common.auth.service.ColumnScopeFallbackLoader;
import com.njydsz.common.auth.service.DataPermissionResolver;
import com.njydsz.common.auth.service.DataScopeFallbackLoader;
import com.njydsz.common.auth.service.RbacPermissionEvaluator;
import com.njydsz.common.auth.service.RbacUserInfoService;
import com.njydsz.common.auth.service.RolePermissionCacheService;
import com.njydsz.common.auth.service.RolePermissionLoader;
import com.njydsz.common.auth.service.impl.LocalRoleColumnPermissionResolver;
import com.njydsz.common.auth.service.impl.LocalRoleDataPermissionResolver;
import com.njydsz.common.auth.service.impl.RedisRbacUserInfoService;
import com.njydsz.common.auth.service.impl.RedisRoleColumnPermissionResolver;
import com.njydsz.common.auth.service.impl.RedisRoleDataPermissionResolver;
import com.njydsz.common.auth.service.impl.RedisRolePermissionLoader;
import com.njydsz.common.auth.strategy.CacheKeyStrategy;
import com.njydsz.common.auth.strategy.DefaultCacheKeyStrategy;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * RBAC 权限引擎配置。
 *
 * <p>负责装配权限校验全链路的核心 Bean：
 *
 * <ul>
 *   <li>{@link RolePermissionLoader}（Redis 加载器 + 可选层级）
 *   <li>{@link RbacPermissionEvaluator}（权限评估器）
 *   <li>{@link AuthPermissionAspect} / {@link AuthRowPermissionAspect} / {@link AuthColPermissionAspect}（权限切面）
 *   <li>{@link DataPermissionResolver} / {@link ColumnPermissionResolver}（行/列权限解析器）
 *   <li>{@link RolePermissionCacheService}（角色权限缓存）
 *   <li>{@link PermissionChangeNotifier}（权限变更事件发布器）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.18
 */
@Configuration
public class RbacConfiguration {

  /**
   * 创建权限变更事件发布器。
   *
   * <p>使用 Spring {@link ApplicationEventPublisher} 广播 {@link
   * com.njydsz.common.auth.event.PermissionChangedEvent}，由 {@link PermissionCacheInvalidationListener} 订阅执行缓存失效。
   *
   * @param applicationEventPublisher Spring 应用事件发布器
   * @return 权限变更事件发布器实例
   */
  @Bean
  @ConditionalOnMissingBean
  public PermissionChangeNotifier permissionChangeNotifier(
      ApplicationEventPublisher applicationEventPublisher) {
    return new PermissionChangeNotifier(applicationEventPublisher);
  }

  /**
   * 创建用户信息服务。
   *
   * @param redisHashOps Redis Hash 操作
   * @return 用户信息服务实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RedisHashOps.class)
  public RbacUserInfoService rbacUserInfoService(RedisHashOps redisHashOps) {
    return new RedisRbacUserInfoService(redisHashOps);
  }

  /**
   * 创建角色权限加载器。
   *
   * @param redisStringOps Redis String 操作
   * @param properties 认证配置属性
   * @param notifier 权限变更事件发布器
   * @return 角色权限加载器实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RedisStringOps.class)
  public RolePermissionLoader rolePermissionLoader(
      RedisStringOps redisStringOps,
      AuthProperties properties,
      PermissionChangeNotifier notifier,
      RolePermissionCacheService permissionCacheService,
      ObjectProvider<PermissionHierarchyService> hierarchyServiceProvider) {
    PermissionHierarchyService hierarchyService = hierarchyServiceProvider.getIfAvailable();
    return new RedisRolePermissionLoader(
        redisStringOps, properties, notifier, permissionCacheService, hierarchyService);
  }

  /**
   * 创建缓存 Key 生成策略 Bean（默认实现）。
   *
   * <p>可通过 {@code @Bean} + {@code @Primary} 或 {@code @ConditionalOnMissingBean} 覆盖。
   *
   * @return 默认的缓存 Key 生成策略
   */
  @Bean
  @ConditionalOnMissingBean(CacheKeyStrategy.class)
  public CacheKeyStrategy cacheKeyStrategy() {
    return new DefaultCacheKeyStrategy();
  }

  /**
   * 创建角色权限缓存服务。
   *
   * <p>封装角色权限缓存的全部管理职责（缓存查询、写入、失效、反向索引）， 使 {@link RbacPermissionEvaluator} 专注于权限校验逻辑。
   *
   * @param properties 认证配置属性
   * @return 角色权限缓存服务实例
   */
  @Bean
  @ConditionalOnMissingBean
  public RolePermissionCacheService rolePermissionCacheService(AuthProperties properties) {
    return new RolePermissionCacheService(properties);
  }

  /**
   * 创建权限评估器。
   *
   * @param properties 认证配置属性
   * @param userInfoService 用户信息服务
   * @param rolePermissionLoader 角色权限加载器
   * @param rolePermissionCacheService 角色权限缓存服务
   * @param cacheKeyStrategy 缓存 Key 生成策略
   * @param metricsCollectorProvider 指标采集器提供者（可选）
   * @param hierarchyServiceProvider 权限层级服务提供者（可选）
   * @return 权限评估器实例
   */
  @Bean
  @ConditionalOnMissingBean
  public RbacPermissionEvaluator rbacPermissionEvaluator(
      AuthProperties properties,
      RbacUserInfoService userInfoService,
      RolePermissionLoader rolePermissionLoader,
      RolePermissionCacheService rolePermissionCacheService,
      CacheKeyStrategy cacheKeyStrategy,
      ObjectProvider<AuthMetricsCollector> metricsCollectorProvider,
      ObjectProvider<PermissionHierarchyService> hierarchyServiceProvider) {
    RbacPermissionEvaluator evaluator =
        new RbacPermissionEvaluator(
            properties, userInfoService, rolePermissionLoader, rolePermissionCacheService);
    evaluator.setCacheKeyStrategy(cacheKeyStrategy);
    AuthMetricsCollector metricsCollector = metricsCollectorProvider.getIfAvailable();
    if (metricsCollector != null) {
      evaluator.setMetricsCollector(metricsCollector);
    }
    PermissionHierarchyService hierarchyService = hierarchyServiceProvider.getIfAvailable();
    if (hierarchyService != null) {
      evaluator.setHierarchyService(hierarchyService);
    }
    return evaluator;
  }

  /**
   * 创建统一权限校验切面。
   *
   * @param evaluator 权限评估器
   * @param metricsCollectorProvider 指标采集器提供者（可选）
   * @return 权限校验切面实例
   */
  @Bean
  @ConditionalOnMissingBean
  public AuthPermissionAspect authPermissionAspect(
      RbacPermissionEvaluator evaluator,
      ObjectProvider<AuthMetricsCollector> metricsCollectorProvider) {
    AuthPermissionAspect aspect = new AuthPermissionAspect(evaluator);
    AuthMetricsCollector collector = metricsCollectorProvider.getIfAvailable();
    if (collector != null) {
      aspect.setMetricsCollector(collector);
    }
    return aspect;
  }

  /**
   * 创建 Redis 数据权限解析器。
   *
   * <p>当 RedisStringOps 可用时（ydsz-common-redis 在 classpath 上且 Redis 连接正常），注册此高性能实现。
   *
   * @param redisStringOps Redis String 操作
   * @param properties 认证配置属性
   * @param userInfoService 用户信息服务
   * @return 数据权限解析器实例
   */
  @Bean
  @ConditionalOnMissingBean(DataPermissionResolver.class)
  @ConditionalOnBean(RedisStringOps.class)
  public RedisRoleDataPermissionResolver redisDataPermissionResolver(
      RedisStringOps redisStringOps,
      AuthProperties properties,
      RbacUserInfoService userInfoService) {
    return new RedisRoleDataPermissionResolver(redisStringOps, properties, userInfoService);
  }

  /**
   * 创建本地兜底数据权限解析器。
   *
   * <p>当 Redis 不可用时（{@link DataPermissionResolver} 尚无其他实现），注册此本地缓存兜底实现。 缓存 TTL 使用 {@code
   * localPermissionCacheMinutes}（默认 5 分钟），并通过 {@link DataScopeFallbackLoader} SPI 允许业务方从本地 DB / 配置文件加载兜底数据。
   *
   * @param properties 认证配置属性
   * @param userInfoServiceProvider 用户信息服务提供者（可选）
   * @param fallbackLoaderProvider 兜底数据加载器提供者（可选）
   * @return 本地兜底数据权限解析器
   */
  @Bean
  @ConditionalOnMissingBean(DataPermissionResolver.class)
  public LocalRoleDataPermissionResolver localDataPermissionResolver(
      AuthProperties properties,
      ObjectProvider<RbacUserInfoService> userInfoServiceProvider,
      ObjectProvider<DataScopeFallbackLoader> fallbackLoaderProvider) {
    return new LocalRoleDataPermissionResolver(
        properties, userInfoServiceProvider, fallbackLoaderProvider);
  }

  /**
   * 创建行级权限切面。
   *
   * @param resolver 数据权限解析器接口
   * @return 行级权限切面实例
   */
  @Bean
  @ConditionalOnMissingBean
  public AuthRowPermissionAspect authRowPermissionAspect(DataPermissionResolver resolver) {
    return new AuthRowPermissionAspect(resolver);
  }

  /**
   * 创建 Redis 列权限解析器。
   *
   * <p>当 RedisStringOps 可用时注册此实现。
   *
   * @param redisStringOps Redis String 操作
   * @param properties 认证配置属性
   * @param userInfoService 用户信息服务
   * @return 列权限解析器实例
   */
  @Bean
  @ConditionalOnMissingBean(ColumnPermissionResolver.class)
  @ConditionalOnBean(RedisStringOps.class)
  public ColumnPermissionResolver redisColumnPermissionResolver(
      RedisStringOps redisStringOps,
      AuthProperties properties,
      RbacUserInfoService userInfoService) {
    return new RedisRoleColumnPermissionResolver(redisStringOps, properties, userInfoService);
  }

  /**
   * 创建本地兜底列权限解析器。
   *
   * <p>当 Redis 不可用时（{@link ColumnPermissionResolver} 尚无其他实现），注册此本地缓存兜底实现。 缓存 TTL 使用 {@code
   * localPermissionCacheMinutes}（默认 5 分钟），并通过 {@link ColumnScopeFallbackLoader} SPI 允许业务方从本地 DB / 配置文件加载兜底数据。
   *
   * @param properties 认证配置属性
   * @param userInfoServiceProvider 用户信息服务提供者（可选）
   * @param fallbackLoaderProvider 兜底数据加载器提供者（可选）
   * @return 本地兜底列权限解析器
   */
  @Bean
  @ConditionalOnMissingBean(ColumnPermissionResolver.class)
  public LocalRoleColumnPermissionResolver localColumnPermissionResolver(
      AuthProperties properties,
      ObjectProvider<RbacUserInfoService> userInfoServiceProvider,
      ObjectProvider<ColumnScopeFallbackLoader> fallbackLoaderProvider) {
    return new LocalRoleColumnPermissionResolver(
        properties, userInfoServiceProvider, fallbackLoaderProvider);
  }

  /**
   * 创建列权限切面。
   *
   * @param resolver 列权限解析器
   * @param desensitizationService 列脱敏服务
   * @return 列权限切面实例
   */
  @Bean
  @ConditionalOnMissingBean
  public AuthColPermissionAspect authColPermissionAspect(
      ColumnPermissionResolver resolver, ColumnDesensitizationService desensitizationService) {
    return new AuthColPermissionAspect(resolver, desensitizationService);
  }

  /**
   * 权限缓存失效监听器。
   *
   * <p>监听 {@link com.njydsz.common.auth.event.PermissionChangedEvent}，在权限变更时自动清理 {@link RbacPermissionEvaluator} 中的缓存。
   *
   * @param evaluator 权限评估器
   * @return 缓存失效监听器实例
   */
  @Bean
  @ConditionalOnMissingBean
  public PermissionCacheInvalidationListener permissionCacheInvalidationListener(
      RbacPermissionEvaluator evaluator) {
    return new PermissionCacheInvalidationListener(evaluator);
  }
}
