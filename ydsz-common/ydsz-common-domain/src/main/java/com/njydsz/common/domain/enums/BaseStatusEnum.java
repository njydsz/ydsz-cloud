package com.njydsz.common.domain.enums;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * 状态枚举统一抽象。
 *
 * <p>所有业务状态枚举应实现此接口，复用 {@link #canTransitTo(Enum)} 状态流转校验，
 * 避免各模块重复定义状态机逻辑。业务层可显式调用 {@code requireTransitTo} 实现状态变迁前置校验。
 *
 * <p>实现约定：
 * <ul>
 *   <li>自身到自身（{@code this == target}）返回 {@code true}</li>
 *   <li>{@code target == null} 返回 {@code false}</li>
 *   <li>终态到任何其他状态返回 {@code false}</li>
 * </ul>
 *
 * <p>路径推导、下一跳查询等高级能力请使用独立工具类 {@link StateTransitionUtil}，
 * 避免强制所有枚举实现完整状态空间。
 *
 * @param <E> 具体状态枚举类型
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.6.0 增加路径推导与下一跳查询
 * @since 1.8.0 pathTo/successors 标记废弃
 * @since 2.0.0 pathTo/successors 已移除，使用 {@link StateTransitionUtil} 替代
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
     * @throws IllegalStateException 当状态流转非法时
     */
    default void requireTransitTo(E target) {
        if (!canTransitTo(target)) {
            throw new IllegalStateException("Illegal state transition: " + this + " -> " + target);
        }
    }

    /**
     * 获取所有状态枚举值。
     *
     * <p>由实现类覆写，提供完整的状态集合。默认返回空列表，
     * 已废弃的 {@link #pathTo(Enum)} 使用此方法获取完整状态空间。
     *
     * @return 所有状态枚举值列表（非 null）
     * @since 1.6.0
     */
    default List<E> allStates() {
        return Collections.emptyList();
    }

}
