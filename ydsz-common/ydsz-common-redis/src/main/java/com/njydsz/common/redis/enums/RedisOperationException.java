package com.njydsz.common.redis.enums;

import com.njydsz.common.exception.custom.InfraException;

/**
 * Redis 操作异常（26.09.30 迁移至 InfraException 统一体系）。
 *
 * <p>当 {@link FailOpenPolicy#FAIL_THROW} 策略生效时，Redis 操作失败会抛出此异常。 封装了操作的 key、操作名称、原始异常以及恢复建议，便于上层统一处理和运维排查。
 *
 * <p>继承 {@link InfraException} 后具备：统一错误码（HTTP 500 / SYSTEM 分类）、异常指标统计、异常事件发布等能力。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public class RedisOperationException extends InfraException {

  private static final long serialVersionUID = 1L;

  /** 操作的 Redis key（可为 null） */
  private final String key;

  /** 操作名称（如 "GET", "SET", "HGET"） */
  private final String operation;

  /** 恢复建议（面向运维和开发者，提供故障排查方向） */
  private final String suggestion;

  /**
   * 构造 Redis 操作异常
   *
   * @param key 操作的 key
   * @param operation 操作名称
   * @param cause 原始异常
   */
  public RedisOperationException(String key, String operation, Throwable cause) {
    this(key, operation, cause, null);
  }

  /**
   * 构造 Redis 操作异常（含恢复建议）
   *
   * @param key 操作的 key
   * @param operation 操作名称
   * @param cause 原始异常
   * @param suggestion 恢复建议（如检查方向、修复步骤）
   */
  public RedisOperationException(
      String key, String operation, Throwable cause, String suggestion) {
    super(buildMessage(key, operation, cause, suggestion), cause);
    this.key = key;
    this.operation = operation;
    this.suggestion = suggestion;
  }

  /**
   * 构建异常描述消息
   *
   * @param key 操作的 key
   * @param operation 操作名称
   * @param cause 原始异常
   * @param suggestion 恢复建议
   * @return 格式化的异常描述
   */
  private static String buildMessage(
      String key, String operation, Throwable cause, String suggestion) {
    return String.format(
        "Redis operation failed | operation=%s | key=%s | cause=%s%s",
        operation,
        key,
        cause.getMessage(),
        suggestion != null ? " | suggestion=" + suggestion : "");
  }

  /**
   * 获取操作的 key
   *
   * @return key（可为 null）
   */
  public String getKey() {
    return key;
  }

  /**
   * 获取操作名称
   *
   * @return 操作名称
   */
  public String getOperation() {
    return operation;
  }

  /**
   * 获取恢复建议
   *
   * @return 恢复建议字符串，可能为 null
   */
  public String getSuggestion() {
    return suggestion;
  }
}
