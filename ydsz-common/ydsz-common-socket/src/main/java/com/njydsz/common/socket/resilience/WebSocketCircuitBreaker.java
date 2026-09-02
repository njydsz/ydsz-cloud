package com.njydsz.common.socket.resilience;

import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.safe.ratelimit.circuitbreaker.AbstractCircuitBreaker;

/**
 * WebSocket 模块轻量级熔断器。
 *
 * <p>基于滑动窗口失败率统计，达到阈值后触发熔断。 继承 {@link AbstractCircuitBreaker} 复用核心状态机逻辑。
 *
 * <p>状态流转：
 *
 * <ul>
 *   <li>CLOSED → 失败率超过阈值 → OPEN
 *   <li>OPEN → 等待半开时间 → HALF_OPEN
 *   <li>HALF_OPEN → 探测成功 → CLOSED
 *   <li>HALF_OPEN → 探测失败 → OPEN
 * </ul>
 *
 * <h3>26.09.01 变更</h3>
 *
 * <p>自 26.09.01 起，继承 {@link AbstractCircuitBreaker}（ydsz-common-safe）， 复用标准三态状态机，移除自研 CAS 状态管理代码。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class WebSocketCircuitBreaker extends AbstractCircuitBreaker {

  /** 滑动窗口大小（调用次数） */
  private final int slidingWindowSize;

  private final AtomicInteger failureCount = new AtomicInteger(0);
  private final AtomicInteger totalCount = new AtomicInteger(0);

  /**
   * 构造 WebSocket 熔断器
   *
   * @param name 熔断器名称
   * @param failureRateThreshold 失败率阈值（0~1.0）
   * @param slidingWindowSize 滑动窗口大小（调用次数）
   * @param halfOpenAfterMillis OPEN 状态等待时间（毫秒）
   */
  public WebSocketCircuitBreaker(
      String name, double failureRateThreshold, int slidingWindowSize, long halfOpenAfterMillis) {
    super(new Config(name, failureRateThreshold, halfOpenAfterMillis, 1));
    this.slidingWindowSize = slidingWindowSize;
    log.info(
        "[WS-CircuitBreaker] '{}' 初始化: threshold={}, window={}, halfOpenAfter={}ms",
        name,
        failureRateThreshold,
        slidingWindowSize,
        halfOpenAfterMillis);
  }

  @Override
  protected boolean evaluateThreshold() {
    int total = totalCount.get();
    if (total < slidingWindowSize) {
      return false;
    }
    int failures = failureCount.get();
    double rate = (double) failures / total;
    // 窗口已满，重置统计
    failureCount.set(0);
    totalCount.set(0);
    return rate >= config.getFailureThreshold();
  }

  @Override
  protected void onSuccessRecord() {
    totalCount.incrementAndGet();
  }

  @Override
  protected void onFailureRecord() {
    failureCount.incrementAndGet();
    totalCount.incrementAndGet();
  }

  @Override
  protected void resetStats() {
    failureCount.set(0);
    totalCount.set(0);
  }
}
