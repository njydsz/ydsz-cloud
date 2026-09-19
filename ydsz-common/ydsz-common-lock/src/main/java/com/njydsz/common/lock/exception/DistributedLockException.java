package com.njydsz.common.lock.exception;

import java.time.LocalDateTime;

import com.njydsz.common.exception.custom.BusinessException;

/**
 * 分布式锁异常
 *
 * <p>在分布式锁操作失败时抛出的业务异常，包括：
 *
 * <ul>
 *   <li>获取锁超时
 *   <li>释放锁失败
 *   <li>锁续期失败
 *   <li>超过最大重入深度
 * </ul>
 *
 * <p>支持结构化上下文（{@link #errorCode}、{@link #lockKey}、{@link #lockType}）， 便于日志告警系统基于错误码进行自动分类与根因分析。 细分错误码使用 {@link LockExceptionCode} 枚举。
 *
 * <p><b>P2-E2 改进：</b>使用 {@link Builder} 构建异常，消除 5 个重载构造器的组合爆炸。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class DistributedLockException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /**
   * 细分错误码枚举，用于日志告警分类与根因分析
   *
   * <p>常见取值为：
   *
   * <ul>
   *   <li>{@link LockExceptionCode#ACQUIRE_TIMEOUT} - 获取锁超时
   *   <li>{@link LockExceptionCode#ACQUIRE_INTERRUPTED} - 获取锁被中断
   *   <li>{@link LockExceptionCode#RELEASE_FAILED} - 释放锁失败
   *   <li>{@link LockExceptionCode#RENEW_FAILED} - 锁续期失败
   *   <li>{@link LockExceptionCode#MAX_DEPTH_EXCEEDED} - 超过最大重入深度
   *   <li>{@link LockExceptionCode#REDIS_UNAVAILABLE} - Redis 不可用
   *   <li>{@link LockExceptionCode#UNKNOWN} - 未知错误
   * </ul>
   */
  private final LockExceptionCode errorCode;

  /** 锁键（可选） */
  private final String lockKey;

  /** 锁类型（可选，如 REENTRANT / FAIR 等） */
  private final String lockType;

  /**
   * 私有构造器，通过 {@link Builder} 构建。
   *
   * @param builder 构建器实例
   */
  private DistributedLockException(Builder builder) {
    super();
    LockExceptionCode resolvedCode =
        builder.errorCode != null ? builder.errorCode : LockExceptionCode.UNKNOWN;
    initFields(resolvedCode.getCode(), resolvedCode.getKey(), new Object[] {});
    setHttpStatus(resolvedCode.getHttpStatus());
    this.errorCode = resolvedCode;
    this.lockKey = builder.lockKey;
    this.lockType = builder.lockType;
    setTimestamp(LocalDateTime.now());
    setMessage(builder.message);
  }

  /**
   * 构造分布式锁异常（向后兼容，无结构化上下文）。
   *
   * @param message 异常消息
   */
  public DistributedLockException(String message) {
    this(builder().message(message));
  }

  /**
   * 构造分布式锁异常（带原因，向后兼容）。
   *
   * @param message 异常消息
   * @param cause 原始异常
   */
  public DistributedLockException(String message, Throwable cause) {
    this(builder().message(message));
    if (cause != null) {
      initCause(cause);
    }
  }

  /**
   * 获取构建器（P2-E2 新增）。
   *
   * @return 新的构建器实例
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * 获取细分错误码。
   *
   * @return 错误码枚举
   */
  public LockExceptionCode getErrorCode() {
    return errorCode;
  }

  /**
   * 获取锁键。
   *
   * @return 锁键，可能为 null
   */
  public String getLockKey() {
    return lockKey;
  }

  /**
   * 获取锁类型。
   *
   * @return 锁类型名称，可能为 null
   */
  public String getLockType() {
    return lockType;
  }

  /**
   * 判断是否为超时类错误。
   *
   * @return true-超时相关错误
   */
  public boolean isTimeout() {
    return LockExceptionCode.ACQUIRE_TIMEOUT.equals(errorCode);
  }

  /**
   * 判断是否为资源不可用类错误。
   *
   * @return true-Redis 不可用或连接异常
   */
  public boolean isResourceUnavailable() {
    return LockExceptionCode.REDIS_UNAVAILABLE.equals(errorCode);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(super.toString());
    sb.append("{errorCode='").append(errorCode).append('\'');
    if (lockKey != null) {
      sb.append(", lockKey='").append(lockKey).append('\'');
    }
    if (lockType != null) {
      sb.append(", lockType='").append(lockType).append('\'');
    }
    sb.append(", timestamp=").append(getTimestamp());
    sb.append('}');
    return sb.toString();
  }

  /**
   * 分布式锁异常构建器（P2-E2 新增）。
   *
   * <p>消除构造器重载组合爆炸，以流式 API 构造带完整上下文的异常。
   *
   * <p><b>使用示例：</b>
   *
   * <pre>{@code
   * throw DistributedLockException.builder()
   *     .message("获取分布式锁失败")
   *     .errorCode(LockExceptionCode.ACQUIRE_TIMEOUT)
   *     .lockKey("order:lock:123")
   *     .lockType("REENTRANT")
   *     .build();
   * }</pre>
   */
  public static final class Builder {
    private String message;
    private LockExceptionCode errorCode;
    private String lockKey;
    private String lockType;

    private Builder() {}

    /** 异常消息 */
    public Builder message(String message) {
      this.message = message;
      return this;
    }

    /** 细分错误码 */
    public Builder errorCode(LockExceptionCode errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    /** 锁键 */
    public Builder lockKey(String lockKey) {
      this.lockKey = lockKey;
      return this;
    }

    /** 锁类型 */
    public Builder lockType(String lockType) {
      this.lockType = lockType;
      return this;
    }

    /**
     * 构建异常实例。
     *
     * @return DistributedLockException 实例
     */
    public DistributedLockException build() {
      return new DistributedLockException(this);
    }
  }
}
