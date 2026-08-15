package com.njydsz.common.util.id;

/**
 * 策略不适用异常——当前 WorkerIdAllocator 无法处理当前环境时抛出。
 *
 * <p>用于 {@link WorkerIdAllocatorChain} 策略链中，通知链尝试下个策略。
 * 与 {@link WorkerIdExhaustedException} 不同，此异常不表示最终失败，
 * 仅表示当前策略无法适用。
 *
 * @author ydsz-team
 * @since 3.0.0
 */
public class NotApplicableException extends RuntimeException {

    public NotApplicableException(String message) {
        super(message);
    }

    public NotApplicableException(String message, Throwable cause) {
        super(message, cause);
    }
}










