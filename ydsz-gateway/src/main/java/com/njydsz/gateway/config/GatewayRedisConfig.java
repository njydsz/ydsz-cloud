package com.njydsz.gateway.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.metrics.RedisMetricsCollector;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.redis.tenant.TenantRedisKeyPrefixer;

/**
 * 网关 Redis 兜底配置。
 *
 * <p>网关为 WebFlux reactive 栈，使用 spring-boot-starter-data-redis-reactive 的 Lettuce 连接。
 * common-redis 的 {@code RedisConfiguration} 因 Jedis/Lettuce 客户端唯一性校验（{@code validateClientUniqueness}）
 * 无法在 reactive 栈中加载（编译期引用 Jedis 类但运行时缺少，或双客户端冲突），
 * 故本模块通过 {@code spring.autoconfigure.exclude} 排除它，改由此类仅创建网关必需的 {@link RedisStringOps}，
 * 供 {@code JwtConfiguration#tokenBlacklistService} 注入使用。
 *
 * <p>{@link RedisTemplate} 由 Spring Boot reactive 自动配置提供（Lettuce 连接工厂），
 * 此处仅依赖它构建 {@code RedisStringOps}。
 *
 * @author ydsz-team
 * @since 26.10.08
 */
@AutoConfiguration
@SuppressWarnings("rawtypes")
public class GatewayRedisConfig {

  /**
   * 创建网关使用的 RedisStringOps Bean。
   *
   * <p>参数 metricsProvider / tenantPrefixerProvider 均为永远返回 null 的 ObjectProvider，
   * 因为网关场景不涉及指标采集和租户 Key 前缀特性，RedisStringOps 内部调用 getIfAvailable() 时
   * 会得到 null，自动降级为无指标 / 无前缀模式。
   *
   * @param redisTemplate Spring Boot 自动配置的 RedisTemplate（Lettuce）
   * @return RedisStringOps 实例
   */
  @SuppressWarnings("unchecked")
  @Bean
  public RedisStringOps redisStringOps(RedisTemplate<String, Object> redisTemplate) {
    RedisProperties redisProperties = new RedisProperties();
    redisProperties.setKeyPrefix("gateway");
    return new RedisStringOps(
        redisTemplate,
        redisProperties,
        (ObjectProvider<RedisMetricsCollector>) (ObjectProvider) new NullObjectProvider(),
        (ObjectProvider<TenantRedisKeyPrefixer>) (ObjectProvider) new NullObjectProvider());
  }

  /** 永远返回 null 的 ObjectProvider 实现。 */
  private static class NullObjectProvider implements ObjectProvider<Object> {
    @Override
    public Object getObject(Object... args) { return null; }
    @Override
    public Object getObject() { return null; }
    @Override
    public Object getIfUnique() { return null; }
    @Override
    public Object getIfAvailable() { return null; }
  }
}
