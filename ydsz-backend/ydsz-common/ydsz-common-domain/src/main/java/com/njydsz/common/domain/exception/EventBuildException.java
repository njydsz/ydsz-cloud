package com.njydsz.common.domain.exception;

/**
 * 领域事件构建异常。
 *
 * <p>当 {@link com.njydsz.common.domain.event.DomainEvent} 构建失败时抛出，
 * 例如事件类型为空、事件字段非法等。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public class EventBuildException extends DomainException {

    private static final long serialVersionUID = 1L;

    public EventBuildException(String message) {
        super(message);
    }

    public EventBuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
