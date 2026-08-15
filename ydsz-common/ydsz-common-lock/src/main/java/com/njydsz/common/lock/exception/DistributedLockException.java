package com.njydsz.common.lock.exception;

import com.njydsz.common.exception.code.CoreExceptionCode;
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
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DistributedLockException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * 默认错误码
     */
    private static final String DEFAULT_CODE = "LOCK_ERROR";

    /**
     * 细分错误码，用于日志告警分类与根因分析
     *
     * <p>常见取值为：
     * <ul>
     *   <li>{@link LockErrorCode#ACQUIRE_TIMEOUT} - 获取锁超时</li>
     *   <li>{@link LockErrorCode#ACQUIRE_INTERRUPTED} - 获取锁被中断</li>
     *   <li>{@link LockErrorCode#RELEASE_FAILED} - 释放锁失败</li>
     *   <li>{@link LockErrorCode#RENEW_FAILED} - 锁续期失败</li>
     *   <li>{@link LockErrorCode#MAX_DEPTH_EXCEEDED} - 超过最大重入深度</li>
     *   <li>{@link LockErrorCode#REDIS_UNAVAILABLE} - Redis 不可用</li>
     *   <li>{@link LockErrorCode#UNKNOWN} - 未知错误</li>
     * </ul>
     */
    private final String errorCode;

    /**
     * 锁键（可选）
     */
    private final String lockKey;

    /**
     * 锁类型（可选，如 REENTRANT / MULTI_LOCK 等）
     */
    private final String lockType;

    /**
     * 异常发生时间戳（毫秒，自 Unix 纪元）
     */
    private final long timestamp;

    /**
     * 构造分布式锁异常（向后兼容，无结构化上下文）
     *
     * @param message 异常消息
     */
    public DistributedLockException(String message) {
        this(message, DEFAULT_CODE, null, null);
    }

    /**
     * 构造分布式锁异常（带原因，向后兼容）
     *
     * @param message 异常消息
     * @param cause   原始异常
     */
    public DistributedLockException(String message, Throwable cause) {
        this(message, DEFAULT_CODE, null, cause);
    }

    /**
     * 构造分布式锁异常（仅带原因，向后兼容）
     *
     * @param cause 原始异常
     */
    public DistributedLockException(Throwable cause) {
        this(cause != null ? cause.getMessage() : null, DEFAULT_CODE, null, cause);
    }

    /**
     * 构造带完整性上下文的分布式锁异常
     *
     * @param message   异常消息
     * @param errorCode 细分错误码（参考 {@link LockErrorCode}）
     * @param lockKey   锁键
     * @param cause     原始异常（可为 null）
     */
    public DistributedLockException(String message, String errorCode, String lockKey, Throwable cause) {
        super(CoreExceptionCode.FAIL, cause);
        setMessage(message);
        this.errorCode = errorCode != null ? errorCode : LockErrorCode.UNKNOWN;
        this.lockKey = lockKey;
        this.lockType = null;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 构造带完整结构化上下文的分布式锁异常
     *
     * @param message   异常消息
     * @param errorCode 细分错误码（参考 {@link LockErrorCode}）
     * @param lockKey   锁键
     * @param lockType  锁类型名称
     * @param cause     原始异常（可为 null）
     */
    public DistributedLockException(String message, String errorCode, String lockKey, String lockType, Throwable cause) {
        super(CoreExceptionCode.FAIL, cause);
        setMessage(message);
        this.errorCode = errorCode != null ? errorCode : LockErrorCode.UNKNOWN;
        this.lockKey = lockKey;
        this.lockType = lockType;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 获取细分错误码
     *
     * @return 错误码字符串
     */
    public String getErrorCode() {
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
     * 获取异常发生时间戳
     *
     * @return 毫秒级时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 判断是否为超时类错误
     *
     * @return true-超时相关错误
     */
    public boolean isTimeout() {
        return LockErrorCode.ACQUIRE_TIMEOUT.equals(errorCode);
    }

    /**
     * 判断是否为资源不可用类错误
     *
     * @return true-Redis 不可用或连接异常
     */
    public boolean isResourceUnavailable() {
        return LockErrorCode.REDIS_UNAVAILABLE.equals(errorCode);
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
        sb.append(", timestamp=").append(timestamp);
        sb.append('}');
        return sb.toString();
    }

    /**
     * 锁细分错误码常量
     *
     * <p>用于 {@link DistributedLockException#errorCode} 字段，支持错误分类与告警路由
     */
    public static final class LockErrorCode {

        /** 获取锁超时 */
        public static final String ACQUIRE_TIMEOUT = "LOCK_ACQUIRE_TIMEOUT";

        /** 获取锁被中断 */
        public static final String ACQUIRE_INTERRUPTED = "LOCK_ACQUIRE_INTERRUPTED";

        /** 释放锁失败 */
        public static final String RELEASE_FAILED = "LOCK_RELEASE_FAILED";

        /** 锁续期失败 */
        public static final String RENEW_FAILED = "LOCK_RENEW_FAILED";

        /** 超过最大重入深度 */
        public static final String MAX_DEPTH_EXCEEDED = "LOCK_MAX_DEPTH_EXCEEDED";

        /** Redis 不可用 */
        public static final String REDIS_UNAVAILABLE = "LOCK_REDIS_UNAVAILABLE";

        /** 未知错误 */
        public static final String UNKNOWN = "LOCK_UNKNOWN";

        private LockErrorCode() {
            throw new AssertionError("Cannot instantiate LockErrorCode");
        }
    }
}
