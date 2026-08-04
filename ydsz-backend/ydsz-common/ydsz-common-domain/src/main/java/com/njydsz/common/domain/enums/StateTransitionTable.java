package com.njydsz.common.domain.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 声明式状态机表：基于 {@link StateTransition} 注解构建并执行流转校验。
 *
 * <p>为采用注解驱动的状态枚举提供统一的状态机能力：
 * <ul>
 *   <li>在首次访问时解析枚举常量上的 {@link StateTransition} 注解，构建不可变流转表</li>
 *   <li>表按枚举 Class 缓存（{@link ConcurrentHashMap}），线程安全且避免重复解析</li>
 *   <li>提供 {@link #canTransit(Enum, Enum)} 基础校验与
 *       {@link #canTransit(Enum, Enum, String)} 事件级精确校验</li>
 *   <li>未标注任何 {@link StateTransition} 出边的常量视为<b>终态</b>（保守策略）</li>
 *   <li>允许自身到自身的流转（与 {@link BaseStatusEnum} 契约一致）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public enum OrderStatus implements BaseStatusEnum<OrderStatus> {
 *     &#64;StateTransition(to = "PAID", event = "PAY")
 *     PENDING,
 *     PAID,
 *     SHIPPED;
 *
 *     &#64;Override
 *     public boolean canTransitTo(OrderStatus target) {
 *         return StateTransitionTable.of(OrderStatus.class).canTransit(this, target);
 *     }
 * }
 *
 * // 事件级校验
 * boolean ok = StateTransitionTable.of(OrderStatus.class)
 *         .canTransit(OrderStatus.PENDING, OrderStatus.PAID, "PAY");
 * // 可视化：allowedTargets(PENDING) -> [PAID]
 * }</pre>
 *
 * @param <E> 状态枚举类型
 * @author ydsz-team
 * @since 1.4.0
 *
 * @see StateTransition
 * @see BaseStatusEnum
 */
public final class StateTransitionTable<E extends Enum<E>> {

    /** 枚举 Class -> 状态机表 缓存 */
    private static final Map<Class<?>, StateTransitionTable<?>> CACHE = new ConcurrentHashMap<>();

    /** 各状态的全部允许目标集（含事件声明目标，不可变） */
    private final Map<E, Set<E>> transitions;

    /** 各状态"任意事件"（未声明 event）允许目标集（不可变） */
    private final Map<E, Set<E>> anyEventTargets;

    /** 各状态按事件区分的允许目标集（不可变） */
    private final Map<E, Map<String, Set<E>>> transitionsByEvent;

    private StateTransitionTable(Class<E> enumClass) {
        EnumMap<E, Set<E>> targets = new EnumMap<>(enumClass);
        EnumMap<E, Set<E>> anyTargets = new EnumMap<>(enumClass);
        EnumMap<E, Map<String, Set<E>>> byEvent = new EnumMap<>(enumClass);

        for (E constant : enumClass.getEnumConstants()) {
            targets.put(constant, new LinkedHashSet<>());
            anyTargets.put(constant, new LinkedHashSet<>());
            // 事件名是 String，非枚举常量，不能使用 EnumMap
            byEvent.put(constant, new java.util.HashMap<>());
        }

        for (E constant : enumClass.getEnumConstants()) {
            StateTransition[] transitionsArr = readTransitions(constant);
            for (StateTransition st : transitionsArr) {
                for (String toName : st.to()) {
                    E target = toEnumConstant(enumClass, toName, constant);
                    targets.get(constant).add(target);
                    if (st.event() == null || st.event().isEmpty()) {
                        anyTargets.get(constant).add(target);
                    } else {
                        byEvent.get(constant)
                                .computeIfAbsent(st.event(), k -> new LinkedHashSet<>())
                                .add(target);
                    }
                }
            }
        }

        this.transitions = freezeEnumMap(targets);
        this.anyEventTargets = freezeEnumMap(anyTargets);
        EnumMap<E, Map<String, Set<E>>> frozenByEvent = new EnumMap<>(enumClass);
        for (Map.Entry<E, Map<String, Set<E>>> e : byEvent.entrySet()) {
            // 事件名是 String，非枚举常量，不能使用 EnumMap
            Map<String, Set<E>> frozenEventMap = new java.util.HashMap<>();
            for (Map.Entry<String, Set<E>> ee : e.getValue().entrySet()) {
                frozenEventMap.put(ee.getKey(), Collections.unmodifiableSet(ee.getValue()));
            }
            frozenByEvent.put(e.getKey(), Collections.unmodifiableMap(frozenEventMap));
        }
        this.transitionsByEvent = Collections.unmodifiableMap(frozenByEvent);
    }

    /**
     * 获取指定枚举的声明式状态机表（按 Class 缓存，线程安全）。
     *
     * @param enumClass 状态枚举 Class
     * @param <E>       枚举类型
     * @return 状态机表实例
     */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> StateTransitionTable<E> of(Class<E> enumClass) {
        StateTransitionTable<?> table = CACHE.get(enumClass);
        if (table == null) {
            table = new StateTransitionTable<>(enumClass);
            StateTransitionTable<?> existing = CACHE.putIfAbsent(enumClass, table);
            if (existing != null) {
                table = existing;
            }
        }
        return (StateTransitionTable<E>) table;
    }

    /**
     * 判断是否允许从 {@code from} 流转到 {@code to}。
     *
     * <p>自身到自身恒为 true；无出边（终态）返回 false。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return 允许返回 true
     */
    public boolean canTransit(E from, E to) {
        if (from == to) {
            return true;
        }
        Set<E> allowed = transitions.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * 判断是否允许在指定事件下从 {@code from} 流转到 {@code to}。
     *
     * <p>匹配规则：先按事件精确匹配；其次匹配"未声明事件"的任意事件声明。
     * 自身到自身恒为 true；事件为空时退化为 {@link #canTransit(Enum, Enum)}。
     *
     * @param from  当前状态
     * @param to    目标状态
     * @param event 触发事件（可为 null/空，退化为无事件校验）
     * @return 允许返回 true
     */
    public boolean canTransit(E from, E to, String event) {
        if (from == to) {
            return true;
        }
        if (event == null || event.isBlank()) {
            return canTransit(from, to);
        }
        Map<String, Set<E>> eventMap = transitionsByEvent.get(from);
        if (eventMap != null && eventMap.containsKey(event)
                && eventMap.get(event).contains(to)) {
            return true;
        }
        Set<E> anySet = anyEventTargets.get(from);
        return anySet != null && anySet.contains(to);
    }

    /**
     * 获取从当前状态可流转到的全部目标状态。
     *
     * @param from 当前状态
     * @return 目标状态集合（不可变）；无出边返回空集
     */
    public Set<E> allowedTargets(E from) {
        Set<E> allowed = transitions.get(from);
        return allowed != null ? allowed : Collections.emptySet();
    }

    /**
     * 判断是否为终态（无任何声明出边）。
     *
     * @param state 状态
     * @return 终态返回 true
     */
    public boolean isTerminal(E state) {
        Set<E> allowed = transitions.get(state);
        return allowed == null || allowed.isEmpty();
    }

    /**
     * 校验流转是否合法，非法时抛出异常（{@link BaseStatusEnum#requireTransitTo} 的注解驱动版本）。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @throws IllegalStateException 当流转非法时
     */
    public void requireTransit(E from, E to) {
        if (!canTransit(from, to)) {
            throw new IllegalStateException(
                    "非法状态流转: " + from + " -> " + to);
        }
    }

    /** 读取枚举常量上的 @StateTransition（兼容 @Repeatable 容器形式） */
    private static <E extends Enum<E>> StateTransition[] readTransitions(E constant) {
        try {
            java.lang.reflect.Field field = constant.getClass().getField(constant.name());
            StateTransitions container = field.getAnnotation(StateTransitions.class);
            if (container != null) {
                return container.value();
            }
            StateTransition single = field.getAnnotation(StateTransition.class);
            return single != null ? new StateTransition[] {single} : new StateTransition[0];
        } catch (NoSuchFieldException e) {
            return new StateTransition[0];
        }
    }

    /** 将目标状态名解析为枚举常量 */
    private static <E extends Enum<E>> E toEnumConstant(Class<E> enumClass, String name, E from) {
        try {
            return Enum.valueOf(enumClass, name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "StateTransition.to 引用不存在的枚举常量: " + name
                    + "（声明于 " + from + "）", e);
        }
    }

    /** 冻结 EnumMap 为不可变（Set 值亦不可变） */
    private static <E extends Enum<E>> Map<E, Set<E>> freezeEnumMap(Map<E, Set<E>> source) {
        EnumMap<E, Set<E>> frozen = new EnumMap<>(source);
        for (Map.Entry<E, Set<E>> e : frozen.entrySet()) {
            e.setValue(Collections.unmodifiableSet(e.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }
}
