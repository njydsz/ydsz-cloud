package com.njydsz.system.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 配置（P1-7 跨实例缓存一致性）。
 *
 * <p>注册 {@link StringRedisTemplate} Bean，供 {@link
 * com.njydsz.system.server.cache.CacheInvalidationPublisher} 和 {@link
 * com.njydsz.system.server.cache.CacheInvalidationSubscriber} 实现 Redis Pub/Sub 缓存失效消息传递。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
public class RedisConfig {

  /**
   * 注册 StringRedisTemplate Bean（使用 Spring Boot 自动配置的 RedisConnectionFactory）。
   *
   * @param connectionFactory Redis 连接工厂（由 Spring Boot 自动配置）
   * @return StringRedisTemplate
   */
  @Bean
  public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
    return new StringRedisTemplate(connectionFactory);
  }
}
