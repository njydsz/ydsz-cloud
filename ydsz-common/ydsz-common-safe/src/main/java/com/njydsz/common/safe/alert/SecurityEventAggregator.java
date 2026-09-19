package com.njydsz.common.safe.alert;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.safe.ip.IpAccessService;
import com.njydsz.common.thread.factory.InternalExecutorFactory;

/**
 * 安全事件自动响应聚合器。
 *
 * <p>监听 {@link SecurityEvent} 事件，基于滑动窗口统计同一 IP 的安全事件频率。 当同一 IP 在指定时间窗口内触发超过阈值数量的安全事件时，自动触发 IP 封禁。
 *
 * <p><b>自动封禁逻辑：</b>
 *
 * <ul>
 *   <li>每个 IP 维护一个原子计数器 + 窗口起始时间（lock-free 设计，避免 synchronized 竞争）
 *   <li>统计窗口内的事件数量，超过阈值时触发自动封禁并重置窗口
 *   <li>封禁时长根据事件严重级别递增：LOW=30min, MEDIUM=1h, HIGH=2h, CRITICAL=6h
 *   <li>已被封禁的 IP 的事件继续累计，触发升级封禁
 *   <li>定时清理过期的 IP 计数器，避免内存泄漏
 * </ul>
 *
 * <p><b>高并发优化：</b> 事件聚合热路径使用 {@link AtomicLong} 原子递增 + CAS 窗口重置，无需 synchronized 全局加锁，
 * 消除极端高频事件风暴下的串行瓶颈。
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   safe:
 *     auto-block:
 *       enabled: true
 *       threshold: 10
 *       window-seconds: 60
 * }</pre>
 *
 * <p><b>异步解耦设计：</b> 为避免事件处理链中的循环依赖（事件聚合器 → IP 封禁 → 可能的后续事件）， 自动封禁操作通过 {@link java.util.concurrent.BlockingQueue}
 * 异步投递到单线程消费者执行， 事件监听线程仅负责入队，不直接调用 {@link IpAccessService}。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see SecurityEvent
 * @see IpAccessService
 */
public class SecurityEventAggregator {

  private static final Logger LOG = LoggerFactory.getLogger(SecurityEventAggregator.class);

  /** 封禁命令队列容量 */
  private static final int BLOCK_QUEUE_CAPACITY = 256;

  private final IpAccessService ipAccessService;
  private final boolean enabled;
  private final int threshold;
  private final long windowMillis;

  /**
   * IP 事件计数器（lock-free 滑动窗口实现）。
   *
   * <p>使用 AtomicLong 维护 count 与 windowStart，通过原子递增 + CAS 重置窗口避免 synchronized。
   * {@link #incrementAndGet(long, int)} 返回值：>=0 表示当前窗口内的累计计数（未触发阈值）；
   * -1 表示已触发阈值（本次调用已触发自动封禁流程）。
   */
  private static class IpEventCounter {
    private final AtomicLong count = new AtomicLong(0);
    private final AtomicLong windowStartMillis = new AtomicLong(0);

    /**
     * 原子递增计数并检查是否达到阈值。
     *
     * @param now 当前时间（毫秒）
     * @param windowMillis 滑动窗口大小（毫秒）
     * @param threshold 触发阈值
     * @return 当前窗口累计计数；若返回 -1 表示已触发阈值并重置窗口
     */
    long incrementAndGet(long now, long windowMillis, int threshold) {
      long start = windowStartMillis.get();
      if (now - start > windowMillis) {
        // 窗口已过期，尝试 CAS 重置窗口
        if (windowStartMillis.compareAndSet(start, now)) {
          // CAS 成功：当前线程重置窗口，计数置 1
          count.set(1);
          return 1;
        }
        // CAS 失败：其他线程已重置，视为新窗口的第一个事件继续递增
      }
      long currentCount = count.incrementAndGet();
      if (currentCount >= threshold) {
        // 达到阈值：重置窗口并返回 -1 表示触发
        windowStartMillis.set(now);
        count.set(0);
        return -1;
      }
      return currentCount;
    }

    long getWindowStartMillis() {
      return windowStartMillis.get();
    }
  }

  private final ConcurrentHashMap<String, IpEventCounter> ipCounters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> blockedIpMap = new ConcurrentHashMap<>();
  private final AtomicLong autoBlockedCount = new AtomicLong(0);

  /** 异步封禁命令队列 */
  private final LinkedBlockingQueue<BlockCommand> blockQueue =
      new LinkedBlockingQueue<>(BLOCK_QUEUE_CAPACITY);

  /** 封禁消费者单线程池 */
  private final ExecutorService blockConsumerExecutor;

  /** 封禁命令（内部模型） */
  private record BlockCommand(String ip, long blockSeconds) {}

  /**
   * 构造方法。
   *
   * @param ipAccessService IP 访问控制服务（可为 null，未启用 IP 访问控制时降级为仅日志）
   * @param enabled 是否启用自动封禁
   * @param threshold 触发自动封禁的事件数量阈值
   * @param windowSeconds 滑动窗口大小（秒）
   */
  public SecurityEventAggregator(
      IpAccessService ipAccessService, boolean enabled, int threshold, long windowSeconds) {
    this.ipAccessService = ipAccessService;
    this.enabled = enabled;
    this.threshold = threshold;
    this.windowMillis = windowSeconds * 1000L;

    // 单线程守护线程，专用于消费自动封禁命令队列，生命周期随 Bean 销毁，统一使用 InternalExecutorFactory
    this.blockConsumerExecutor =
        InternalExecutorFactory.newFixedThreadPool("safe-auto-block-consumer", 1);
    this.blockConsumerExecutor.submit(this::consumeBlockCommands);

    LOG.info(
        "安全事件自动响应聚合器初始化: enabled={}, threshold={}, window={}s", enabled, threshold, windowSeconds);
  }

  /** 封禁命令消费循环 */
  private void consumeBlockCommands() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        BlockCommand command = blockQueue.poll(1, TimeUnit.SECONDS);
        if (command != null) {
          executeBlock(command);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        LOG.error("【安全事件自动响应】封禁命令消费异常: {}", e.getMessage());
      }
    }
  }

  /** 执行 IP 封禁 */
  private void executeBlock(BlockCommand command) {
    if (ipAccessService == null) {
      return;
    }
    try {
      ipAccessService.blockIp(command.ip(), command.blockSeconds(), TimeUnit.SECONDS);
    } catch (Exception e) {
      LOG.error("【安全事件自动响应】IP 自动封禁失败: ip={}, error={}", command.ip(), e.getMessage());
    }
  }

  /**
   * 监听安全事件。
   *
   * <p>使用 lock-free 原子递增替代 synchronized + Queue 设计。事件聚合整个过程无需全局锁，
   * 仅通过 {@link AtomicLong#incrementAndGet()} 和 {@link AtomicLong#compareAndSet} 保证一致性。
   *
   * @param event 安全事件
   */
  @EventListener
  public void onSecurityEvent(SecurityEvent event) {
    if (!enabled) {
      return;
    }

    String sourceIp = event.getSourceIp();
    if (sourceIp == null || sourceIp.isEmpty()) {
      return;
    }

    long now = System.currentTimeMillis();
    IpEventCounter counter = ipCounters.computeIfAbsent(sourceIp, k -> new IpEventCounter());
    long result = counter.incrementAndGet(now, windowMillis, threshold);

    if (result < 0) {
      // result == -1 表示已触发阈值
      triggerAutoBlock(sourceIp, event.getSeverity(), threshold);
    }
  }

  /**
   * 触发自动封禁。
   *
   * <p>将封禁命令异步投递到队列，由单线程消费者执行，避免事件风暴放大。
   *
   * @param ip 来源 IP
   * @param severity 最后一个事件的严重级别
   * @param count 窗口内事件数量
   */
  private void triggerAutoBlock(String ip, SecurityEvent.Severity severity, int count) {
    long blockSeconds = calculateBlockDuration(severity);

    Long previousBlock = blockedIpMap.get(ip);
    if (previousBlock != null && previousBlock > System.currentTimeMillis()) {
      blockSeconds *= 2;
    }

    blockedIpMap.put(ip, System.currentTimeMillis() + blockSeconds * 1000);
    autoBlockedCount.incrementAndGet();

    LOG.warn(
        "【安全事件自动响应】IP {} 在 {} 秒内触发 {} 次安全事件（严重级别: {}），自动封禁 {} 秒",
        ip,
        windowMillis / 1000,
        count,
        severity,
        blockSeconds);

    // 异步投递封禁命令，解耦事件处理链
    boolean offered = blockQueue.offer(new BlockCommand(ip, blockSeconds));
    if (!offered) {
      LOG.warn("【安全事件自动响应】封禁命令队列已满，降级为同步执行: ip={}", ip);
      executeBlock(new BlockCommand(ip, blockSeconds));
    }
  }

  /** 根据严重级别计算封禁时长（秒） */
  private long calculateBlockDuration(SecurityEvent.Severity severity) {
    return switch (severity) {
      case LOW -> 1800;
      case MEDIUM -> 3600;
      case HIGH -> 7200;
      case CRITICAL -> 21600;
    };
  }

  /**
   * 定时清理过期的 IP 计数器记录和已过期封禁记录。
   *
   * <p>每 60 秒执行一次，清理超过滑动窗口的 IP 计数器，避免内存泄漏。
   * 清理操作本身无锁安全：ConcurrentHashMap 的 remove 和 entrySet 迭代本身就是线程安全的。
   */
  @Scheduled(
      fixedRateString = "${ydsz.safe.auto-block.clean-interval:60000}",
      initialDelayString = "${ydsz.safe.auto-block.clean-initial-delay:60000}")
  public void cleanExpired() {
    long now = System.currentTimeMillis();
    long windowStart = now - windowMillis;

    int cleanedEntries = 0;
    for (var entry : ipCounters.entrySet()) {
      IpEventCounter counter = entry.getValue();
      // 清理超过 2 倍窗口未活跃的计数器（确保窗口完全过期后的一个完整周期再清理）
      if (counter.getWindowStartMillis() < windowStart - windowMillis) {
        ipCounters.remove(entry.getKey());
        cleanedEntries++;
      }
    }

    blockedIpMap.entrySet().removeIf(entry -> entry.getValue() < now);

    if (cleanedEntries > 0 && LOG.isDebugEnabled()) {
      LOG.debug(
          "【安全事件自动响应】清理过期事件记录: 清理IP={}, 活跃IP={}, 累计封禁={}",
          cleanedEntries,
          ipCounters.size(),
          autoBlockedCount.get());
    }
  }

  /** 销毁回调：关闭异步消费者线程 */
  @PreDestroy
  public void destroy() {
    blockConsumerExecutor.shutdownNow();
  }

  /**
   * 获取累计自动封禁 IP 数量。
   *
   * @return 累计封禁次数
   */
  public long getAutoBlockedCount() {
    return autoBlockedCount.get();
  }

  /**
   * 获取当前活跃 IP 数量（有事件记录但未被清理的 IP）。
   *
   * @return 活跃 IP 数量
   */
  public int getActiveIpCount() {
    return ipCounters.size();
  }
}
