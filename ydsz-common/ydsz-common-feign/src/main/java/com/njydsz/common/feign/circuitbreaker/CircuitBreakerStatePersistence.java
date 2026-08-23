package com.njydsz.common.feign.circuitbreaker;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 熔断器状态 Redis 持久化。
 *
 * <p>将熔断器状态（OPEN/HALF_OPEN/CLOSED 及指标快照）持久化到 Redis， 应用重启后可恢复熔断状态，避免重启后瞬间流量冲击已恢复的下游服务。
 *
 * <p>仅在 RedisStringOps 可用时启用，否则降级为无持久化模式。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class CircuitBreakerStatePersistence {

  private final ObjectProvider<RedisStringOps> redisStringOpsProvider;
  private final Duration ttl;

  private static final String KEY_PREFIX = "ydsz:feign:cb:";

  /**
   * 构造熔断状态持久化器。
   *
   * @param redisStringOpsProvider Redis 服务提供者（可选）
   * @param ttl 状态 TTL
   */
  public CircuitBreakerStatePersistence(
      ObjectProvider<RedisStringOps> redisStringOpsProvider, Duration ttl) {
    this.redisStringOpsProvider = redisStringOpsProvider;
    this.ttl = ttl;
  }

  /**
   * 读取指定服务的熔断器状态。
   *
   * @param serviceName 服务名称
   * @return 状态字符串（OPEN/HALF_OPEN/CLOSED），未持久化时返回 null
   */
  public String loadState(String serviceName) {
    RedisStringOps ops = redisStringOpsProvider.getIfAvailable();
    if (ops == null) {
      return null;
    }
    try {
      return ops.get(KEY_PREFIX + serviceName, String.class);
    } catch (Exception e) {
      log.warn("[Feign] 读取熔断状态失败: service={}", serviceName, e);
      return null;
    }
  }

  /**
   * 保存指定服务的熔断器状态。
   *
   * @param serviceName 服务名称
   * @param state 状态字符串
   */
  public void saveState(String serviceName, String state) {
    RedisStringOps ops = redisStringOpsProvider.getIfAvailable();
    if (ops == null) {
      return;
    }
    try {
      ops.set(KEY_PREFIX + serviceName, state, ttl);
    } catch (Exception e) {
      log.warn("[Feign] 保存熔断状态失败: service={}, state={}", serviceName, state, e);
    }
  }

  /**
   * 清除指定服务的熔断器状态。
   *
   * @param serviceName 服务名称
   */
  public void clearState(String serviceName) {
    RedisStringOps ops = redisStringOpsProvider.getIfAvailable();
    if (ops == null) {
      return;
    }
    try {
      ops.del(KEY_PREFIX + serviceName);
    } catch (Exception e) {
      log.warn("[Feign] 清除熔断状态失败: service={}", serviceName, e);
    }
  }
}
