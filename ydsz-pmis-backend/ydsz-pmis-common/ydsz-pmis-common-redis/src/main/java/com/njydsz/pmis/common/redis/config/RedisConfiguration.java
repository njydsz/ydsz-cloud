package com.njydsz.pmis.common.redis.config;

import com.njydsz.pmis.common.redis.annotation.RemiCacheableAspect;
import com.njydsz.pmis.common.redis.health.RedisHealthIndicator;
import com.njydsz.pmis.common.redis.interceptor.RedisRetryInterceptor;
import com.njydsz.pmis.common.redis.serializer.JacksonRedisSerializer;
import com.njydsz.pmis.common.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

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
 *   <li>使用 RemiJson 作为高性能序列化器</li>
 *   <li>支持连接池配置（commons-pool2）</li>
 *   <li>支持 SSL 配置</li>
 *   <li>客户端自动选择（通过 remi.redis.client.type 配置）</li>
 *   <li>Lettuce 自动重连 + 集群拓扑自适应刷新</li>
 *   <li>空闲连接驱逐策略</li>
 * </ul>
 *
 * <p><b>客户端配置示例（application.yml）：</b>
 * <pre>{@code
 * remi:
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
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@AutoConfiguration
@AutoConfigureBefore(DataRedisAutoConfiguration.class)
@RequiredArgsConstructor
@EnableConfigurationProperties({RedisProperties.class, RedisClientProperties.class})
public class RedisConfiguration {

    private final RedisConnectionFactoryConfigurer connectionFactoryConfigurer;
    private final RedisProperties redisProperties;

    /**
     * 创建 Redis 连接工厂
     *
     * <p>根据 RedisClientProperties 中的 clientType 配置，
     * 自动选择 Jedis 或 Lettuce 连接工厂。
     *
     * @param properties       Redis 配置属性
     * @param clientProperties 客户端配置属性
     * @return RedisConnectionFactory 实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedisConnectionFactory redisConnectionFactory(RedisProperties properties,
                                                          RedisClientProperties clientProperties) {
        return connectionFactoryConfigurer.createConnectionFactory(properties, clientProperties);
    }

    /**
     * 创建 Redis 重试工具 Bean
     *
     * <p>默认重试 3 次，指数退避（初始 100ms，最大 2s）
     * 仅针对读操作重试（write=false），避免写操作重复执行导致数据不一致。
     *
     * <p>重试参数可通过 {@code remi.redis.retry.*} 配置覆盖。
     *
     * @return RedisRetryInterceptor 实例
     */
    @Bean
    @ConditionalOnProperty(name = "remi.redis.retry.enabled", havingValue = "true", matchIfMissing = true)
    public RedisRetryInterceptor redisRetryInterceptor() {
        RedisProperties.Retry retry = redisProperties.getRetry();
        return new RedisRetryInterceptor(
                retry.getMaxRetries(),
                retry.getInitialBackoffMs(),
                retry.getMaxBackoffMs(),
                retry.isRetryOnWrite());
    }

    /**
     * 创建 Jackson 序列化器（默认）
     *
     * <p>当 {@code remi.redis.serializer=jackson} 或未配置时启用。
     * 使用 Jackson ObjectMapper 作为 Redis 值的序列化引擎，
     * 支持 Java 8 时间类型（JavaTimeModule）。
     *
     * <p><b>配置示例：</b>
     * <pre>{@code
     * remi:
     *   redis:
     *     serializer: jackson
     * }</pre>
     *
     * @return JacksonRedisSerializer 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(com.fasterxml.jackson.databind.ObjectMapper.class)
    @ConditionalOnProperty(name = "remi.redis.serializer", havingValue = "jackson", matchIfMissing = true)
    public JacksonRedisSerializer jacksonRedisSerializer() {
        return new JacksonRedisSerializer(Object.class);
    }

    /**
     * 创建纯净的 RedisTemplate
     *
     * <p>配置序列化方式：
     * <ul>
     *   <li>Key：使用 StringRedisSerializer，确保可读性</li>
     *   <li>Value：根据 {@code remi.redis.serializer} 配置选择序列化器（默认 jackson）</li>
     *   <li>Hash Key：使用 StringRedisSerializer</li>
     *   <li>Hash Value：使用与 Value 相同的序列化器</li>
     * </ul>
     *
     * <p>自 3.5.0 起，RedisTemplate 不再默认被 AOP 代理包装，以保持其作为基础数据访问 Bean 的纯净性。
     * 如需重试能力，请注入 {@code retryableRedisTemplate}，或将 {@code remi.redis.retry.proxy-template}
     * 设置为 {@code true} 恢复旧行为。
     *
     * @param connectionFactory Redis 连接工厂
     * @param valueSerializer   Redis 值序列化器（由 {@code remi.redis.serializer} 配置决定）
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
     * <p>该 Bean 默认不创建；需要时设置 {@code remi.redis.retry.proxy-template=true}。
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
    @ConditionalOnProperty(name = "remi.redis.retry.proxy-template", havingValue = "true")
    @ConditionalOnMissingBean(name = "retryableRedisTemplate")
    @SuppressWarnings("unchecked")
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
     * 注册 RemiCacheable 注解切面
     *
     * @param redisService Redis 服务
     * @return RemiCacheableAspect 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RemiCacheableAspect remiCacheableAspect(RedisService redisService) {
        return new RemiCacheableAspect(redisService);
    }

}
