package com.njydsz.common.thread.metrics;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * 任务执行耗时追踪装饰器。
 *
 * <p>包装原始 {@link Runnable}，在任务执行前后记录时间戳，自动计算执行耗时与队列等待时长， 并回调关联的 {@link ThreadPoolTimerMetrics}。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * TimedTaskDecorator decorator = new TimedTaskDecorator(poolName, slowTaskThresholdMs, timerMetrics);
 * executor.setTaskDecorator(decorator);
 * }</pre>
 *
 * <p>线程安全：使用不可变包装对象传递时间戳，无跨任务串扰风险， 避免全局 {@code ConcurrentMap} 在高并发场景下因 threadId 复用导致的数据污染。
 *
 * <p>26.09.19 变更（P2-8）：新增 {@link SlowTaskRecord} 采样记录，超出阈值的慢任务信息保存在有限队列内， 供
 * {@link ThreadPoolMetricsEndpoint} 端点或告警系统消费。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ThreadPoolTimerMetrics
 */
@Slf4j
public class TimedTaskDecorator implements TaskDecorator {

  /** 默认慢任务记录队列容量。 */
  private static final int DEFAULT_SLOW_TASK_CAPACITY = 64;

  private final String poolName;
  private final long slowTaskThresholdMs;
  private final ThreadPoolTimerMetrics timerMetrics;
  private final int slowTaskCapacity;

  /**
   * 慢任务记录环形队列（FIFO，超出容量时丢弃最旧记录）。
   *
   * <p>使用 {@link ConcurrentLinkedQueue} 保证写入无锁、读取时复制快照避免迭代器异常。
   */
  private final Queue<SlowTaskRecord> slowTaskRecords = new ConcurrentLinkedQueue<>();

  /**
   * 构造耗时追踪装饰器（使用默认队列容量）。
   *
   * @param poolName 线程池名称（用于日志追溯与慢任务标记）
   * @param slowTaskThresholdMs 慢任务阈值（毫秒），≥ 100
   * @param timerMetrics 关联的耗时指标绑定器
   */
  public TimedTaskDecorator(
      String poolName, long slowTaskThresholdMs, ThreadPoolTimerMetrics timerMetrics) {
    this(poolName, slowTaskThresholdMs, timerMetrics, DEFAULT_SLOW_TASK_CAPACITY);
  }

  /**
   * 构造耗时追踪装饰器（指定慢任务队列容量）。
   *
   * @param poolName 线程池名称（用于日志追溯与慢任务标记）
   * @param slowTaskThresholdMs 慢任务阈值（毫秒），≥ 100
   * @param timerMetrics 关联的耗时指标绑定器
   * @param slowTaskCapacity 慢任务记录队列容量
   */
  public TimedTaskDecorator(
      String poolName,
      long slowTaskThresholdMs,
      ThreadPoolTimerMetrics timerMetrics,
      int slowTaskCapacity) {
    this.poolName = poolName;
    this.slowTaskThresholdMs = slowTaskThresholdMs;
    this.timerMetrics = timerMetrics;
    this.slowTaskCapacity = slowTaskCapacity > 0 ? slowTaskCapacity : DEFAULT_SLOW_TASK_CAPACITY;
  }

  @Override
  public Runnable decorate(@NonNull Runnable runnable) {
    long submittedAt = System.nanoTime();
    // 捕获任务类名（P2-8：负载类名采样，辅助定位慢任务来源）
    String taskClassName = extractTaskClassName(runnable);
    return new TimedRunnable(submittedAt, runnable, taskClassName);
  }

  /**
   * 提取 Runnable 委托对象的类名。
   *
   * <p>如果 runnable 本身是包装器（如 TtlRunnable、CompositeTaskDecorator），取其 toString 中的关键信息；
   * 默认取 delegate 的类名。
   *
   * @param runnable 被包装的 Runnable
   * @return 类名；无法提取时返回 "unknown"
   */
  private String extractTaskClassName(Runnable runnable) {
    if (runnable == null) {
      return "unknown";
    }
    try {
      // 尝试获取被包装的原始任务类名
      // 通过反射查找常见包装器模式的 "delegate"/"runnable" 字段
      java.lang.reflect.Field delegateField = findDelegateField(runnable.getClass());
      if (delegateField != null) {
        delegateField.setAccessible(true);
        Object delegate = delegateField.get(runnable);
        if (delegate != null) {
          return delegate.getClass().getName();
        }
      }
      return runnable.getClass().getName();
    } catch (Exception e) {
      return runnable.getClass().getName();
    }
  }

  /**
   * 在类层次结构中查找委托字段。
   *
   * @param clazz 要检查的类
   * @return 找到的委托字段；未找到返回 null
   */
  private java.lang.reflect.Field findDelegateField(Class<?> clazz) {
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      for (java.lang.reflect.Field field : current.getDeclaredFields()) {
        if ("delegate".equals(field.getName()) || "runnable".equals(field.getName())
            || "task".equals(field.getName())) {
          return field;
        }
      }
      current = current.getSuperclass();
    }
    return null;
  }

  /**
   * 获取指定线程池名称。
   *
   * @return 线程池名称
   */
  public String getPoolName() {
    return poolName;
  }

  /**
   * 获取慢任务阈值。
   *
   * @return 慢任务阈值（毫秒）
   */
  public long getSlowTaskThresholdMs() {
    return slowTaskThresholdMs;
  }

  /**
   * 获取慢任务记录的快照（不可变）。
   *
   * @return 慢任务记录列表（最多 {@code slowTaskCapacity} 条）
   */
  public List<SlowTaskRecord> getSlowTaskRecords() {
    return Collections.unmodifiableList(new ArrayList<>(slowTaskRecords));
  }

  /**
   * 可执行包装对象，携带任务提交时间戳和任务类名。
   *
   * <p>不可变设计确保装饰后的任务可跨线程安全传递，无竞态条件。
   */
  private final class TimedRunnable implements Runnable {

    private final long submittedAt;
    private final Runnable delegate;
    private final String taskClassName;

    TimedRunnable(long submittedAt, Runnable delegate, String taskClassName) {
      this.submittedAt = submittedAt;
      this.delegate = delegate;
      this.taskClassName = taskClassName;
    }

    @Override
    public void run() {
      long startedAt = System.nanoTime();
      long queueWaitMs = Math.max(0L, (startedAt - submittedAt) / 1_000_000L);

      try {
        delegate.run();
      } finally {
        long finishedAt = System.nanoTime();
        long executionMs = (finishedAt - startedAt) / 1_000_000L;
        recordMetric(executionMs, queueWaitMs, taskClassName);
      }
    }
  }

  /**
   * 记录耗时指标和慢任务采样。
   *
   * <p>捕获所有异常，确保指标上报不影响业务任务。
   *
   * @param executionMs 执行耗时（毫秒）
   * @param queueWaitMs 队列等待（毫秒）
   * @param taskClassName 任务类名
   */
  private void recordMetric(long executionMs, long queueWaitMs, String taskClassName) {
    try {
      if (timerMetrics != null) {
        timerMetrics.record(executionMs, queueWaitMs, slowTaskThresholdMs, poolName);
      }
    } catch (Exception e) {
      log.debug("[TimedTaskDecorator] 指标记录失败: pool={}, err={}", poolName, e.getMessage());
    }

    // P2-8：超出阈值的任务记录到 slowTaskRecords 队列
    if (executionMs > slowTaskThresholdMs) {
      SlowTaskRecord record =
          new SlowTaskRecord(poolName, executionMs, queueWaitMs, taskClassName, LocalDateTime.now());
      slowTaskRecords.offer(record);
      // FIFO 容量控制（超出容量时移除最旧记录）
      while (slowTaskRecords.size() > slowTaskCapacity) {
        slowTaskRecords.poll();
      }

      // P1-8 warn 级别日志：仅有任务类名，辅助定位（仅在慢任务超 3 倍阈值时减少噪音）
      if (executionMs > slowTaskThresholdMs * 3) {
        log.warn(
            "[TimedTaskDecorator] 慢任务告警: pool={}, executionMs={}, threshold={}, taskClass={}",
            poolName,
            executionMs,
            slowTaskThresholdMs,
            taskClassName);
      }
    }
  }
}
