package com.njydsz.common.domain.specification;

import java.util.function.Predicate;

/**
 * 规约模式接口
 *
 * <p>用于封装业务规则，支持规约组合（AND/OR/NOT）。
 * 规约模式将业务规则从业务逻辑中分离出来，使规则可组合、可测试、可复用。
 *
 * <p><b>核心语义：</b>
 * <ul>
 *   <li>谓词封装：将业务规则封装为独立的谓词对象</li>
 *   <li>可组合性：通过 AND/OR/NOT 操作组合复杂规则</li>
 *   <li>可测试性：每个规约可独立测试</li>
 *   <li>可复用性：规约可在不同的业务场景中复用</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 定义规约
 * Specification<Order> isPending = Specification.where(o -> o.getStatus() == OrderStatus.PENDING);
 * Specification<Order> isHighValue = Specification.where(o -> o.getAmount().compareTo(BigDecimal.valueOf(10000)) > 0);
 *
 * // 组合规约
 * Specification<Order> pendingHighValueOrders = isPending.and(isHighValue);
 *
 * // 使用规约
 * List<Order> filtered = orders.stream()
 *     .filter(pendingHighValueOrders::isSatisfiedBy)
 *     .collect(Collectors.toList());
 * }</pre>
 *
 * @param <T> 规约适用的类型
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public interface Specification<T> {

    /**
     * 判断候选对象是否满足规约条件
     *
     * @param candidate 候选对象
     * @return 满足返回 true，否则返回 false
     */
    boolean isSatisfiedBy(T candidate);

    /**
     * 与操作：当前规约 AND 另一个规约
     *
     * @param other 另一个规约
     * @return 组合后的新规约
     */
    default Specification<T> and(Specification<T> other) {
        return new AndSpecification<>(this, other);
    }

    /**
     * 或操作：当前规约 OR 另一个规约
     *
     * @param other 另一个规约
     * @return 组合后的新规约
     */
    default Specification<T> or(Specification<T> other) {
        return new OrSpecification<>(this, other);
    }

    /**
     * 非操作：对当前规约取反
     *
     * @return 取反后的新规约
     */
    default Specification<T> not() {
        return new NotSpecification<>(this);
    }

    // ── 静态工厂方法 ──────────────────────────────────────────────────────────

    /**
     * 创建基于 Predicate 的规约
     *
     * @param predicate 谓词
     * @param <T>       规约适用的类型
     * @return 规约实例
     */
    static <T> Specification<T> where(Predicate<T> predicate) {
        return predicate::test;
    }

    /**
     * 创建始终满足的规约
     *
     * @param <T> 规约适用的类型
     * @return 始终返回 true 的规约
     */
    static <T> Specification<T> always() {
        return candidate -> true;
    }

    /**
     * 创建始终不满足的规约
     *
     * @param <T> 规约适用的类型
     * @return 始终返回 false 的规约
     */
    static <T> Specification<T> never() {
        return candidate -> false;
    }
}
