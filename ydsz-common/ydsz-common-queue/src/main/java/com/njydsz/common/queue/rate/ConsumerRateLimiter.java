package com.njydsz.common.queue.rate;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于令牌桶的消费者限流器
 *
 * <p>使用令牌桶算法控制消息消费速率，防止下游系统被突发流量击垮。每个消费者实例持有独立的限流器，支持动态调整限流速率。
 *
 * <p><b>算法：</b>基于 Guava {@code RateLimiter} 的「Bursty」变体，所有运算基于 {@code long} 纳秒刻度与 {@code double} 运算，
 * 避免在高频消费路径上产生任何对象分配或 BigDecimal 除法开销。
 *
 * <ul>
 *   <li>令牌以固定速率（stableIntervalNanos）填充</li>
 *   <li>每条消息消费前需获取一个令牌</li>
 *   <li>桶满（maxPermits）时多余令牌被丢弃，允许等同于 1 秒容量的短突发</li>
 *   <li>桶空时消费者 sleep 到下一个令牌的可用时刻（nextFreeTicketNanos）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * ConsumerRateLimiter limiter = new ConsumerRateLimiter(100); // 每秒 100 条
 * // 在消费循环中
 * limiter.acquire(); // 阻塞直到获取令牌
 * processMessage(message);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class ConsumerRateLimiter {

  /**
   * 相邻两个令牌之间的固定间隔（纳秒）。根据初始化时的稳定速率计算，一旦构造不再改变。
   */
  private final long stableIntervalNanos;

  /**
   * 桶容量（最大令牌数），等于构造时的 {@code permitsPerSecond}，允许短时突发。
   */
  private final double maxPermits;

  /** 当前已存储（未消费）的令牌数，可以为小数。 */
  private double storedPermits;

  /**
   * 下一个令牌可被请求者消费的系统时间（纳秒）。该字段构成「虚拟时钟」——当桶内令牌不足时会把这个时钟向前推，
   * 请求者需要真实等待到该时刻才能获取新的请求成功。
   */
  private long nextFreeTicketNanos;

  /** 是否启用限流（构造时 {@code permitsPerSecond} ≤ 0 则不启用）。 */
  private final boolean enabled;

  /**
   * 创建限流器
   *
   * @param permitsPerSecond 每秒允许通过的令牌数，≤ 0 表示不限流
   */
  public ConsumerRateLimiter(int permitsPerSecond) {
    if (permitsPerSecond <= 0) {
      this.stableIntervalNanos = 0L;
      this.maxPermits = 0.0;
      this.storedPermits = 0.0;
      this.nextFreeTicketNanos = 0L;
      this.enabled = false;
    } else {
      this.stableIntervalNanos = TimeUnit.SECONDS.toNanos(1) / permitsPerSecond;
      this.maxPermits = permitsPerSecond;
      this.storedPermits = permitsPerSecond;
      this.nextFreeTicketNanos = System.nanoTime();
      this.enabled = true;
    }
  }

  /**
   * 获取令牌，如果桶中没有令牌则阻塞等待
   *
   * <p>此方法会阻塞当前线程直到获取到一个令牌为止。如果限流器未启用（permitsPerSecond ≤ 0），则立即返回。
   *
   * <p>内部通过 {@link #reserve(int)} 计算需要等待的纳秒数；若大于 0 则执行 {@link Thread#sleep(long, int)} 精确等待。
   */
  public void acquire() {
    if (!enabled) {
      return;
    }
    long waitNanos = reserve(1);
    if (waitNanos > 0) {
      sleepNanos(waitNanos);
    }
  }

  /**
   * 尝试获取令牌，不阻塞
   *
   * @return true 表示获取成功，false 表示当前无可用令牌
   */
  public boolean tryAcquire() {
    if (!enabled) {
      return true;
    }
    return reserve(1) <= 0;
  }

  /**
   * 预留指定数量的令牌，返回请求者需要等待的纳秒数（0 表示立即可用）。
   *
   * <p>这是令牌桶算法的核心：把当前真实时间视为真实时钟，{@code nextFreeTicketNanos} 视为虚拟时钟。
   * 两者的差值（除以 {@code stableIntervalNanos}）即为过去这段时间内新增的令牌数，累加到 {@code storedPermits}。
   * 如果累加后已覆盖本次请求则立即可用；否则把虚拟时钟向前推，并返回请求者需要等待的时间。
   *
   * @param requestedPermits 本次请求预留的令牌数（当前所有调用处均传 1）
   * @return 请求者需要等待的纳秒数（0 表示立即可用；正值表示需要 sleep 的纳秒数）
   */
  private synchronized long reserve(int requestedPermits) {
    long nowNanos = System.nanoTime();
    refill(nowNanos);
    if (storedPermits >= requestedPermits) {
      storedPermits -= requestedPermits;
      return 0L;
    }
    double deficit = requestedPermits - storedPermits;
    long waitNanos = (long) (deficit * stableIntervalNanos);
    nextFreeTicketNanos += waitNanos;
    storedPermits = 0.0;
    return waitNanos;
  }

  /**
   * 根据真实时钟与虚拟时钟的差值补充令牌到桶内。
   *
   * <p>差值除以 {@code stableIntervalNanos} 即为新增的令牌数，累加至 {@code storedPermits}，但不超过 {@link #maxPermits}。
   *
   * @param nowNanos 当前系统时间（纳秒）
   */
  private void refill(long nowNanos) {
    long elapsed = nowNanos - nextFreeTicketNanos;
    if (elapsed <= 0) {
      return;
    }
    double permitsToAdd = (double) elapsed / stableIntervalNanos;
    storedPermits = Math.min(maxPermits, storedPermits + permitsToAdd);
    nextFreeTicketNanos = nowNanos;
  }

  /**
   * 获取当前可用令牌数（仅用于监控，不用于限流判断）。
   *
   * @return 可用令牌数
   */
  public synchronized double getAvailableTokens() {
    if (!enabled) {
      return 0.0;
    }
    refill(System.nanoTime());
    return storedPermits;
  }

  /**
   * 获取配置的限流速率（仅用于监控）。
   *
   * @return 每秒令牌数
   */
  public double getPermitsPerSecond() {
    return maxPermits;
  }

  /**
   * 判断限流器是否启用。
   *
   * @return 是否启用
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * 纳秒级 sleep。将纳秒拆分为毫秒 + 不足一毫秒的分数部分，调用 {@link Thread#sleep(long, int)} 精确等待。
   *
   * @param nanos 需要等待的纳秒数（≤ 0 时不做任何事）
   */
  private static void sleepNanos(long nanos) {
    long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
    int fractionNanos = (int) (nanos % TimeUnit.MILLISECONDS.toNanos(1));
    if (millis <= 0 && fractionNanos <= 0) {
      return;
    }
    try {
      Thread.sleep(millis, fractionNanos);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
