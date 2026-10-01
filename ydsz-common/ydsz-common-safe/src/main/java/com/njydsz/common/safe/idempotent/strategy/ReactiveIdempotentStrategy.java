package com.njydsz.common.safe.idempotent.strategy;

import reactor.core.publisher.Mono;

/**
 * 响应式幂等策略接口（WebFlux / Reactor 专用）。
 *
 * <p>定义响应式语义下的幂等锁获取与检查能力，供网关等响应式框架使用。
 * 与同步 {@link IdempotentStrategy} 互补：两者语义一致，仅返回类型适配不同编程模型。
 *
 * <p><b>架构定位：</b>ydsz-cloud 网关（WebFlux）使用此接口，Servlet 栈业务模块使用
 * {@link IdempotentStrategy}，两者共用底层 Redis 但编程模型隔离。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see IdempotentStrategy
 * @see RedisIdempotentStrategy
 */
public interface ReactiveIdempotentStrategy {

  /**
   * 尝试获取幂等锁（SETNX 语义）。
   *
   * <p>首次请求写入成功返回 {@code Mono.just(true)}，重复请求返回 {@code Mono.just(false)}。
   * Redis 异常时根据 fail-open 配置降级返回 {@code Mono.just(true)}（放行）。
   *
   * @param key          幂等键
   * @param expireSeconds 过期时间（秒），必须 &gt; 0
   * @return true=获取成功（首次请求），false=锁已被占用（重复请求），异常时按 fail-open 降级
   */
  Mono<Boolean> tryAcquire(String key, long expireSeconds);

  /**
   * 检查幂等键是否存在。
   *
   * @param key 幂等键
   * @return true=存在，false=不存在或检查失败
   */
  Mono<Boolean> exists(String key);
}
