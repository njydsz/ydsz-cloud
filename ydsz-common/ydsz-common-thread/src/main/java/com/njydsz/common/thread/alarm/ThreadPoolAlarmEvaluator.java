package com.njydsz.common.thread.alarm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.thread.alarm.AlarmEvent.AlarmDimension;
import com.njydsz.common.thread.alarm.AlarmEvent.Severity;

/**
 * 线程池告警事件评估器基类。
 *
 * <p>周期性采集已注册线程池的实时指标，按阈值评估是否生成告警事件。
 *
 * <p>告警去重：同一线程池同一维度在 {@code alarmSuppressWindowMs} 窗口内仅触发一次，防止告警风暴。
 *
 * <p>使用方式：由 {@code ThreadPoolAlarmAutoConfiguration} 自动装配并定期执行评估。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see AlarmNotifier
 * @see DefaultAlarmNotifier
 */
public abstract class ThreadPoolAlarmEvaluator {

  private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolAlarmEvaluator.class);

  private final AlarmNotifier alarmNotifier;
  private final ScheduledExecutorService scheduler;
  private final AlarmEvalConfig evalConfig;

  /** 上次告警时间戳（用于去重 suppress）：poolName + ":" + dimension → epoch millis */
  private final ConcurrentMap<String, Long> lastAlarmTime = new ConcurrentHashMap<>(16);

  /** 累计拒绝计数快照：用于速率计算 */
  private final ConcurrentMap<String, AtomicLong> rejectRateCounter = new ConcurrentHashMap<>(16);

  /** 累计慢任务计数快照：用于速率计算 */
  private final ConcurrentMap<String, AtomicLong> slowTaskRateCounter = new ConcurrentHashMap<>(16);

  /** 是否已启动 */
  private volatile boolean started;

  /**
   * 构造告警评估器。
   *
   * @param alarmNotifier 告警通知器（不可为 null）
   * @param scheduler 调度器（不可为 null）
   * @param evalConfig 评估配置（不可为 null）
   */
  protected ThreadPoolAlarmEvaluator(
      AlarmNotifier alarmNotifier, ScheduledExecutorService scheduler, AlarmEvalConfig evalConfig) {
    this.alarmNotifier = alarmNotifier;
    this.scheduler = scheduler;
    this.evalConfig = evalConfig;
  }

  /**
   * 启动周期性评估任务。
   *
   * <p>首次延迟 {@code evalIntervalMs} 后开始，避免启动阶段线程池未完全就绪时产生误报。
   */
  public void start() {
    if (started) {
      return;
    }
    started = true;
    scheduler.scheduleWithFixedDelay(
        this::safeEvaluateAll,
        evalConfig.getEvalIntervalMs(),
        evalConfig.getEvalIntervalMs(),
        TimeUnit.MILLISECONDS);
    LOG.info(
        "[ydsz-thread-alarm] 告警评估器已启动，评估间隔: {}ms，suppress窗口: {}ms",
        evalConfig.getEvalIntervalMs(),
        evalConfig.getAlarmSuppressWindowMs());
  }

  /**
   * 关闭评估器（停止调度任务）。
   */
  public void shutdown() {
    started = false;
    scheduler.shutdown();
    LOG.info("[ydsz-thread-alarm] 告警评估器已关闭");
  }

  /**
   * 获取当前监控的线程池。
   *
   * <p>子类实现此方法提供被评估的线程池集合。
   *
   * @return 线程池映射；不会为 null
   */
  protected abstract Map<String, ? extends Object> getMonitoredExecutors();

  /**
   * 从线程池实例获取 ThreadPoolExecutor。
   *
   * <p>子类实现此方法以支持 ThreadPoolTaskExecutor / ExecutorService 等不同类型。
   *
   * @param executor 线程池对象
   * @return 对应的 ThreadPoolExecutor；无法转换时返回 null
   */
  protected abstract java.util.concurrent.ThreadPoolExecutor unwrapExecutor(Object executor);

  /**
   * 安全评估所有线程池（捕获异常防止调度器静默吞掉错误）。
   */
  private void safeEvaluateAll() {
    try {
      evaluateAllInternal();
    } catch (Exception e) {
      LOG.warn("[ydsz-thread-alarm] 评估过程异常: {}", e.getMessage());
    }
  }

  /**
   * 评估所有已注册线程池。
   */
  private void evaluateAllInternal() {
    Map<String, ? extends Object> executors = getMonitoredExecutors();
    if (executors.isEmpty()) {
      return;
    }
    List<AlarmEvent> events = new ArrayList<>(executors.size() * 2);
    for (Map.Entry<String, ? extends Object> entry : executors.entrySet()) {
      java.util.concurrent.ThreadPoolExecutor pool = unwrapExecutor(entry.getValue());
      if (pool != null) {
        evaluateSinglePool(entry.getKey(), pool, events);
      }
    }
    if (!events.isEmpty()) {
      alarmNotifier.notifyBatch(events);
    }
  }

  /**
   * 评估单个线程池。
   *
   * @param poolName 线程池名称
   * @param pool 线程池执行器
   * @param outEvents 收集生成的告警事件
   */
  private void evaluateSinglePool(
      String poolName,
      java.util.concurrent.ThreadPoolExecutor pool,
      List<AlarmEvent> outEvents) {
    int activeCount = pool.getActiveCount();
    int maxSize = pool.getMaximumPoolSize();
    int poolSize = pool.getPoolSize();
    int queueSize = pool.getQueue() != null ? pool.getQueue().size() : 0;
    int queueCapacity = queueSize + (pool.getQueue() != null ? pool.getQueue().remainingCapacity() : 0);

    // 维度1：队列使用率超阈值
    if (queueCapacity > 0) {
      double queueUsage = (double) queueSize / queueCapacity;
      maybeEmitAlarm(
          poolName,
          AlarmDimension.QUEUE_USAGE,
          queueUsage,
          evalConfig.getQueueUsageThreshold(),
          "队列使用率 " + String.format("%.1f%%", queueUsage * 100) + " 超过阈值 "
              + String.format("%.1f%%", evalConfig.getQueueUsageThreshold() * 100),
          queueUsage >= evalConfig.getQueueUsageThreshold() * 1.5,
          outEvents);
    }

    // 维度2：活跃线程数接近最大值
    if (maxSize > 0) {
      double activeRatio = (double) activeCount / maxSize;
      maybeEmitAlarm(
          poolName,
          AlarmDimension.ACTIVE_NEAR_MAX,
          activeRatio,
          evalConfig.getActiveNearMaxThreshold(),
          "活跃线程数 " + activeCount + "/" + maxSize + " 接近最大值 ("
              + String.format("%.1f%%", activeRatio * 100) + ")",
          activeRatio >= evalConfig.getActiveNearMaxThreshold() * 1.5,
          outEvents);
    }

    // 维度3：活跃度为 0 且有任务堆积（异常状态）
    if (activeCount == 0 && poolSize > 0 && queueSize > 0) {
      maybeEmitAlarm(
          poolName,
          AlarmDimension.LIVENESS_ZERO,
          0.0,
          evalConfig.getLivenessZeroThreshold(),
          "线程池活跃线程为 0 但队列有 " + queueSize + " 个待执行任务",
          false,
          outEvents);
    }
  }

  /**
   * 检查是否应该发出告警（含 suppress 去重）。
   *
   * @param poolName 线程池名称
   * @param dimension 告警维度
   * @param currentValue 当前值
   * @param threshold 阈值
   * @param message 告警消息
   * @param critical 是否升级严重级别
   * @param outEvents 输出事件列表
   */
  private void maybeEmitAlarm(
      String poolName,
      AlarmDimension dimension,
      double currentValue,
      double threshold,
      String message,
      boolean critical,
      List<AlarmEvent> outEvents) {
    if (currentValue < threshold) {
      return;
    }

    // suppress 去重检查
    String suppressKey = poolName + ":" + dimension.name();
    long now = System.currentTimeMillis();
    Long lastTime = lastAlarmTime.get(suppressKey);
    if (lastTime != null && (now - lastTime) < evalConfig.getAlarmSuppressWindowMs()) {
      return;
    }
    lastAlarmTime.put(suppressKey, now);

    AlarmEvent event =
        AlarmEvent.builder(poolName, dimension)
            .severity(critical ? Severity.CRITICAL : Severity.WARN)
            .currentValue(currentValue)
            .threshold(threshold)
            .message(message)
            .build();
    outEvents.add(event);
  }

  /**
   * 接收拒绝策略的即时告警（由 {@code MeteredRejectedHandler} 调用）。
   *
   * <p>拒绝事件具有即时性，但仍经过 suppress 去重防止短时间内大量拒绝产生告警风暴。
   *
   * @param poolName 线程池名称
   * @param rejectedCount 当前拒绝计数
   */
  public void onRejectedExecution(String poolName, long rejectedCount) {
    String suppressKey = poolName + ":REJECT_RATE";
    long now = System.currentTimeMillis();
    Long lastTime = lastAlarmTime.get(suppressKey);
    if (lastTime != null && (now - lastTime) < evalConfig.getAlarmSuppressWindowMs()) {
      return;
    }
    lastAlarmTime.put(suppressKey, now);

    AlarmEvent event =
        AlarmEvent.builder(poolName, AlarmDimension.REJECT_RATE)
            .severity(Severity.CRITICAL)
            .currentValue(rejectedCount)
            .threshold(0)
            .message("线程池拒绝任务执行 (累计拒绝 " + rejectedCount + " 次)，请检查负载和队列配置")
            .build();
    alarmNotifier.notify(event);
  }

  /**
   * 评估配置（不可变值对象）。
   */
  public static final class AlarmEvalConfig {
    private final long evalIntervalMs;
    private final long alarmSuppressWindowMs;
    private final double queueUsageThreshold;
    private final double activeNearMaxThreshold;
    private final double livenessZeroThreshold;

    private AlarmEvalConfig(Builder builder) {
      this.evalIntervalMs = builder.evalIntervalMs;
      this.alarmSuppressWindowMs = builder.alarmSuppressWindowMs;
      this.queueUsageThreshold = builder.queueUsageThreshold;
      this.activeNearMaxThreshold = builder.activeNearMaxThreshold;
      this.livenessZeroThreshold = builder.livenessZeroThreshold;
    }

    public long getEvalIntervalMs() {
      return evalIntervalMs;
    }

    public long getAlarmSuppressWindowMs() {
      return alarmSuppressWindowMs;
    }

    public double getQueueUsageThreshold() {
      return queueUsageThreshold;
    }

    public double getActiveNearMaxThreshold() {
      return activeNearMaxThreshold;
    }

    public double getLivenessZeroThreshold() {
      return livenessZeroThreshold;
    }

    public static Builder builder() {
      return new Builder();
    }

    /** {@link AlarmEvalConfig} 构造器。 */
    public static final class Builder {
      private long evalIntervalMs = 30_000L;
      private long alarmSuppressWindowMs = 120_000L;
      private double queueUsageThreshold = 0.8;
      private double activeNearMaxThreshold = 0.9;
      private double livenessZeroThreshold = 0.001;

      Builder() {}

      public Builder evalIntervalMs(long evalIntervalMs) {
        this.evalIntervalMs = evalIntervalMs;
        return this;
      }

      public Builder alarmSuppressWindowMs(long alarmSuppressWindowMs) {
        this.alarmSuppressWindowMs = alarmSuppressWindowMs;
        return this;
      }

      public Builder queueUsageThreshold(double queueUsageThreshold) {
        this.queueUsageThreshold = queueUsageThreshold;
        return this;
      }

      public Builder activeNearMaxThreshold(double activeNearMaxThreshold) {
        this.activeNearMaxThreshold = activeNearMaxThreshold;
        return this;
      }

      public Builder livenessZeroThreshold(double livenessZeroThreshold) {
        this.livenessZeroThreshold = livenessZeroThreshold;
        return this;
      }

      public AlarmEvalConfig build() {
        return new AlarmEvalConfig(this);
      }
    }
  }
}
