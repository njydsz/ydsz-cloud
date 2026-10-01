package com.njydsz.common.safe.idempotent.strategy;

import java.time.Duration;

import reactor.core.publisher.Mono;

import com.njydsz.common.redis.service.ops.ReactiveStringRedisOps;

/**
 * 基于 Redis 的响应式幂等策略实现（WebFlux 场景）。
 *
 * <p>使用 {@link ReactiveStringRedisOps#setIfAbsent} 实现原子 SETNX + TTL 语义，
 * 供 ydsz-gateway 等响应式框架使用。与同步 {@link RedisIdempotentStrategy} 互补：
 *
 * <ul>
 *   <li>同步版本使用 {@link org.springframework.data.redis.core.StringRedisTemplate} + Lua 脚本</li>
 *   <li>本实现使用 {@link ReactiveStringRedisOps}（基于 ReactiveStringRedisTemplate）</li>
 * </ul>
 *
 * <p><b>降级策略（fail-open）：</b>Redis 异常时自动降级返回 {@code true}（放行），
 * 避免幂等组件故障导致全链路不可用。降级行为对调用方透明。
 *
 * <h3>典型调用方</h3>
 * <ul>
 *   <li>{@code com.njydsz.gateway.filter.IdempotentGlobalFilter} — 网关幂等过滤器</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see ReactiveIdempotentStrategy
 * @see RedisIdempotentStrategy
 */
public class RedisReactiveIdempotentStrategy implements ReactiveIdempotentStrategy {

  private final ReactiveStringRedisOps reactiveStringRedisOps;

  /**
   * 构造 Redis 响应式幂等策略。
   *
   * @param reactiveStringRedisOps 响应式 Redis String 操作
   */
  public RedisReactiveIdempotentStrategy(ReactiveStringRedisOps reactiveStringRedisOps) {
    this.reactiveStringRedisOps = reactiveStringRedisOps;
  }

  @Override
  public Mono<Boolean> tryAcquire(String key, long expireSeconds) {
    if (expireSeconds <= 0) {
      return Mono.just(true);
    }
    return reactiveStringRedisOps
        .setIfAbsent(key, "1", Duration.ofSeconds(expireSeconds))
        .doOnError(
            e ->
                org.slf4j.LoggerFactory.getLogger(RedisReactiveIdempotentStrategy.class)
                    .warn(
                        "[ydsz-safe] [idempotent] [reactive] Redis 异常，fail-open 降级"
                            + " key={} cause={}",
                        key,
                        e.getMessage()))
        .onErrorReturn(true);
  }

  @Override
  public Mono<Boolean> exists(String key) {
    return reactiveStringRedisOps
        .hasKey(key)
        .onErrorReturn(false);
  }
}
