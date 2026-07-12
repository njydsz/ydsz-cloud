package com.njydsz.pmis.common.entity.specification;

/**
 * DDD 规约模式接口 —— 封装领域规则为可组合的谓词。
 * <p>
 * 对标 remi-comm Specification，支持 AND / OR / NOT 组合操作。
 * 典型用法：
 * <pre>
 * Specification&lt;Project&gt; active = p -> p.getStatus() == Status.ACTIVE;
 * Specification&lt;Project&gt; budgetOk = p -> p.getBudget().compareTo(BigDecimal.valueOf(10000)) > 0;
 * Specification&lt;Project&gt; spec = active.and(budgetOk);
 * boolean match = spec.isSatisfiedBy(project);
 * </pre>
 * </p>
 *
 * @param <T> 候选对象类型
 * @author njydsz
 * @since 1.0.0
 */
@FunctionalInterface
public interface Specification<T> {

    /**
     * 判断候选对象是否满足此规约。
     *
     * @param candidate 候选对象
     * @return true 如果满足
     */
    boolean isSatisfiedBy(T candidate);

    /**
     * 与操作 —— 两个规约都满足。
     */
    default Specification<T> and(Specification<T> other) {
        return candidate -> this.isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate);
    }

    /**
     * 或操作 —— 任一规约满足。
     */
    default Specification<T> or(Specification<T> other) {
        return candidate -> this.isSatisfiedBy(candidate) || other.isSatisfiedBy(candidate);
    }

    /**
     * 非操作 —— 规约不满足。
     */
    default Specification<T> not() {
        return candidate -> !this.isSatisfiedBy(candidate);
    }

    /**
     * 静态工厂 —— 永真规约。
     */
    static <T> Specification<T> all() {
        return candidate -> true;
    }

    /**
     * 静态工厂 —— 永假规约。
     */
    static <T> Specification<T> none() {
        return candidate -> false;
    }
}
