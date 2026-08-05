package com.remisoft.common.event.api;

import com.remisoft.common.exception.custom.AbstractRemiException;

/**
 * 领域事件构建异常。
 *
 * <p>当 {@link DomainEvent} 构建失败时抛出，例如事件类型为空、事件字段非法等。
 *
 * <p><b>继承说明：</b>统一异常体系已收敛至 {@code remi-common-exception}，
 * 原 {@code com.remisoft.common.domain.exception.DomainException} 已废弃移除，
 * 本类改继承 {@link AbstractRemiException}。
 *
 * @author remi-team
 * @since 1.4.0
 * @since 1.5.0 由 common-domain 迁入 common-event，与 DomainEvent 同包
 */
public class EventBuildException extends AbstractRemiException {

    private static final long serialVersionUID = 1L;

    public EventBuildException(String message) {
        super(message);
    }

    public EventBuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
