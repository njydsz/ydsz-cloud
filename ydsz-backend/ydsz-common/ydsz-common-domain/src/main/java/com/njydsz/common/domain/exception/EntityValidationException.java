package com.njydsz.common.domain.exception;

/**
 * 实体/领域对象校验异常。
 *
 * <p>当实体或领域对象违反业务不变量（invariant）时抛出，
 * 例如必填字段缺失、主键非法、聚合边界被破坏等。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public class EntityValidationException extends DomainException {

    private static final long serialVersionUID = 1L;

    public EntityValidationException(String message) {
        super(message);
    }

    public EntityValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
