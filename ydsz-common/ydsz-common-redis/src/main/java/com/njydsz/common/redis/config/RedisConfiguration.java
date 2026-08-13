package com.njydsz.common.redis.config;

import lombok.RequiredArgsConstructor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.njydsz.common.redis.annotation.YdszCacheableAspect;
import com.njydsz.common.redis.health.RedisHealthIndicator;
import com.njydsz.common.redis.interceptor.RedisRetryInterceptor;
import com.njydsz.common.redis.metrics.RedisMetricsCollector;
import com.njydsz.common.redis.serializer.YdszJsonRedisSerializer;
import com.njydsz.common.redis.service.RedisBloomFilter;
import com.njydsz.common.redis.service.RedisCacheGuard;
import com.njydsz.common.redis.service.RedisDelayedQueue;
import com.njydsz.common.redis.service.RedisKeyExpirationDispatcher;
import com.njydsz.common.redis.service.RedisRateLimiter;
import com.njydsz.common.redis.service.RedisSnowflakeIdGenerator;
import com.njydsz.common.redis.service.ops.RedisAdvancedOps;
import com.njydsz.common.redis.service.ops.RedisCollectionOps;
import com.njydsz.common.redis.service.ops.RedisGeoOps;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.redis.service.ops.RedisPubSubOps;
import com.njydsz.common.redis.service.ops.RedisStreamOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.redis.service.ops.RedisTransactionOps;

/**
 * Redis 配置类
 *
 * <p>提供 Redis 连接工厂和 RedisTemplate 的配置，支持：
 * <ul>
 *   <li>单机模式（standalone）</li>
 *   <li>集群模式（cluster）</li>
 *   <li>哨兵模式（sentinel）</li>
 *   <li>多客户端支持（Jedis / Lettuce）</li>
 * </ul>
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>使用 YdszJson 作为高性能序列化器</li>
 *   <li>支持连接池配置（commons-pool2）</li>
 *   <li>支持 SSL 配置</li>
 *   <li>客户端自动选择（通过 ydsz.redis.client.type 配置）</li>
 *   <li>Lettuce 自动重连 + 集群拓扑自适应刷新</li>
 *   <li>空闲连接驱逐策略</li>
 * </ul>
 *
 * <p><b>客户端配置示例（application.yml）：</b>
 * <pre>{@code
 * ydsz:
 *   redis:
 *     client:
 *       type: jedis  # jedis 或 lettuce（默认 lettuce）
 *       pool:
 *         max-active: 16
 *         max-idle: 8
 *         min-idle: 2
 *       ssl:
 *         enabled: true
 * }</pre>
 *
 * <p>通过 {@code @AutoConfigureBefore} 确保在 Spring Boot 的
 * {@link DataRedisAutoConfiguration} 之前加载，避免与自动配置产生冲突。
 * 所有 Bean 均添加了 {@code @ConditionalOnMissingBean}，允许用户自定义覆盖。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@AutoConfigureBefore(DataRedisAutoConfiguration.class)
@RequiredArgsConstructor
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfiguration {

    private final RedisProperties redisProperties;

    /**
     * 创建 Redis 连接工厂
     *
     * <p>根据 {@link RedisProperties.Client} 中的 clientType 配置，
     * 自动选择 Jedis 或 Lettuce 连接工厂。
     *
     * @param properties Redis 配置属性（包含嵌套的 client 配置）
     * @return RedisConnectionFactory 实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedisConnectionFactory redisConnectionFactory(RedisProperties properties) {
        RedisConnectionFactoryConfigurer configurer = new RedisConnectionFactoryConfigurer();
        return configurer.createConnectionFactory(properties);
    }

    /**
     * 创建 Redis 重试工具 Bean
     *
     * <p>默认重试 3 次，指数退避（初始 100ms，最大 2s）
     * 仅针对读操作重试（write=false），避免写操作重复执行导致数据不一致。
     *
     * <p>重试参数可通过 {@code ydsz.redis.retry.*} 配置覆盖。
     *
     * @return RedisRetryInterceptor 实例
     */
    @Bean
    @ConditionalOnProperty(name = "ydsz.redis.retry.enabled", havingValue = "true", matchIfMissing = true)
    public RedisRetryInterceptor redisRetryInterceptor() {
        RedisProperties.Retry retry = redisProperties.getRetry();
        return new RedisRetryInterceptor(
                retry.getMaxRetries(),
                retry.getInitialBackoffMs(),
                retry.getMaxBackoffMs(),
                retry.isRetryOnWrite());
    }

    /**
     * 创建 YdszJson 序列化器（默认）
     *
     * <p>当 {@code ydsz.redis.serializer=ydsz-json} 或未配置时启用。
     * 使用 YdszJson 作为 Redis 值的序列化引擎，
     * 支持 Java 8 时间类型。
     *
     * <p><b>配置示例：</b>
     * <pre>{@code
     * ydsz:
     *   redis:
     *     serializer: ydsz-json
     * }</pre>
     *
     * @return YdszJsonRedisSerializer 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "ydsz.redis.serializer", havingValue = "ydsz-json", matchIfMissing = true)
    public YdszJsonRedisSerializer ydszJsonRedisSerializer() {
        return new YdszJsonRedisSerializer(Object.class);
    }

    /**
     * 创建纯净的 RedisTemplate
     *
     * <p>配置序列化方式：
     * <ul>
     *   <li>Key：使用 StringRedisSerializer，确保可读性</li>
     * <li>Value：根据 {@code ydsz.redis.serializer} 配置选择序列化器（默认 YdszJson）</li>
     *   <li>Hash Key：使用 StringRedisSerializer</li>
     *   <li>Hash Value：使用与 Value 相同的序列化器</li>
     * </ul>
     *
     * <p>自 3.5.0 起，RedisTemplate 不再默认被 AOP 代理包装，以保持其作为基础数据访问 Bean 的纯净性。
     * 如需重试能力，请注入 {@code retryableRedisTemplate}，或将 {@code ydsz.redis.retry.proxy-template}
     * 设置为 {@code true} 恢复旧行为。
     *
     * @param connectionFactory Redis 连接工厂
     * @param valueSerializer   Redis 值序列化器（由 {@code ydsz.redis.serializer} 配置决定）
     * @return 未代理的 RedisTemplate 实例
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                        RedisSerializer<Object> valueSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(valueSerializer);

        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 创建带重试能力的 RedisTemplate
     *
     * <p>使用 ProxyFactory 包装普通 RedisTemplate，使其方法调用经过
     * {@link RedisRetryInterceptor}，从而为 Redis 操作提供自动重试能力。
     *
     * <p>该 Bean 默认不创建；需要时设置 {@code ydsz.redis.retry.proxy-template=true}。
     * 注入方式：
     * <pre>{@code
     * @Resource(name = "retryableRedisTemplate")
     * private RedisTemplate<String, Object> retryableRedisTemplate;
     * }</pre>
     *
     * @param redisTemplate         普通 RedisTemplate
     * @param redisRetryInterceptor 重试拦截器
     * @return 已代理包装的 RedisTemplate 实例
     */
    @Bean("retryableRedisTemplate")
    @ConditionalOnBean(RedisRetryInterceptor.class)
    @ConditionalOnProperty(name = "ydsz.redis.retry.proxy-template", havingValue = "true")
    @ConditionalOnMissingBean(name = "retryableRedisTemplate")
    public RedisTemplate<String, Object> retryableRedisTemplate(RedisTemplate<String, Object> redisTemplate,
                                                                 RedisRetryInterceptor redisRetryInterceptor) {
        ProxyFactory proxyFactory = new ProxyFactory(redisTemplate);
        proxyFactory.addAdvice(redisRetryInterceptor);
        return (RedisTemplate<String, Object>) proxyFactory.getProxy();
    }

    /**
     * 创建 String 专用的 RedisTemplate
     *
     * <p>用于需要直接操作字符串的场景，避免序列化开销。
     *
     * @param connectionFactory Redis 连接工厂
     * @return StringRedisTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }

    /**
     * 注册 Redis 健康检查指示器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(HealthIndicator.class)
    public HealthIndicator redisHealthIndicator(RedisConnectionFactory connectionFactory) {
        return new RedisHealthIndicator(connectionFactory);
    }

    /**
     * 注册 YdszCacheable 注解切面
     *
     * @param redisStringOps Redis String 操作组件（用于读写缓存、SETNX 锁等）
     * @param redisTemplate  Redis 模板（用于执行 Lua 脚本释放锁）
     * @return YdszCacheableAspect 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public YdszCacheableAspect ydszCacheableAspect(RedisStringOps redisStringOps,
                                                   RedisTemplate<String, Object> redisTemplate) {
        return new YdszCacheableAspect(redisStringOps, redisTemplate);
    }

    /**
     * 注册 Redis 缓存防护组件
     *
     * <p>提供防穿透、防击穿、防雪崩三重缓存保护。
     * 空值缓存 TTL 从 {@link RedisProperties#getNullValueTtlSeconds()} 获取。
     * 锁租约、自旋退避等参数从 {@link RedisProperties.CacheGuard} 获取，
     * 可通过 {@code ydsz.redis.cache-guard.*} 配置项覆盖。
     *
     * @param stringOps      Redis String 操作组件（用于读写缓存、SETNX 锁等）
     * @param redisTemplate  Redis 模板（用于 WatchDog 续期）
     * @param redisProperties Redis 配置属性
     * @return RedisCacheGuard 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisCacheGuard redisCacheGuard(RedisStringOps stringOps,
                                           RedisTemplate<String, Object> redisTemplate,
                                           RedisProperties redisProperties) {
        return new RedisCacheGuard(stringOps, redisTemplate,
                redisProperties.getNullValueTtlSeconds(),
                redisProperties.getCacheGuard());
    }

    /**
     * 注册 Redis Pub/Sub 消息监听容器
     *
     * <p>用于支持 {@code RedisPubSubOps} 的订阅功能。
     * 默认使用 SimpleAsyncTaskExecutor，生产环境可通过自定义 Bean 覆盖。
     *
     * @param connectionFactory Redis 连接工厂
     * @return RedisMessageListenerContainer 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    // ============================ Redis Ops Beans ============================

    /**
     * 注册 String 类型键值操作封装（get/set/incr/expire 等基础命令）。
     *
     * <p>仅当容器已存在 {@code RedisTemplate} 时装配；依赖注入的 {@code RedisMetricsCollector} 通过
     * {@code ObjectProvider#getIfAvailable()} 惰性获取，未引入监控模块时传 null，对应操作降级为不采集指标。
     *
     * @param redisTemplate  基础模板，由容器注入，不会为 null
     * @param redisProperties 全局配置（含命令超时等），不会为 null
     * @param metricsProvider 指标采集器供应方，可能返回 null（缺失监控依赖时）
     * @return String 操作封装实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisStringOps.class)
    @ConditionalOnBean(RedisTemplate.class)
    public RedisStringOps redisStringOps(RedisTemplate<String, Object> redisTemplate,
                                          RedisProperties redisProperties,
                                          ObjectProvider<RedisMetricsCollector> metricsProvider) {
        return new RedisStringOps(redisTemplate, redisProperties, metricsProvider.getIfAvailable());
    }

    /**
     * 注册 Hash 结构操作封装（hGet/hSet/hMGet/hDel 等）。
     *
     * <p>指标采集器同样为可选依赖，缺失时降级不采集。其余装配条件同 {@link #redisStringOps}。
     *
     * @param redisTemplate  基础模板，不会为 null
     * @param redisProperties 全局配置，不会为 null
     * @param metricsProvider 指标采集器供应方，可能为 null
     * @return Hash 操作封装实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisHashOps.class)
    @ConditionalOnBean(RedisTemplate.class)
    public RedisHashOps redisHashOps(RedisTemplate<String, Object> redisTemplate,
                                      RedisProperties redisProperties,
                                      ObjectProvider<RedisMetricsCollector> metricsProvider) {
        return new RedisHashOps(redisTemplate, redisProperties, metricsProvider.getIfAvailable());
    }

    /**
     * 注册集合（List/Set/ZSet）操作封装（push/pop/sAdd/zRange 等）。
     *
     * <p>指标采集器可选，缺失时降级不采集。其余装配条件同 {@link #redisStringOps}。
     *
     * @param redisTemplate  基础模板，不会为 null
     * @param redisProperties 全局配置，不会为 null
     * @param metricsProvider 指标采集器供应方，可能为 null
     * @return 集合操作封装实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisCollectionOps.class)
    @ConditionalOnBean(RedisTemplate.class)
    public RedisCollectionOps redisCollectionOps(RedisTemplate<String, Object> redisTemplate,
                                                   RedisProperties redisProperties,
                                                   ObjectProvider<RedisMetricsCollector> metricsProvider) {
        return new RedisCollectionOps(redisTemplate, redisProperties, metricsProvider.getIfAvailable());
    }

    /**
     * 注册地理空间（GEO）操作封装（GEOADD / GEORADIUS 等经纬度与半径检索）。
     *
     * <p>指标采集器可选，缺失时降级不采集。其余装配条件同 {@link #redisStringOps}。
     *
     * @param redisTemplate  基础模板，不会为 null
     * @param redisProperties 全局配置，不会为 null
     * @param metricsProvider 指标采集器供应方，可能为 null
     * @return GEO 操作封装实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisGeoOps.class)
    @ConditionalOnBean(RedisTemplate.class)
    public RedisGeoOps redisGeoOps(RedisTemplate<String, Object> redisTemplate,
                                    RedisProperties redisProperties,
                                    ObjectProvider<RedisMetricsCollector> metricsProvider) {
        return new RedisGeoOps(redisTemplate, redisProperties, metricsProvider.getIfAvailable());
    }

    /**
     * 注册高级操作封装（Lua 脚本执行、SCAN 游标遍历、Bitmap/HyperLogLog 等底层命令）。
     *
     * <p>此类不接收指标采集器，其命令多属运维/批量场景，不纳入常规命令耗时统计。装配条件同其它 ops Bean。
     *
     * @param redisTemplate  基础模板，不会为 null
     * @param redisProperties 全局配置，不会为 null
     * @return 高级操作封装实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisAdvancedOps.class)
    @ConditionalOnBean(RedisTemplate.class)
    public RedisAdvancedOps redisAdvancedOps(RedisTemplate<String, Object> redisTemplate,
                                              RedisProperties redisProperties) {
        return new RedisAdvancedOps(redisTemplate, redisProperties);
    }

    /**
     * 注册发布/订阅操作封装（publish/subscribe）。
     *
     * <p>除 {@code RedisTemplate} 外还需 {@link RedisMessageListenerContainer} 已存在（见上方
     * {@link #redisMessageListenerContainer}），否则订阅能力无法装配，仅发布功能可用。
     *
     * @param redisTemplate  基础模板，不会为 null
     * @param listenerContainer 消息监听容器，不会为 null
     * @return Pub/Sub 操作封装实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisPubSubOps.class)
    @ConditionalOnBean({RedisTemplate.class, RedisMessageListenerContainer.class})
    public RedisPubSubOps redisPubSubOps(RedisTemplate<String, Object> redisTemplate,
                                          RedisMessageListenerContainer listenerContainer) {
        return new RedisPubSubOps(redisTemplate, listenerContainer);
    }

    /**
     * 注册 Redis Stream 流操作封装（XADD/XREADGROUP/消费者组等消息流场景）。
     *
     * <p>装配条件同其它 ops Bean，无独立指标采集器。
     *
     * @param redisTemplate  基础模板，不会为 null
     * @param redisProperties 全局配置，不会为 null
     * @return Stream 操作封装实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisStreamOps.class)
    @ConditionalOnBean(RedisTemplate.class)
    public RedisStreamOps redisStreamOps(RedisTemplate<String, Object> redisTemplate,
                                          RedisProperties redisProperties) {
        return new RedisStreamOps(redisTemplate, redisProperties);
    }

    /**
     * 注册事务操作封装（MULTI/EXEC/DISCARD 命令批处理）。
     *
     * <p>仅依赖 {@code RedisTemplate}，不涉及配置或指标。注意事务仅在非管线、同连接内有效。
     *
     * @param redisTemplate 基础模板，不会为 null
     * @return 事务操作封装实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisTransactionOps.class)
    @ConditionalOnBean(RedisTemplate.class)
    public RedisTransactionOps redisTransactionOps(RedisTemplate<String, Object> redisTemplate) {
        return new RedisTransactionOps(redisTemplate);
    }

    /**
     * 注册布隆过滤器封装（BF.ADD/BF.EXISTS，用于存在性判重与缓存穿透防护）。
     *
     * <p>建立在 RedisBloom 模块之上；若 Redis 未加载该模块，调用相关命令将抛异常，需由调用方保证模块可用。
     *
     * @param redisTemplate 基础模板，不会为 null
     * @return 布隆过滤器封装实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisBloomFilter.class)
    @ConditionalOnBean(RedisTemplate.class)
    public RedisBloomFilter redisBloomFilter(RedisTemplate<String, Object> redisTemplate) {
        return new RedisBloomFilter(redisTemplate);
    }

    /**
     * 注册延迟队列封装（基于 ZSet 的 score 时间戳 + 轮询弹出实现定时任务）。
     *
     * <p>指标采集器可选，缺失时降级不采集。消费端需自行保证幂等：轮询取出后若处理失败，消息不会自动重回队列。
     *
     * @param redisTemplate  基础模板，不会为 null
     * @param redisProperties 全局配置（含延迟队列轮询参数），不会为 null
     * @param metricsProvider 指标采集器供应方，可能为 null
     * @return 延迟队列封装实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisDelayedQueue.class)
    @ConditionalOnBean(RedisTemplate.class)
    public RedisDelayedQueue redisDelayedQueue(RedisTemplate<String, Object> redisTemplate,
                                                 RedisProperties redisProperties,
                                                 ObjectProvider<RedisMetricsCollector> metricsProvider) {
        return new RedisDelayedQueue(redisTemplate, redisProperties, metricsProvider.getIfAvailable());
    }

    /**
     * 注册分布式限流器封装（基于 Redis 原子计数/脚本实现的令牌桶或滑动窗口）。
     *
     * <p>依赖 {@code redisProperties} 中的限流阈值配置。Redis 不可用时限流判定无法执行，调用方应明确降级策略
     * （fail-open 放行还是 fail-closed 拒绝）。
     *
     * @param redisTemplate  基础模板，不会为 null
     * @param redisProperties 全局配置（含限流参数），不会为 null
     * @return 限流器封装实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisRateLimiter.class)
    @ConditionalOnBean(RedisTemplate.class)
    public RedisRateLimiter redisRateLimiter(RedisTemplate<String, Object> redisTemplate,
                                               RedisProperties redisProperties) {
        return new RedisRateLimiter(redisTemplate, redisProperties);
    }

    /**
     * 注册雪花 ID 生成器（依赖 Redis 注册并续约 workerId，避免多实例时钟回拨/workerId 冲突）。
     *
     * <p>workerId 的分配与保活由 WorkerIdAllocator 策略链完成；Redis 不可用时该生成器无法初始化 workerId，
     * 将导致 ID 生成失败。装配条件同其它 ops Bean。
     *
     * @param redisTemplate  基础模板，不会为 null
     * @param redisProperties 全局配置，不会为 null
     * @return 雪花 ID 生成器实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisSnowflakeIdGenerator.class)
    @ConditionalOnBean(RedisTemplate.class)
    public RedisSnowflakeIdGenerator redisSnowflakeIdGenerator(RedisTemplate<String, Object> redisTemplate,
                                                                  RedisProperties redisProperties) {
        return new RedisSnowflakeIdGenerator(redisTemplate, redisProperties);
    }

    /**
     * 注册 Key 过期事件监听调度器
     *
     * <p>扫描所有 Spring Bean 中标注了 {@code @RedisKeyExpireListener} 的方法，
     * 自动订阅 Redis Keyspace Notification 的过期事件，匹配 keyPattern 后回调。
     *
     * <p>需要通过 {@code ydsz.redis.key-expiration.enabled=true} 启用，
     * 且 Redis 服务端需配置 {@code notify-keyspace-events Ex}。
     *
     * @param listenerContainer 消息监听容器
     * @param redisProperties   Redis 配置
     * @return 过期事件调度器
     */
    @Bean
    @ConditionalOnMissingBean(RedisKeyExpirationDispatcher.class)
    @ConditionalOnBean({RedisTemplate.class, RedisMessageListenerContainer.class})
    @ConditionalOnProperty(prefix = "ydsz.redis.key-expiration", name = "enabled", havingValue = "true")
    public RedisKeyExpirationDispatcher redisKeyExpirationDispatcher(
            RedisMessageListenerContainer listenerContainer,
            RedisProperties redisProperties) {
        return new RedisKeyExpirationDispatcher(listenerContainer, redisProperties);
    }

}
