package com.remisoft.common.domain.enums;

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
 * @author remi-team
 * @since 1.0.0
 * @since 1.6.0 增加 {@link #pathTo(Enum)} 状态流转路径推导与 {@link #successors()} 合法下一跳查询
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
     * {@link #pathTo(Enum)} 和 {@link #successors()} 使用此方法获取完整状态空间。
     *
     * @return 所有状态枚举值列表（非 null）
     * @since 1.6.0
     */
    default List<E> allStates() {
        return Collections.emptyList();
    }

    /**
     * 获取从当前状态到目标状态的最短合法路径（BFS 搜索）。
     *
     * <p>适用于流程画布展示、审批路径预览等场景。
     *
     * <p>算法：广度优先搜索（BFS），状态空间复杂度 O(V+E)。
     * 如需全量拓扑或最长路径，请使用工作流引擎（如 Flowable/activiti）。
     *
     * @param target 目标状态
     * @return 最短路径（含起始状态和目标状态），空列表表示不可达
     * @throws IllegalStateException 如果 {@link #allStates()} 返回空（实现类未覆写）
     * @since 1.6.0
     */
    default List<E> pathTo(E target) {
        if (target == null) {
            return Collections.emptyList();
        }
        if (this == target) {
            return List.of((E) this);
        }

        List<E> states = allStates();
        if (states.isEmpty()) {
            throw new IllegalStateException(
                "pathTo() requires non-empty allStates() return value. " +
                "Please override allStates() in enum " + this.getClass().getSimpleName());
        }

        // BFS
        Deque<E> queue = new ArrayDeque<>();
        Set<E> visited = new HashSet<>();
        java.util.Map<E, E> parentMap = new java.util.HashMap<>();

        E start = (E) this;
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            E current = queue.poll();
            if (current == target) {
                // 重建路径
                LinkedList<E> path = new LinkedList<>();
                E node = target;
                while (node != null) {
                    path.addFirst(node);
                    node = parentMap.get(node);
                }
                return path;
            }

            for (E next : states) {
                // E extends Enum<E> 不含接口方法，需显式转换为 BaseStatusEnum
                if (!visited.contains(next) && ((BaseStatusEnum<E>) current).canTransitTo(next)) {
                    visited.add(next);
                    parentMap.put(next, current);
                    queue.add(next);
                }
            }
        }

        return Collections.emptyList(); // 不可达
    }

    /**
     * 获取当前状态的所有合法下一跳状态集合。
     *
     * <p>适用于前端下拉渲染、流程画布边、自动化规则配置等场景。
     *
     * @return 合法下一跳状态集合（非 null，可能为空）
     * @since 1.6.0
     */
    default Set<E> successors() {
        List<E> states = allStates();
        Set<E> result = new HashSet<>();
        for (E state : states) {
            if (canTransitTo(state)) {
                result.add(state);
            }
        }
        return result;
    }
}
