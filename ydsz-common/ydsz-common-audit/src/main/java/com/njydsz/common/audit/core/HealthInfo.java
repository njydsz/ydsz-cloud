package com.njydsz.common.audit.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康信息数据载体
 *
 * <p>封装审计记录器的运行时健康状态，由 {@link AuditRecorder#health()} 方法返回， 供 {@link
 * com.njydsz.common.audit.health.AuditHealthIndicator} 直接使用。
 *
 * <p>不同实现可扩展额外字段（如 {@code queueSize}、{@code usageRatio}）， 通过 {@link #getDetails()} 传递给健康检查端点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class HealthInfo {

  /** 健康状态 */
  private final Status status;

  /** 详细信息（key-value 对） */
  private final Map<String, Object> details;

  /**
   * 私有构造器，使用工厂方法创建实例
   *
   * @param status 健康状态
   * @param details 详细信息
   */
  private HealthInfo(Status status, Map<String, Object> details) {
    this.status = status;
    this.details = details;
  }

  /**
   * 创建 UP 状态的健康信息
   *
   * @param details 详细信息
   * @return HealthInfo 实例
   */
  public static HealthInfo up(Map<String, Object> details) {
    return new HealthInfo(
        Status.UP, details != null ? new LinkedHashMap<>(details) : new LinkedHashMap<>());
  }

  /**
   * 创建 UP 状态的健康信息（无额外详情）
   *
   * @return HealthInfo 实例
   */
  public static HealthInfo up() {
    return new HealthInfo(Status.UP, new LinkedHashMap<>());
  }

  /**
   * 创建 DOWN 状态的健康信息
   *
   * @param details 详细信息（应包含错误原因）
   * @return HealthInfo 实例
   */
  public static HealthInfo down(Map<String, Object> details) {
    return new HealthInfo(
        Status.DOWN, details != null ? new LinkedHashMap<>(details) : new LinkedHashMap<>());
  }

  /**
   * 创建 DOWN 状态的健康信息
   *
   * @param error 错误原因
   * @return HealthInfo 实例
   */
  public static HealthInfo down(String error) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("error", error);
    return down(details);
  }

  /**
   * 添加详情条目（链式调用）
   *
   * @param key 键
   * @param value 值
   * @return 当前实例（便于链式调用）
   */
  public HealthInfo withDetail(String key, Object value) {
    this.details.put(key, value);
    return this;
  }

  public Status getStatus() {
    return status;
  }

  public Map<String, Object> getDetails() {
    return Collections.unmodifiableMap(details);
  }

  /** 健康状态枚举 */
  public enum Status {
    /** 正常运行 */
    UP,
    /** 异常不可用 */
    DOWN
  }
}
