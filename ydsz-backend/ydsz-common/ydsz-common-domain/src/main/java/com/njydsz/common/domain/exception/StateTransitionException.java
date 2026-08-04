package com.njydsz.common.domain.exception;

/**
 * 非法状态流转异常。
 *
 * <p>当状态机校验发现不允许的状态迁移时抛出（如
 * {@code DELIVERED -> PAID}），携带当前状态与目标状态信息。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public class StateTransitionException extends DomainException {

    private static final long serialVersionUID = 1L;

    public StateTransitionException(String message) {
        super(message);
    }

    public StateTransitionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 便捷构造：携带源状态与目标状态。
     *
     * @param from 当前状态
     * @param to   非法目标状态
     */
    public StateTransitionException(Object from, Object to) {
        super("非法状态流转: " + from + " -> " + to);
    }
}
