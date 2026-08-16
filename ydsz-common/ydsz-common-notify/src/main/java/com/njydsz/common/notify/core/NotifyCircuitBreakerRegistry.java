package com.njydsz.common.notify.core;

import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.safe.ratelimit.circuitbreaker.AbstractCircuitBreaker;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 渠道熔断器注册中心（P0-3）
 *
 * <p>管理各通知渠道的熔断器实例，提供统一的熔断器访问入口。 熔断器参数可通过 {@link com.njydsz.common.notify.config.NotifyProperties}
 * 配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class NotifyCircuitBreakerRegistry {

  private static final Logger log = LoggerFactory.getLogger(NotifyCircuitBreakerRegistry.class);

  private final ConcurrentMap<NotifyChannel, NotifyCircuitBreaker> breakers =
      new ConcurrentHashMap<>();

  private final int failureThreshold;
  private final long recoveryTimeoutMs;

  /** 使用默认参数创建注册中心 */
  public NotifyCircuitBreakerRegistry() {
    this(5, 60_000L);
  }

  /**
   * 创建注册中心
   *
   * @param failureThreshold 连续失败阈值
   * @param recoveryTimeoutMs 恢复等待时间（毫秒）
   */
  public NotifyCircuitBreakerRegistry(int failureThreshold, long recoveryTimeoutMs) {
    this.failureThreshold = failureThreshold;
    this.recoveryTimeoutMs = recoveryTimeoutMs;
    log.info(
        "[NotifyCircuitBreakerRegistry] 初始化完成, failureThreshold={}, recoveryTimeoutMs={}",
        failureThreshold,
        recoveryTimeoutMs);
  }

  /**
   * 获取指定渠道的熔断器（不存在则自动创建）
   *
   * @param channel 通知渠道
   * @return 熔断器实例
   */
  public NotifyCircuitBreaker getBreaker(NotifyChannel channel) {
    return breakers.computeIfAbsent(
        channel, ch -> new NotifyCircuitBreaker(ch, failureThreshold, recoveryTimeoutMs));
  }

  /**
   * 尝试获取指定渠道的熔断许可
   *
   * @param channel 通知渠道
   * @return true 表示允许通过，false 表示被熔断
   */
  public boolean tryAcquire(NotifyChannel channel) {
    return getBreaker(channel).tryAcquire();
  }

  /**
   * 记录渠道发送成功
   *
   * @param channel 通知渠道
   */
  public void recordSuccess(NotifyChannel channel) {
    getBreaker(channel).recordSuccess();
  }

  /**
   * 记录渠道发送失败
   *
   * @param channel 通知渠道
   */
  public void recordFailure(NotifyChannel channel) {
    getBreaker(channel).recordFailure();
  }

  /**
   * 获取所有渠道的熔断状态
   *
   * @return 渠道到熔断状态的映射
   */
  public ConcurrentMap<NotifyChannel, AbstractCircuitBreaker.State> getAllStates() {
    ConcurrentMap<NotifyChannel, AbstractCircuitBreaker.State> states = new ConcurrentHashMap<>();
    breakers.forEach((channel, breaker) -> states.put(channel, breaker.getState()));
    return states;
  }
}
