package com.njydsz.agent.domain.asynctask;

import java.util.EnumSet;
import java.util.Set;

/**
 * 异步任务状态枚举
 *
 * <p>状态流转：
 *
 * <pre>
 * PENDING ──▶ RUNNING ──▶ SUCCEEDED
 *    │            │
 *    │            ├──▶ FAILED ──▶ PENDING（重试）
 *    │            │
 *    │            └──▶ EXPIRED
 *    │
 *    ├──▶ CANCELED
 *    └──▶ EXPIRED
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public enum AsyncTaskStatus {

  /** 待执行 */
  PENDING("PENDING", "待执行"),

  /** 执行中 */
  RUNNING("RUNNING", "执行中"),

  /** 已完成 */
  SUCCEEDED("SUCCEEDED", "已完成"),

  /** 失败（可重试） */
  FAILED("FAILED", "失败"),

  /** 已取消 */
  CANCELED("CANCELED", "已取消"),

  /** 已过期 */
  EXPIRED("EXPIRED", "已过期");

  private final String code;

  private final String description;

  /** 终态状态集合 */
  private static final Set<AsyncTaskStatus> TERMINAL_STATES =
      EnumSet.of(SUCCEEDED, FAILED, CANCELED, EXPIRED);

  /** 可重试状态集合 */
  private static final Set<AsyncTaskStatus> RETRYABLE_STATES = EnumSet.of(FAILED);

  AsyncTaskStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  /**
   * 判断状态是否为终态。
   *
   * @param statusCode 状态编码
   * @return true=终态
   */
  public static boolean isTerminal(String statusCode) {
    if (statusCode == null) {
      return false;
    }
    AsyncTaskStatus status = fromCode(statusCode);
    return status != null && TERMINAL_STATES.contains(status);
  }

  /**
   * 判断状态是否可重试。
   *
   * @param statusCode 状态编码
   * @return true=可重试
   */
  public static boolean isRetryable(String statusCode) {
    if (statusCode == null) {
      return false;
    }
    AsyncTaskStatus status = fromCode(statusCode);
    return status != null && RETRYABLE_STATES.contains(status);
  }

  /**
   * 根据编码解析枚举。
   *
   * @param code 状态编码
   * @return 匹配枚举，未找到返回 null
   */
  public static AsyncTaskStatus fromCode(String code) {
    if (code == null) {
      return null;
    }
    for (AsyncTaskStatus status : values()) {
      if (status.code.equals(code)) {
        return status;
      }
    }
    return null;
  }
}
