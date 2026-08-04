package com.njydsz.common.domain.enums;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式状态流转注解。
 *
 * <p>标注在状态枚举常量上，声明该状态允许流转到的目标状态（及触发事件）。
 * 配合 {@link StateTransitionTable} 在运行时构建状态机表并执行流转校验，
 * 替代手写 {@code switch (this) {...}} 式 {@code canTransitTo} 实现，
 * 避免漏判边界、便于生成状态机文档与可视化。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public enum OrderStatus implements BaseStatusEnum<OrderStatus> {
 *     &#64;StateTransition(to = "PAID", event = "PAY")
 *     &#64;StateTransition(to = "CANCELLED", event = "CANCEL")
 *     PENDING,
 *
 *     &#64;StateTransition(to = "SHIPPED", event = "SHIP")
 *     &#64;StateTransition(to = "CANCELLED", event = "CANCEL")
 *     PAID,
 *
 *     &#64;StateTransition(to = "DELIVERED", event = "DELIVER")
 *     SHIPPED,
 *
 *     DELIVERED,   // 无出边 -> 终态
 *     CANCELLED;   // 无出边 -> 终态
 *
 *     &#64;Override
 *     public boolean canTransitTo(OrderStatus target) {
 *         return StateTransitionTable.of(OrderStatus.class).canTransit(this, target);
 *     }
 * }
 * }</pre>
 *
 * <p><b>与现有实现的兼容性：</b>本注解是增量能力，已有枚举可继续手写
 * {@code canTransitTo} 实现，两者互不影响。
 *
 * @param <E> 状态枚举类型（由使用方通过 {@link StateTransitionTable} 关联）
 * @author ydsz-team
 * @since 1.4.0
 *
 * @see StateTransitionTable
 * @see BaseStatusEnum
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(StateTransitions.class)
public @interface StateTransition {

    /**
     * 允许流转到的目标状态名（枚举常量名）。
     *
     * <p>可为多个目标状态，例如 {@code to = {"PAID", "CANCELLED"}}；
     * 至少需要一个非空值。
     *
     * @return 目标状态名数组
     */
    String[] to();

    /**
     * 触发该流转的业务事件（可选）。
     *
     * <p>用于表达"由什么操作触发"，如 {@code "PAY"} / {@code "CANCEL"}；
     * 不参与默认的 {@code canTransit(from, to)} 校验，仅在调用
     * {@link StateTransitionTable#canTransit(Enum, Enum, String)} 时按事件精确匹配。
     *
     * @return 事件名，默认空串表示任意事件均可触发
     */
    String event() default "";
}
