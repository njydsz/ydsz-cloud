package com.njydsz.common.domain.exception;

/**
 * 领域层基础异常。
 *
 * <p>所有领域模型层（实体、状态机、事件、查询模型）抛出的业务异常的统一父类，
 * 便于上层 {@code @ControllerAdvice} / 全局异常处理器按类型捕获：
 * <ul>
 *   <li>{@link StateTransitionException} — 非法状态流转</li>
 *   <li>{@link EntityValidationException} — 实体/领域对象校验失败</li>
 *   <li>{@link EventBuildException} — 领域事件构建失败</li>
 * </ul>
 *
 * <p>相较于直接抛出 {@link IllegalStateException} / {@link IllegalArgumentException}，
 * 使用本体系可让调用方精准 catch 具体领域语义，避免泛化处理。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
