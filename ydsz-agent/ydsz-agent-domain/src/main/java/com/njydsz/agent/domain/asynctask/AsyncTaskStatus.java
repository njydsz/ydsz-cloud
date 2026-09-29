package com.njydsz.agent.domain.asynctask;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.njydsz.common.domain.enums.BaseStatusEnum;

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
public enum AsyncTaskStatus implements BaseStatusEnum<AsyncTaskStatus> {

  /** 待执行 */
  PENDING("PENDING", "agent.status.pending"),

  /** 执行中 */
  RUNNING("RUNNING", "agent.status.running"),

  /** 已完成 */
  SUCCEEDED("SUCCEEDED", "agent.status.succeeded"),

  /** 失败（可重试） */
  FAILED("FAILED", "agent.status.failed"),

  /** 已取消 */
  CANCELED("CANCELED", "agent.status.canceled"),

  /** 已过期 */
  EXPIRED("EXPIRED", "agent.status.expired");

  private final String code;

  private final String description;

  /** 终态状态集合（静态 isTerminal 语义，包含 FAILED 作为"业务终态"） */
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

  // ---- BaseStatusEnum 实现 ----

  /**
   * 判断当前状态是否为终态（实例方法）。
   *
   * <p>FAILED 虽在 {@link #isTerminal(String)} 静态方法中被归为"业务终态"，
   * 但其可重试回到 PENDING，不符合状态机"不可再迁移"定义，故此处返回 false。
   *
   * @return true 表示不可再向其他状态迁移
   */
  @Override
  public boolean isTerminal() {
    // 可重试状态（FAILED）不是终态
    return TERMINAL_STATES.contains(this) && !RETRYABLE_STATES.contains(this);
  }

  /**
   * 校验状态流转合法性。
   *
   * <pre>
   * PENDING  → RUNNING | CANCELED | EXPIRED
   * RUNNING  → SUCCEEDED | FAILED | EXPIRED
   * FAILED   → PENDING（重试）
   * 终态（SUCCEEDED/CANCELED/EXPIRED）→ 仅自身
   * </pre>
   *
   * @param target 目标状态
   * @return true 表示允许从当前状态迁移到目标状态
   */
  @Override
  public boolean canTransitTo(AsyncTaskStatus target) {
    if (target == null) {
      return false;
    }
    if (this == target) {
      return true;
    }
    if (this.isTerminal()) {
      return false;
    }
    return switch (this) {
      case PENDING -> target == RUNNING || target == CANCELED || target == EXPIRED;
      case RUNNING -> target == SUCCEEDED || target == FAILED || target == EXPIRED;
      case FAILED -> target == PENDING;
      default -> false;
    };
  }

  /**
   * 返回所有枚举值。
   *
   * @return 全量状态列表
   */
  @Override
  public List<AsyncTaskStatus> allStates() {
    return List.of(values());
  }

  // ---- 静态工具方法 ----

  /**
   * 判断状态是否为终态（静态方法，向后兼容）。
   *
   * <p>注意：FAILED 在此方法中被视为终态（同原语义），但其可通过 {@link #canTransitTo} 重试回 PENDING。
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
