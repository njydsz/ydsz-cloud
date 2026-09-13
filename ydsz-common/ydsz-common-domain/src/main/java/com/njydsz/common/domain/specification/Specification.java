package com.njydsz.common.domain.specification;

/**
 * 规约模式（Specification Pattern）接口。
 *
 * <p>规约模式是 DDD 中用于封装业务规则的可组合单元。每个规约代表一条业务规则，
 * 可通过 {@link #and}、{@link #or}、{@link #negate} 进行组合，形成复杂的规则表达式。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 1. 实现具体规约
 * public class ActiveUserSpec implements Specification<User> {
 *     @Override
 *     public boolean isSatisfiedBy(User user) {
 *         return user.getStatus() == UserStatus.ACTIVE;
 *     }
 * }
 *
 * // 2. 组合规约
 * Specification<User> spec = new ActiveUserSpec().and(new InDeptSpec(deptId));
 * List<User> filtered = users.stream().filter(spec::isSatisfiedBy).toList();
 * }</pre>
 *
 * <p><b>SPI：</b>业务模块通过 {@code @Component} 注册自定义规约实现，
 * {@link SpecificationRegistry} 自动收集并可根据名称获取。
 *
 * @param <T> 被规约评估的对象类型
 * @author ydsz-team
 * @since 26.09.13
 * @see SpecificationRegistry
 */
public interface Specification<T> {

    /**
     * 评估候选对象是否满足本规约。
     *
     * @param candidate 被评估的候选对象
     * @return true=满足规约；false=不满足
     */
    boolean isSatisfiedBy(T candidate);

    /**
     * 与另一个规约进行 AND 组合。
     *
     * @param other 另一个规约
     * @return 组合后的规约（两者都满足才返回 true）
     */
    default Specification<T> and(Specification<T> other) {
        return new AndSpecification<>(this, other);
    }

    /**
     * 与另一个规约进行 OR 组合。
     *
     * @param other 另一个规约
     * @return 组合后的规约（任一满足即返回 true）
     */
    default Specification<T> or(Specification<T> other) {
        return new OrSpecification<>(this, other);
    }

    /**
     * 对当前规约取反。
     *
     * @return 取反后的规约
     */
    default Specification<T> negate() {
        return new NotSpecification<>(this);
    }

    /**
     * 恒真规约（满足所有对象）。
     *
     * @param <T> 对象类型
     * @return 恒真规约实例
     */
    static <T> Specification<T> alwaysTrue() {
        return candidate -> true;
    }

    /**
     * 恒假规约（不满足任何对象）。
     *
     * @param <T> 对象类型
     * @return 恒假规约实例
     */
    static <T> Specification<T> alwaysFalse() {
        return candidate -> false;
    }
}
