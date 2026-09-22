package com.njydsz.common.redis.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.njydsz.common.redis.service.ops.ReactiveStringRedisOps;

/**
 * 响应式 Redis 自动配置
 *
 * <p>当 classpath 中存在 {@link ReactiveStringRedisTemplate} 且 {@link ReactiveRedisConnectionFactory} 时，
 * 自动创建 {@link ReactiveStringRedisOps} Bean，供 WebFlux 场景（如 ydsz-gateway）注入使用。
 *
 * <p>业务代码通过 {@code @RequiredArgsConstructor} + {@code private final ReactiveStringRedisOps reactiveRedis}
 * 即可获得响应式 Redis 操作能力，无需直接注入 Spring Data 的 {@link ReactiveStringRedisTemplate}。
 *
 * @author ydsz-team
 * @since 26.09.22
 * @see ReactiveStringRedisOps
 */
@AutoConfiguration
@AutoConfigureAfter(DataRedisAutoConfiguration.class)
@ConditionalOnClass(ReactiveStringRedisTemplate.class)
@ConditionalOnBean(ReactiveRedisConnectionFactory.class)
public class ReactiveRedisAutoConfiguration {

  /**
   * 创建响应式 String Redis 操作 Bean。
   *
   * @param reactiveTemplate 响应式 Redis 模板（由 Spring Boot 自动配置创建）
   * @return 响应式 Redis String 操作组件
   */
  @ConditionalOnMissingBean
  public ReactiveStringRedisOps reactiveStringRedisOps(ReactiveStringRedisTemplate reactiveTemplate) {
    return new ReactiveStringRedisOps(reactiveTemplate);
  }
}
