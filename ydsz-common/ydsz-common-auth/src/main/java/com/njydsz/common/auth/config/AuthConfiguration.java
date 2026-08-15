package com.njydsz.common.auth.config;

import java.util.concurrent.TimeUnit;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.auth.aspect.AuthColPermissionAspect;
import com.njydsz.common.auth.aspect.AuthPermissionAspect;
import com.njydsz.common.auth.aspect.AuthRowPermissionAspect;
import com.njydsz.common.auth.cache.LocalPermissionCache;
import com.njydsz.common.auth.desensitize.ColumnDesensitizationService;
import com.njydsz.common.auth.event.PermissionCacheInvalidationListener;
import com.njydsz.common.auth.event.PermissionChangeCacheInvalidator;
import com.njydsz.common.auth.event.PermissionChangeNotifier;
import com.njydsz.common.auth.event.PermissionChangePublisher;
import com.njydsz.common.auth.health.AuthHealthIndicator;
import com.njydsz.common.auth.listener.PermissionKeyspaceNotificationListener;
import com.njydsz.common.auth.metrics.AuthMetricsCollector;
import com.njydsz.common.auth.model.RolePermissions;
import com.njydsz.common.auth.security.CsrfTokenValidator;
import com.njydsz.common.auth.security.RateLimiter;
import com.njydsz.common.auth.service.ColumnPermissionResolver;
import com.njydsz.common.auth.service.DataPermissionResolver;
import com.njydsz.common.auth.service.RbacPermissionEvaluator;
import com.njydsz.common.auth.service.RbacUserInfoService;
import com.njydsz.common.auth.service.RolePermissionLoader;
import com.njydsz.common.auth.service.TokenBlacklistService;
import com.njydsz.common.auth.warmup.PermissionWarmUpInitializer;
import com.njydsz.common.auth.service.impl.RedisRbacUserInfoService;
import com.njydsz.common.auth.service.impl.RedisRoleColumnPermissionResolver;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.auth.service.impl.RedisRoleDataPermissionResolver;
import com.njydsz.common.auth.service.impl.RedisRolePermissionLoader;
import com.njydsz.common.auth.strategy.CacheKeyStrategy;
import com.njydsz.common.auth.strategy.DefaultCacheKeyStrategy;
import com.njydsz.common.auth.token.JwtTokenService;
import com.njydsz.common.auth.token.TokenProperties;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.redis.service.RedisRateLimiter;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;

import io.micrometer.core.instrument.MeterRegistry;
/**
 * 认证授权模块配置类。
 *
 * <p>提供权限相关 Bean 的自动装配，支持 Redis 降级到本地缓存。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li>权限评估器装配</li>
 *   <li>行级/列级权限解析器装配</li>
 *   <li>权限缓存失效监听器</li>
 *   <li>Redis 不可用时自动降级到本地缓存</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
@AutoConfiguration
@EnableScheduling
@ConditionalOnProperty(prefix = "ydsz.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({AuthProperties.class, KeyspaceNotificationProperties.class, TokenProperties.class})
public class AuthConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuthConfiguration.class);

    /**
     * 本地缓存健康检查间隔（秒）
     */
    private static final long HEALTH_CHECK_INTERVAL_SECONDS = 60;

    private final LocalPermissionCache<Object> localCache;

    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
    private final ObjectProvider<RbacPermissionEvaluator> evaluatorProvider;

    public AuthConfiguration(ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider,
                             ObjectProvider<RbacPermissionEvaluator> evaluatorProvider,
                             AuthProperties properties) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.evaluatorProvider = evaluatorProvider;
        this.localCache = new LocalPermissionCache<>("auth-local-cache",
                properties.getLocalPermissionCacheMinutes());
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
    public PermissionChangeNotifier permissionChangeNotifier(ApplicationEventPublisher applicationEventPublisher) {
        return new PermissionChangeNotifier(applicationEventPublisher);
    }

    /**
     * 创建角色权限加载器。
     *
     * @param redisStringOps Redis String 操作
     * @param properties   认证配置属性
     * @param notifier     权限变更事件发布器
     * @return 角色权限加载器实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisStringOps.class)
    public RolePermissionLoader rolePermissionLoader(RedisStringOps redisStringOps, AuthProperties properties,
                                                      PermissionChangeNotifier notifier) {
        LocalPermissionCache<RolePermissions> typedLocalCache = (LocalPermissionCache<RolePermissions>) (LocalPermissionCache<?>) localCache;
        return new RedisRolePermissionLoader(redisStringOps, properties, notifier, typedLocalCache);
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
     * 创建权限评估器。
     *
     * @param properties            认证配置属性
     * @param userInfoService       用户信息服务
     * @param rolePermissionLoader  角色权限加载器
     * @param cacheKeyStrategy      缓存 Key 生成策略
     * @return 权限评估器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RbacPermissionEvaluator rbacPermissionEvaluator(
            AuthProperties properties,
            RbacUserInfoService userInfoService,
            RolePermissionLoader rolePermissionLoader,
            CacheKeyStrategy cacheKeyStrategy,
            ObjectProvider<AuthMetricsCollector> metricsCollectorProvider
    ) {
        RbacPermissionEvaluator evaluator = new RbacPermissionEvaluator(properties, userInfoService, rolePermissionLoader);
        evaluator.setCacheKeyStrategy(cacheKeyStrategy);
        AuthMetricsCollector metricsCollector = metricsCollectorProvider.getIfAvailable();
        if (metricsCollector != null) {
            evaluator.setMetricsCollector(metricsCollector);
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
     * @param redisStringOps    Redis String 操作
     * @param properties      认证配置属性
     * @param userInfoService 用户信息服务
     * @return 数据权限解析器实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisStringOps.class)
    public DataPermissionResolver dataPermissionResolver(
            RedisStringOps redisStringOps,
            AuthProperties properties,
            RbacUserInfoService userInfoService
    ) {
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
    public AuthRowPermissionAspect authRowPermissionAspect(DataPermissionResolver resolver) {
        return new AuthRowPermissionAspect(resolver);
    }

    /**
     * 创建列权限解析器。
     *
     * @param redisStringOps    Redis String 操作
     * @param properties      认证配置属性
     * @param userInfoService 用户信息服务
     * @return 列权限解析器实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisStringOps.class)
    public ColumnPermissionResolver columnPermissionResolver(
            RedisStringOps redisStringOps,
            AuthProperties properties,
            RbacUserInfoService userInfoService
    ) {
        return new RedisRoleColumnPermissionResolver(redisStringOps, properties, userInfoService);
    }

    /**
     * 创建列权限切面。
     *
     * @param resolver                列权限解析器
     * @param desensitizationService  列脱敏服务
     * @param properties              认证配置属性
     * @return 列权限切面实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthColPermissionAspect authColPermissionAspect(
            ColumnPermissionResolver resolver,
            ColumnDesensitizationService desensitizationService,
            AuthProperties properties
    ) {
        return new AuthColPermissionAspect(resolver, desensitizationService, properties);
    }

    /**
     * 权限缓存失效监听器。
     *
     * <p>监听权限变更事件，在权限变更时自动清理 RbacPermissionEvaluator 中的缓存。
     *
     * @param evaluator      权限评估器
     * @return 缓存失效监听器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionCacheInvalidationListener permissionCacheInvalidationListener(
            RbacPermissionEvaluator evaluator
    ) {
        return new PermissionCacheInvalidationListener(evaluator);
    }

    /**
     * 权限缓存 Redis Pub/Sub 订阅容器。
     *
     * <p>当 {@link StringRedisTemplate} 可用时，监听 Redis 频道中的缓存失效消息，
     * 收到其他实例发布的消息后清除本地权限缓存，实现多实例缓存同步。
     *
     * @param redisTemplate Redis 模板
     * @param evaluator     权限评估器
     * @return Redis 消息监听容器
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public RedisMessageListenerContainer permissionCacheRedisSubscriber(
            StringRedisTemplate redisTemplate, RbacPermissionEvaluator evaluator) {
        return PermissionCacheInvalidationListener.createRedisSubscriber(redisTemplate, evaluator);
    }

    /**
     * Redis Keyspace Notification 权限缓存失效监听器。
     *
     * <p>当权限数据在 Redis 中被修改/删除时，通过 Keyspace Notification 精确触发缓存失效，
     * 替代原有的 Pub/Sub 广播模式，解决不保证送达的问题。
     *
     * @param evaluator           权限评估器
     * @param keyspaceProperties  Keyspace Notification 配置
     * @return 监听器实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ydsz.auth.keyspace-notification", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PermissionKeyspaceNotificationListener permissionKeyspaceNotificationListener(
            RbacPermissionEvaluator evaluator,
            KeyspaceNotificationProperties keyspaceProperties) {
        return new PermissionKeyspaceNotificationListener(evaluator);
    }

    /**
     * 创建 Token 黑名单服务。
     *
     * <p>当 {@link DistributedLocker} 可用时（ydsz-common-lock 在 classpath 上），
     * 使用其 {@code tryLock}/{@code unlock} 实现刷新锁，享有 Lua 原子释放与 WatchDog 续期能力；
     * 否则降级为原生 {@code setIfAbsent} 操作。
     *
     * @param redisStringOps    Redis String 操作
     * @param authProperties    认证配置属性
     * @param lockerProvider    分布式锁提供者（可选）
     * @return Token 黑名单服务实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisStringOps.class)
    public TokenBlacklistService tokenBlacklistService(RedisStringOps redisStringOps, AuthProperties authProperties,
                                                       ObjectProvider<DistributedLocker> lockerProvider) {
        DistributedLocker locker = lockerProvider.getIfAvailable();
        if (locker == null) {
            log.info("[AuthConfiguration] DistributedLocker 不可用，TokenBlacklistService 刷新锁降级为原生 setIfAbsent");
        }
        return new TokenBlacklistService(locker, redisStringOps, authProperties);
    }

    /**
     * 创建限流器 Bean（可选，默认 60 秒内 100 次请求）。
     *
     * <p>P0-1 架构优化：当 {@link RedisRateLimiter} 可用时委托其固定窗口限流实现
     * （分布式一致）；RedisRateLimiter 不可用时降级为本地内存实现。
     *
     * @param redisRateLimiterProvider Redis 限流器（可选）
     * @return RateLimiter 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ydsz.auth", name = "rate-limit-enabled", havingValue = "true", matchIfMissing = false)
    public RateLimiter authRateLimiter(ObjectProvider<RedisRateLimiter> redisRateLimiterProvider) {
        RedisRateLimiter redisRateLimiter = redisRateLimiterProvider.getIfAvailable();
        return new RateLimiter(100, 60, TimeUnit.SECONDS, redisRateLimiter);
    }

    /**
     * 创建 CSRF 验证器 Bean（可选，默认启用）。
     *
     * @return CsrfTokenValidator 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ydsz.auth", name = "csrf-enabled", havingValue = "true", matchIfMissing = false)
    public CsrfTokenValidator csrfTokenValidator() {
        return new CsrfTokenValidator(true);
    }

    /**
     * 创建 JWT Token 服务。
     *
     * @param tokenProperties           Token 配置属性
     * @param tokenBlacklistServiceProvider Token 黑名单服务（可选）
     * @param snowflakeIdGeneratorProvider 分布式 ID 生成器（用于 jti；缺失时构造器会给出明确报错）
     * @return Token 服务实例
     */
    @Bean
    @ConditionalOnClass(name = "io.jsonwebtoken.Jwts")
    @ConditionalOnProperty(prefix = "ydsz.auth.token", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(TokenService.class)
    public TokenService jwtTokenService(TokenProperties tokenProperties,
                                         ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider,
                                         ObjectProvider<SnowflakeIdGenerator> snowflakeIdGeneratorProvider) {
        return new JwtTokenService(tokenProperties, tokenBlacklistServiceProvider.getIfAvailable(),
                snowflakeIdGeneratorProvider.getIfAvailable());
    }

    /**
     * 创建权限模块 Micrometer 指标采集器 Bean
     *
     * @param meterRegistry Micrometer 指标注册中心
     * @return AuthMetricsCollector 实例
     */
    @Bean
    @ConditionalOnMissingBean(AuthMetricsCollector.class)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
    public AuthMetricsCollector authMetricsCollector(ObjectProvider<MeterRegistry> meterRegistryProvider) {
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
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnBean(RedisConnectionFactory.class)
    public AuthHealthIndicator authHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        return new AuthHealthIndicator(redisConnectionFactory);
    }

    /**
     * 创建权限变更事件发布器 Bean（支持 Redis Pub/Sub 跨节点通知）
     *
     * @param applicationEventPublisher Spring 事件发布器
     * @param redisTemplate Redis 模板
     * @return PermissionChangePublisher 实例
     */
    @Bean
    @ConditionalOnMissingBean(PermissionChangePublisher.class)
    @ConditionalOnBean(RedisTemplate.class)
    public PermissionChangePublisher permissionChangePublisher(
            ApplicationEventPublisher applicationEventPublisher,
            RedisTemplate<String, Object> redisTemplate) {
        return new PermissionChangePublisher(applicationEventPublisher, redisTemplate);
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
    public PermissionChangeCacheInvalidator permissionChangeCacheInvalidator(
            RolePermissionLoader rolePermissionLoader,
            DataPermissionResolver dataPermissionResolver,
            ColumnPermissionResolver columnPermissionResolver,
            RedisMessageListenerContainer redisMessageListenerContainer) {
        return new PermissionChangeCacheInvalidator(rolePermissionLoader, dataPermissionResolver,
                columnPermissionResolver, redisMessageListenerContainer);
    }

    /**
     * 创建权限预热初始化器 Bean
     *
     * @param properties 认证配置属性
     * @param rolePermissionLoader 角色权限加载器
     * @return PermissionWarmUpInitializer 实例
     */
    @Bean
    @ConditionalOnMissingBean(PermissionWarmUpInitializer.class)
    @ConditionalOnBean(RolePermissionLoader.class)
    public PermissionWarmUpInitializer permissionWarmUpInitializer(
            AuthProperties properties, RolePermissionLoader rolePermissionLoader) {
        return new PermissionWarmUpInitializer(properties, rolePermissionLoader);
    }

    /**
     * 获取本地缓存实例，供降级使用。
     *
     * @return 本地缓存实例
     */
    public LocalPermissionCache<Object> getLocalCache() {
        return localCache;
    }

    /**
     * 定时健康检查 Redis 连通性。
     *
     * <p>每分钟检查一次 Redis 连通状态，Redis 不可用时自动降级到本地缓存，
     * 并通知 RbacPermissionEvaluator 切换降级策略（ALLOW/DENY）。
     */
    @Scheduled(fixedRateString = "${ydsz.auth.health-check-interval:60000}")
    public void checkRedisHealth() {
        boolean redisOk = true;
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            log.debug("Redis 服务未配置，使用本地缓存兜底");
            redisOk = false;
        } else {
            try {
                var connectionFactory = redisTemplate.getConnectionFactory();
                if (connectionFactory == null) {
                    log.debug("Redis 连接工厂未初始化，降级到本地缓存");
                    redisOk = false;
                } else {
                    try (var connection = connectionFactory.getConnection()) {
                        connection.ping();
                        if (!localCache.isRedisAvailable()) {
                            log.info("Redis 健康检查恢复，切换回 Redis 缓存");
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Redis 健康检查异常，降级到本地缓存: {}", e.getMessage());
                redisOk = false;
            }
        }

        localCache.setRedisAvailable(redisOk);

        // 通知权限评估器切换降级策略
        RbacPermissionEvaluator evaluator = evaluatorProvider.getIfAvailable();
        if (evaluator != null) {
            evaluator.setRedisAvailable(redisOk);
        }
    }
}
