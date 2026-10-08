package com.njydsz.gateway.config;

import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.metrics.RedisMetricsCollector;
import com.njydsz.common.redis.service.ops.ReactiveStringRedisOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.redis.tenant.TenantRedisKeyPrefixer;

/**
 * 网关 Redis 兜底配置。
 *
 * <p>网关为 WebFlux reactive 栈，使用 spring-boot-starter-data-redis-reactive 的 Lettuce 连接。
 * common-redis 的 {@code RedisConfiguration} 因 Jedis/Lettuce 客户端唯一性校验（{@code validateClientUniqueness}）
 * 无法在 reactive 栈中加载（编译期引用 Jedis 类但运行时缺少，或双客户端冲突），
 * 故本模块通过 {@code spring.autoconfigure.exclude} 排除它。
 *
 * <p>Spring Boot 4.x 的 {@code DataRedisAutoConfiguration} 因与 common-redis 同名旧类
 * 冲突未激活 Lettuce 连接工厂，故此处手动创建 Lettuce 连接工厂 + 同步/响应式 RedisTemplate + Ops 封装。
 *
 * @author ydsz-team
 * @since 26.10.08
 */
@Configuration
@SuppressWarnings({"rawtypes", "unchecked"})
public class GatewayRedisConfig {

  private static final int DEFAULT_REDIS_PORT = 6379;
  private static final int DEFAULT_REDIS_DB = 0;
  private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(3);

  /**
   * 创建 Lettuce 连接工厂。
   *
   * <p>在 Spring Data Redis 4.x 中，{@link LettuceConnectionFactory} 同时实现了
   * {@code RedisConnectionFactory} 和 {@code ReactiveRedisConnectionFactory}，
   * 可直接用于同步和响应式模板。替代已被排除的 common-redis {@code RedisConfiguration#redisConnectionFactory}。
   */
  @Bean
  public LettuceConnectionFactory redisConnectionFactory() {
    RedisStandaloneConfiguration standaloneConfig =
        new RedisStandaloneConfiguration("127.0.0.1", DEFAULT_REDIS_PORT);
    standaloneConfig.setDatabase(DEFAULT_REDIS_DB);

    GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
    poolConfig.setMaxTotal(16);
    poolConfig.setMaxIdle(8);
    poolConfig.setMinIdle(2);

    LettuceClientConfiguration clientConfig =
        LettucePoolingClientConfiguration.builder()
            .commandTimeout(DEFAULT_COMMAND_TIMEOUT)
            .poolConfig(poolConfig)
            .build();

    return new LettuceConnectionFactory(standaloneConfig, clientConfig);
  }

  /**
   * 创建同步 RedisTemplate（{@code @Primary} 确保覆盖自动配置的同名 Bean）。
   */
  @Bean
  @Primary
  public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    StringRedisSerializer serializer = new StringRedisSerializer();
    template.setKeySerializer(serializer);
    template.setValueSerializer(serializer);
    template.setHashKeySerializer(serializer);
    template.setHashValueSerializer(serializer);
    template.afterPropertiesSet();
    return template;
  }

  /**
   * 创建响应式 String RedisTemplate。
   *
   * <p>LettuceConnectionFactory 本身实现了 ReactiveRedisConnectionFactory（Spring Data Redis 4.x），
   * 直接转型注入给 ReactiveStringRedisTemplate 构造器。
   */
  @Bean
  public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
      LettuceConnectionFactory connectionFactory) {
    return new ReactiveStringRedisTemplate(connectionFactory);
  }

  /**
   * 创建同步 RedisStringOps（供 JwtTokenService 的 TokenBlacklistService 使用）。
   */
  @Bean
  public RedisStringOps redisStringOps(RedisTemplate<String, Object> redisTemplate) {
    RedisProperties redisProperties = new RedisProperties();
    redisProperties.setKeyPrefix("gateway");
    ObjectProvider nullProvider = new NullObjectProvider();
    return new RedisStringOps(
        redisTemplate, redisProperties,
        (ObjectProvider<RedisMetricsCollector>) (ObjectProvider) nullProvider,
        (ObjectProvider<TenantRedisKeyPrefixer>) (ObjectProvider) nullProvider);
  }

  /**
   * 创建响应式 RedisStringOps（供 WebSocketConnectionLimiter、IpAccessControlFilter 使用）。
   */
  @Bean
  public ReactiveStringRedisOps reactiveStringRedisOps(
      ReactiveStringRedisTemplate reactiveTemplate) {
    return new ReactiveStringRedisOps(reactiveTemplate);
  }

  /** 永远返回 null 的 ObjectProvider 实现。 */
  private static class NullObjectProvider implements ObjectProvider<Object> {
    @Override public Object getObject(Object... args) { return null; }
    @Override public Object getObject() { return null; }
    @Override public Object getIfUnique() { return null; }
    @Override public Object getIfAvailable() { return null; }
  }
}
