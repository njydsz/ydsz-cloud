package com.njydsz.common.domain.enums;

/**
 * 状态枚举统一抽象。
 *
 * <p>所有业务状态枚举应实现此接口，复用 {@link #canTransitTo(Enum)} 状态流转校验，
 * 避免各模块重复定义状态机逻辑。配合 {@code StatusTransitionAspect} 或业务层
 * 显式调用 {@code canTransitTo} 实现状态变迁前置校验。
 *
 * <p>实现约定：
 * <ul>
 *   <li>自身到自身（{@code this == target}）返回 {@code true}</li>
 *   <li>{@code target == null} 返回 {@code false}</li>
 *   <li>终态到任何其他状态返回 {@code false}</li>
 * </ul>
 *
 * <p>实现示例见 README「状态枚举实现」章节。
 *
 * @param <E> 具体状态枚举类型
 * @author ydsz-team
 * @since 1.0.0
 */
public interface BaseStatusEnum<E extends Enum<E>> {

    /**
     * 校验状态流转是否合法。
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态流转到目标状态
     */
    boolean canTransitTo(E target);

    /**
     * 是否为终态。
     *
     * <p>终态不可再流转到其他状态。默认返回 {@code false}，
     * 有终态语义的枚举应覆写此方法。
     *
     * @return true 表示当前状态为终态
     */
    default boolean isTerminal() {
        return false;
    }

    /**
     * 校验状态流转，非法时抛出异常。
     *
     * @param target 目标状态
     * @throws com.njydsz.common.domain.exception.StateTransitionException 当状态流转非法时
     */
    default void requireTransitTo(E target) {
        if (!canTransitTo(target)) {
            throw new com.njydsz.common.domain.exception.StateTransitionException(this, target);
        }
    }
}
