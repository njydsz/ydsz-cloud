package com.njydsz.common.domain.enums;

import java.util.List;

/**
 * 状态枚举统一抽象。
 *
 * <p>所有业务状态枚举应实现此接口，复用 {@link #canTransitTo(Enum)} 状态流转校验， 避免各模块重复定义状态机逻辑。业务层可显式调用 {@code
 * requireTransitTo} 实现状态变迁前置校验。
 *
 * <p>实现约定：
 *
 * <ul>
 *   <li>自身到自身返回 {@code true}
 *   <li>{@code target == null} 返回 {@code false}
 *   <li>终态到任何其他状态返回 {@code false}
 * </ul>
 *
 * @param <E> 具体状态枚举类型
 * @author ydsz-team
 * @since 26.09.01
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
   * <p>终态不可再流转到其他状态。默认返回 {@code false}， 有终态语义的枚举应覆写此方法。
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
   * <p>默认实现抛出 {@link UnsupportedOperationException}， 强制实现类显式覆写提供完整状态集合。避免默认返回空列表导致
   * 调用方误以为状态机无可用状态而产生隐蔽逻辑错误。
   *
   * @return 所有状态枚举值列表（非 null）
   * @throws UnsupportedOperationException 默认实现，需子类覆写
   * @since 26.09.01
   */
  default List<E> allStates() {
    throw new UnsupportedOperationException(
        "BaseStatusEnum.allStates() must be overridden by: " + this.getClass().getName());
  }
}
