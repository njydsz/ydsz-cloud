package com.njydsz.common.lock.exception;

import java.time.LocalDateTime;
import com.njydsz.common.exception.custom.BusinessException;


/**
 * 分布式锁异常
 *
 * <p>在分布式锁操作失败时抛出的业务异常，包括：
 * <ul>
 *   <li>获取锁超时</li>
 *   <li>释放锁失败</li>
 *   <li>锁续期失败</li>
 *   <li>超过最大重入深度</li>
 * </ul>
 *
 * <p>支持结构化上下文（{@link #errorCode}、{@link #lockKey}、{@link #lockType}），
 * 便于日志告警系统基于错误码进行自动分类与根因分析。
 * 细分错误码使用 {@link LockExceptionCode} 枚举。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DistributedLockException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * 细分错误码枚举，用于日志告警分类与根因分析
     *
     * <p>常见取值为：
     * <ul>
     *   <li>{@link LockExceptionCode#ACQUIRE_TIMEOUT} - 获取锁超时</li>
     *   <li>{@link LockExceptionCode#ACQUIRE_INTERRUPTED} - 获取锁被中断</li>
     *   <li>{@link LockExceptionCode#RELEASE_FAILED} - 释放锁失败</li>
     *   <li>{@link LockExceptionCode#RENEW_FAILED} - 锁续期失败</li>
     *   <li>{@link LockExceptionCode#MAX_DEPTH_EXCEEDED} - 超过最大重入深度</li>
     *   <li>{@link LockExceptionCode#REDIS_UNAVAILABLE} - Redis 不可用</li>
     *   <li>{@link LockExceptionCode#UNKNOWN} - 未知错误</li>
     * </ul>
     */
    private final LockExceptionCode errorCode;

    /**
     * 锁键（可选）
     */
    private final String lockKey;

    /**
     * 锁类型（可选，如 REENTRANT / MULTI_LOCK 等）
     */
    private final String lockType;

    /**
     * 构造分布式锁异常（向后兼容，无结构化上下文）
     *
     * @param message 异常消息
     */
    public DistributedLockException(String message) {
        this(message, LockExceptionCode.LOCK_ERROR, null, null);
    }

    /**
     * 构造分布式锁异常（带原因，向后兼容）
     *
     * @param message 异常消息
     * @param cause   原始异常
     */
    public DistributedLockException(String message, Throwable cause) {
        this(message, LockExceptionCode.LOCK_ERROR, null, cause);
    }

    /**
     * 构造带完整性上下文的分布式锁异常
     *
     * @param message    异常消息
     * @param errorCode  细分错误码（参考 {@link LockExceptionCode}）
     * @param lockKey    锁键
     * @param cause      原始异常（可为 null）
     */
    public DistributedLockException(String message, LockExceptionCode errorCode,
                                    String lockKey, Throwable cause) {
        super();
        LockExceptionCode resolvedCode = errorCode != null ? errorCode : LockExceptionCode.UNKNOWN;
        initFields(resolvedCode.getCode(), resolvedCode.getKey(), new Object[]{});
        setHttpStatus(resolvedCode.getHttpStatus());
        this.errorCode = resolvedCode;
        this.lockKey = lockKey;
        this.lockType = null;
        setTimestamp(LocalDateTime.now());
        setMessage(message);
    }

    /**
     * 构造带完整结构化上下文的分布式锁异常
     *
     * @param message    异常消息
     * @param errorCode  细分错误码（参考 {@link LockExceptionCode}）
     * @param lockKey    锁键
     * @param lockType   锁类型名称
     * @param cause      原始异常（可为 null）
     */
    public DistributedLockException(String message, LockExceptionCode errorCode,
                                    String lockKey, String lockType, Throwable cause) {
        super();
        LockExceptionCode resolvedCode = errorCode != null ? errorCode : LockExceptionCode.UNKNOWN;
        initFields(resolvedCode.getCode(), resolvedCode.getKey(), new Object[]{});
        setHttpStatus(resolvedCode.getHttpStatus());
        this.errorCode = resolvedCode;
        this.lockKey = lockKey;
        this.lockType = lockType;
        setTimestamp(LocalDateTime.now());
        setMessage(message);
    }

    /**
     * 获取细分错误码
     *
     * @return 错误码枚举
     */
    public LockExceptionCode getErrorCode() {
        return errorCode;
    }

    /**
     * 获取锁键
     *
     * @return 锁键，可能为 null
     */
    public String getLockKey() {
        return lockKey;
    }

    /**
     * 获取锁类型
     *
     * @return 锁类型名称，可能为 null
     */
    public String getLockType() {
        return lockType;
    }

    /**
     * 判断是否为超时类错误
     *
     * @return true-超时相关错误
     */
    public boolean isTimeout() {
        return LockExceptionCode.ACQUIRE_TIMEOUT.equals(errorCode);
    }

    /**
     * 判断是否为资源不可用类错误
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
}
