package com.njydsz.common.lock.exception;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 分布式锁异常
 *
 * <p>在分布式锁操作失败时抛出的业务异常，包括：
 * <ul>
 *   <li>获取锁超时</li>
 *   <li>释放锁失败</li>
 *   <li>锁续期失败</li>
 * </ul>
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
     * 构造分布式锁异常
     *
     * @param message 异常消息
     */
    public DistributedLockException(String message) {
        super(UnifiedExceptionCode.FAIL);
        setMessage(message);
    }

    /**
     * 构造分布式锁异常（带原因）
     *
     * @param message 异常消息
     * @param cause   原始异常
     */
    public DistributedLockException(String message, Throwable cause) {
        super(UnifiedExceptionCode.FAIL, cause);
        setMessage(message);
    }

    /**
     * 构造分布式锁异常（仅带原因）
     *
     * @param cause 原始异常
     */
    public DistributedLockException(Throwable cause) {
        super(UnifiedExceptionCode.FAIL, cause);
        setMessage(cause != null ? cause.getMessage() : null);
    }
}
