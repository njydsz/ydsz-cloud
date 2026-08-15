package com.njydsz.common.util.concurrent;

/**
 * 重试耗尽异常（unchecked）。
 *
 * <p>当 {@link RetryUtils} 的所有重试尝试均失败（或执行过程中线程被中断）时抛出，
 * 包装最后一次失败的异常作为 {@link #getCause()}。
 *
 * @author ydsz-team
 * @since 4.1.0
 */
public class RetryException extends RuntimeException {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 构造重试异常。
     *
     * @param message 异常描述信息
     * @param cause   最后一次失败的异常（可为 null）
     * @return 处理后的结果
     */
    public RetryException(String message, Throwable cause) {
        super(message, cause);
    }
}
















