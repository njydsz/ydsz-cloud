package com.remisoft.common.domain.enums;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 状态流转工具类。
 *
 * <p>提供状态机的路径推导、合法下一跳查询能力，独立于 {@link BaseStatusEnum}，
 * 按需使用，避免强制所有状态枚举实现完整状态空间。
 *
 * <p><b>设计参考：</b>Spring Statemachine 的 StateMachineTransitionConfigurer。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 获取状态流转路径
 * List&lt;OrderStatus&gt; path = StateTransitionUtil.pathTo(
 *     OrderStatus.CREATED, OrderStatus.COMPLETED, OrderStatus.values(),
 *     status -&gt; Map.of(
 *         OrderStatus.CREATED, Set.of(OrderStatus.PAID, OrderStatus.CANCELLED),
 *         OrderStatus.PAID,   Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED)
 *     )
 * );
 * }</pre>
 *
 * @author remi-team
 * @since 1.8.0
 */
public final class StateTransitionUtil {

    private StateTransitionUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 获取从起始状态到目标状态的最短合法路径（BFS 搜索）。
     *
     * <p>适用于流程画布展示、审批路径预览等场景。
     *
     * @param start          起始状态
     * @param target         目标状态
     * @param allStates      所有状态枚举值
     * @param transitionMap  状态流转映射：源状态 → 可达目标状态集合
     * @param <E>            状态枚举类型
     * @return 最短路径（含起始和目标），空列表表示不可达
     */
    public static <E> List<E> pathTo(E start, E target, E[] allStates, Map<E, Set<E>> transitionMap) {
        if (start == null || target == null || allStates == null || allStates.length == 0) {
            return Collections.emptyList();
        }
        if (start == target) {
            return List.of(start);
        }

        // BFS
        Deque<E> queue = new ArrayDeque<>();
        Set<E> visited = new HashSet<>();
        Map<E, E> parentMap = new HashMap<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            E current = queue.poll();
            if (current == target) {
                return reconstructPath(parentMap, start, target);
            }

            Set<E> neighbors = transitionMap.getOrDefault(current, Collections.emptySet());
            for (E next : neighbors) {
                if (!visited.contains(next)) {
                    visited.add(next);
                    parentMap.put(next, current);
                    queue.add(next);
                }
            }
        }

        return Collections.emptyList(); // 不可达
    }

    /**
     * 获取指定状态的所有合法下一跳状态集合。
     *
     * <p>适用于前端下拉渲染、流程画布边、自动化规则配置等场景。
     *
     * @param state          当前状态
     * @param transitionMap  状态流转映射
     * @param <E>            状态枚举类型
     * @return 合法下一跳状态集合（非 null，可能为空）
     */
    public static <E> Set<E> successors(E state, Map<E, Set<E>> transitionMap) {
        if (state == null || transitionMap == null) {
            return Collections.emptySet();
        }
        return transitionMap.getOrDefault(state, Collections.emptySet());
    }

    /**
     * 校验状态流转是否合法。
     *
     * @param from           源状态
     * @param to             目标状态
     * @param transitionMap  状态流转映射
     * @param <E>            状态枚举类型
     * @return 允许流转返回 true
     */
    public static <E> boolean canTransit(E from, E to, Map<E, Set<E>> transitionMap) {
        if (from == null || to == null || transitionMap == null) {
            return false;
        }
        return transitionMap.getOrDefault(from, Collections.emptySet()).contains(to);
    }

    /**
     * 重建路径。
     */
    private static <E> List<E> reconstructPath(Map<E, E> parentMap, E start, E target) {
        LinkedList<E> path = new LinkedList<>();
        E node = target;
        while (node != null) {
            path.addFirst(node);
            if (node == start) {
                break;
            }
            node = parentMap.get(node);
        }
        return path;
    }
}
