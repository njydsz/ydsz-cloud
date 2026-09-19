package com.njydsz.common.thread.alarm;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 线程池告警事件（不可变）。
 *
 * <p>由 {@link ThreadPoolAlarmEvaluator} 生成，传递给 {@link AlarmNotifier} 通知。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public final class AlarmEvent {

  /**
   * 告警维度枚举。
   *
   * <p>对标 Dynamic TP 告警体系，覆盖最常见的线程池异常场景。
   */
  public enum AlarmDimension {
    /** 队列使用率超阈值 */
    QUEUE_USAGE,
    /** 拒绝次数增长速率异常 */
    REJECT_RATE,
    /** 慢任务频次异常 */
    SLOW_TASK_RATE,
    /** 活跃线程数接近最大值 */
    ACTIVE_NEAR_MAX,
    /** 线程池活跃度为 0（可能已满载或无任务提交） */
    LIVENESS_ZERO
  }

  /** 告警级别。 */
  public enum Severity {
    /** 警告，需关注 */
    WARN,
    /** 严重，需立即介入 */
    CRITICAL
  }

  private final String poolName;
  private final AlarmDimension dimension;
  private final Severity severity;
  private final double currentValue;
  private final double threshold;
  private final String message;
  private final LocalDateTime triggeredAt;
  private final Map<String, String> tags;

  private AlarmEvent(Builder builder) {
    this.poolName = builder.poolName;
    this.dimension = builder.dimension;
    this.severity = builder.severity;
    this.currentValue = builder.currentValue;
    this.threshold = builder.threshold;
    this.message = builder.message;
    this.triggeredAt = builder.triggeredAt != null ? builder.triggeredAt : LocalDateTime.now();
    this.tags = builder.tags != null ? Collections.unmodifiableMap(new HashMap<>(builder.tags)) : Collections.emptyMap();
  }

  public String getPoolName() {
    return poolName;
  }

  public AlarmDimension getDimension() {
    return dimension;
  }

  public Severity getSeverity() {
    return severity;
  }

  public double getCurrentValue() {
    return currentValue;
  }

  public double getThreshold() {
    return threshold;
  }

  public String getMessage() {
    return message;
  }

  public LocalDateTime getTriggeredAt() {
    return triggeredAt;
  }

  public Map<String, String> getTags() {
    return tags;
  }

  @Override
  public String toString() {
    return "AlarmEvent{pool=" + poolName
        + ", dimension=" + dimension
        + ", severity=" + severity
        + ", current=" + currentValue
        + ", threshold=" + threshold
        + ", message='" + message + "'"
        + ", triggeredAt=" + triggeredAt + "}";
  }

  /**
   * 创建告警事件构造器。
   *
   * @param poolName 线程池名称
   * @param dimension 告警维度
   * @return 构造器实例
   */
  public static Builder builder(String poolName, AlarmDimension dimension) {
    return new Builder(poolName, dimension);
  }

  /** {@link AlarmEvent} 构造器。 */
  public static final class Builder {
    private final String poolName;
    private final AlarmDimension dimension;
    private AlarmEvent.Severity severity = AlarmEvent.Severity.WARN;
    private double currentValue;
    private double threshold;
    private String message;
    private LocalDateTime triggeredAt;
    private Map<String, String> tags;

    private Builder(String poolName, AlarmDimension dimension) {
      this.poolName = poolName;
      this.dimension = dimension;
    }

    public Builder severity(AlarmEvent.Severity severity) {
      this.severity = severity;
      return this;
    }

    public Builder currentValue(double currentValue) {
      this.currentValue = currentValue;
      return this;
    }

    public Builder threshold(double threshold) {
      this.threshold = threshold;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder triggeredAt(LocalDateTime triggeredAt) {
      this.triggeredAt = triggeredAt;
      return this;
    }

    public Builder tags(Map<String, String> tags) {
      this.tags = tags;
      return this;
    }

    public AlarmEvent build() {
      return new AlarmEvent(this);
    }
  }
}
