package com.njydsz.pmis.common.lock.exception;

import com.njydsz.pmis.common.exception.BizException;

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
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class DistributedLockException extends BizException {

    private static final long serialVersionUID = 1L;

    /** 默认错误码 */
    private static final int DEFAULT_CODE = 500;

    /**
     * 构造分布式锁异常
     *
     * @param message 异常消息
     */
    public DistributedLockException(String message) {
        super(DEFAULT_CODE, message);
    }

    /**
     * 构造分布式锁异常（带原因）
     *
     * @param message 异常消息
     * @param cause   原始异常
     */
    public DistributedLockException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造分布式锁异常（仅带原因）
     *
     * @param cause 原始异常
     */
    public DistributedLockException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
