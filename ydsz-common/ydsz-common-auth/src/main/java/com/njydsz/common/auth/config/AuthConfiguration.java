package com.njydsz.common.auth.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.auth.aspect.AuthColPermissionAspect;
import com.njydsz.common.auth.aspect.AuthPermissionAspect;
import com.njydsz.common.auth.aspect.AuthRowPermissionAspect;
import com.njydsz.common.auth.desensitize.ColumnDesensitizationService;
import com.njydsz.common.auth.event.PermissionCacheInvalidationListener;
import com.njydsz.common.auth.event.PermissionChangeCacheInvalidator;
import com.njydsz.common.auth.event.PermissionChangeNotifier;
import com.njydsz.common.auth.health.AuthHealthIndicator;
import com.njydsz.common.auth.hierarchy.PermissionHierarchyService;
import com.njydsz.common.auth.listener.PermissionKeyspaceNotificationListener;
import com.njydsz.common.auth.metrics.AuthMetricsCollector;
import com.njydsz.common.auth.security.CsrfTokenValidator;
import com.njydsz.common.auth.service.ColumnPermissionResolver;
import com.njydsz.common.auth.service.RbacPermissionEvaluator;
import com.njydsz.common.auth.service.RbacUserInfoService;
import com.njydsz.common.auth.service.RolePermissionCacheService;
import com.njydsz.common.auth.service.RolePermissionLoader;
import com.njydsz.common.auth.service.TokenBlacklistService;
import com.njydsz.common.auth.service.impl.RedisRbacUserInfoService;
import com.njydsz.common.auth.service.impl.RedisRoleColumnPermissionResolver;
import com.njydsz.common.auth.service.impl.RedisRoleDataPermissionResolver;
import com.njydsz.common.auth.service.impl.RedisRolePermissionLoader;
import com.njydsz.common.auth.strategy.CacheKeyStrategy;
import com.njydsz.common.auth.strategy.DefaultCacheKeyStrategy;
import com.njydsz.common.auth.token.JwtTokenService;
import com.njydsz.common.auth.token.TokenProperties;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * 认证授权模块配置类。
 *
 * <p>提供权限相关 Bean 的自动装配，支持 Redis 不可用时的降级策略。
 *
 * <p><b>核心功能：</b>
 *
 * <ul>
 *   <li>权限评估器装配
 *   <li>行级/列级权限解析器装配
 *   <li>权限缓存失效监听器
 *   <li>Redis 不可用时自动降级处理
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "ydsz.auth",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties({
  AuthProperties.class,
  KeyspaceNotificationProperties.class,
  TokenProperties.class
})
public class AuthConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(AuthConfiguration.class);

  /** 本地缓存健康检查间隔（秒） */
  private static final long HEALTH_CHECK_INTERVAL_SECONDS = 60;

  private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
  private final ObjectProvider<RbacPermissionEvaluator> evaluatorProvider;

  public AuthConfiguration(
      ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider,
      ObjectProvider<RbacPermissionEvaluator> evaluatorProvider) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.evaluatorProvider = evaluatorProvider;
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
   * 创建权限变更事件发布器。
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
   * @return 权限校验切面实例
   */
  @Bean
  @ConditionalOnMissingBean
  public AuthPermissionAspect authPermissionAspect(RbacPermissionEvaluator evaluator) {
    return new AuthPermissionAspect(evaluator);
  }

  /**
   * 创建数据权限解析器。
   *
   * @param redisStringOps Redis String 操作
   * @param properties 认证配置属性
   * @param userInfoService 用户信息服务
   * @return 数据权限解析器实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RedisStringOps.class)
  public RedisRoleDataPermissionResolver dataPermissionResolver(
      RedisStringOps redisStringOps,
      AuthProperties properties,
      RbacUserInfoService userInfoService) {
    return new RedisRoleDataPermissionResolver(redisStringOps, properties, userInfoService);
  }

  /**
   * 创建行级权限切面。
   *
   * @param resolver 数据权限解析器
   * @return 行级权限切面实例
   */
  @Bean
  @ConditionalOnMissingBean
  public AuthRowPermissionAspect authRowPermissionAspect(RedisRoleDataPermissionResolver resolver) {
    return new AuthRowPermissionAspect(resolver);
  }

  /**
   * 创建列权限解析器。
   *
   * @param redisStringOps Redis String 操作
   * @param properties 认证配置属性
   * @param userInfoService 用户信息服务
   * @return 列权限解析器实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RedisStringOps.class)
  public ColumnPermissionResolver columnPermissionResolver(
      RedisStringOps redisStringOps,
      AuthProperties properties,
      RbacUserInfoService userInfoService) {
    return new RedisRoleColumnPermissionResolver(redisStringOps, properties, userInfoService);
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
   * <p>监听权限变更事件，在权限变更时自动清理 RbacPermissionEvaluator 中的缓存。
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

  /**
   * Redis Keyspace Notification 权限缓存失效监听器。
   *
   * <p>当权限数据在 Redis 中被修改/删除时，通过 Keyspace Notification 精确触发缓存失效， 替代原有的 Pub/Sub 广播模式，解决不保证送达的问题。
   *
   * @param evaluator 权限评估器
   * @param keyspaceProperties Keyspace Notification 配置
   * @return 监听器实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "ydsz.auth.keyspace-notification",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public PermissionKeyspaceNotificationListener permissionKeyspaceNotificationListener(
      RbacPermissionEvaluator evaluator, KeyspaceNotificationProperties keyspaceProperties) {
    return new PermissionKeyspaceNotificationListener(evaluator);
  }

  /**
   * 创建 Token 黑名单服务。
   *
   * <p>当 {@link DistributedLocker} 可用时（ydsz-common-lock 在 classpath 上）， 使用其 {@code tryLock}/{@code
   * unlock} 实现刷新锁，享有 Lua 原子释放与 WatchDog 续期能力； 否则降级为原生 {@code setIfAbsent} 操作。
   *
   * @param redisStringOps Redis String 操作
   * @param authProperties 认证配置属性
   * @param lockerProvider 分布式锁提供者（可选）
   * @return Token 黑名单服务实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RedisStringOps.class)
  public TokenBlacklistService tokenBlacklistService(
      RedisStringOps redisStringOps,
      AuthProperties authProperties,
      ObjectProvider<DistributedLocker> lockerProvider) {
    DistributedLocker locker = lockerProvider.getIfAvailable();
    if (locker == null) {
      LOG.info(
          "[AuthConfiguration] DistributedLocker 不可用，TokenBlacklistService 刷新锁降级为原生 setIfAbsent");
    }
    return new TokenBlacklistService(locker, redisStringOps, authProperties);
  }

  /**
   * 创建 CSRF 验证器 Bean（可选，默认启用）。
   *
   * @return CsrfTokenValidator 实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "ydsz.auth",
      name = "csrf-enabled",
      havingValue = "true",
      matchIfMissing = false)
  public CsrfTokenValidator csrfTokenValidator() {
    return new CsrfTokenValidator(true);
  }

  /**
   * 创建 JWT Token 服务。
   *
   * @param tokenProperties Token 配置属性
   * @param tokenBlacklistServiceProvider Token 黑名单服务（可选）
   * @param snowflakeIdGeneratorProvider 分布式 ID 生成器（用于 jti；缺失时构造器会给出明确报错）
   * @return Token 服务实例
   */
  @Bean
  @ConditionalOnClass(name = "io.jsonwebtoken.Jwts")
  @ConditionalOnProperty(
      prefix = "ydsz.auth.token",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @ConditionalOnMissingBean(TokenService.class)
  public TokenService jwtTokenService(
      TokenProperties tokenProperties,
      ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider,
      ObjectProvider<SnowflakeIdGenerator> snowflakeIdGeneratorProvider) {
    return new JwtTokenService(
        tokenProperties,
        tokenBlacklistServiceProvider.getIfAvailable(),
        snowflakeIdGeneratorProvider.getIfAvailable());
  }

  /**
   * 创建权限模块 Micrometer 指标采集器 Bean
   *
   * @param meterRegistryProvider Micrometer 指标注册中心提供者（可选）
   * @return AuthMetricsCollector 实例
   */
  @Bean
  @ConditionalOnMissingBean(AuthMetricsCollector.class)
  @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
  @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
  public AuthMetricsCollector authMetricsCollector(
      ObjectProvider<MeterRegistry> meterRegistryProvider) {
    return new AuthMetricsCollector(meterRegistryProvider.getIfAvailable());
  }

  /**
   * 创建权限模块健康检查指示器 Bean
   *
   * @param redisConnectionFactory Redis 连接工厂
   * @return AuthHealthIndicator 实例
   */
  @Bean
  @ConditionalOnMissingBean(AuthHealthIndicator.class)
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  @ConditionalOnBean(RedisConnectionFactory.class)
  public AuthHealthIndicator authHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
    return new AuthHealthIndicator(redisConnectionFactory);
  }

  /**
   * 创建权限缓存失效监听器 Bean（监听 Redis Pub/Sub 和 Spring 事件）
   *
   * @param rolePermissionLoader 角色权限加载器
   * @param dataPermissionResolver 数据权限解析器
   * @param columnPermissionResolver 列权限解析器
   * @param redisMessageListenerContainer Redis 消息监听容器
   * @return PermissionChangeCacheInvalidator 实例
   */
  @Bean
  @ConditionalOnMissingBean(PermissionChangeCacheInvalidator.class)
  @ConditionalOnBean(RedisMessageListenerContainer.class)
  @ConditionalOnProperty(prefix = "ydsz.auth", name = "cross-instance-enabled", havingValue = "true", matchIfMissing = false)
  public PermissionChangeCacheInvalidator permissionChangeCacheInvalidator(
      RolePermissionLoader rolePermissionLoader,
      RedisRoleDataPermissionResolver dataPermissionResolver,
      ColumnPermissionResolver columnPermissionResolver,
      RedisMessageListenerContainer redisMessageListenerContainer) {
    return new PermissionChangeCacheInvalidator(
        rolePermissionLoader,
        dataPermissionResolver,
        columnPermissionResolver,
        redisMessageListenerContainer);
  }

  /**
   * 定时健康检查 Redis 连通性。
   *
   * <p>每分钟检查一次 Redis 连通状态，Redis 不可用时自动降级， 并通知 RbacPermissionEvaluator 切换降级策略（ALLOW/DENY）。
   */
  @Scheduled(fixedRateString = "${ydsz.auth.health-check-interval:60000}")
  public void checkRedisHealth() {
    boolean redisOk = true;
    RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
    RbacPermissionEvaluator evaluator = evaluatorProvider.getIfAvailable();
    if (redisTemplate == null) {
      LOG.debug("Redis 服务未配置，使用本地缓存兜底");
      redisOk = false;
    } else {
      try {
        var connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
          LOG.debug("Redis 连接工厂未初始化，降级到本地缓存");
          redisOk = false;
        } else {
          try (var connection = connectionFactory.getConnection()) {
            connection.ping();
            if (evaluator != null && !evaluator.isRedisAvailable()) {
              LOG.info("Redis 健康检查恢复，切换回 Redis 缓存");
            }
          }
        }
      } catch (Exception e) {
        LOG.error("Redis 健康检查异常，降级到本地缓存: {}", e.getMessage());
        redisOk = false;
      }
    }

    // 通知权限评估器切换降级策略
    if (evaluator != null) {
      evaluator.setRedisAvailable(redisOk);
    }
  }
}
