package com.njydsz.common.domain.specification;

import java.util.Objects;

/**
 * AND 规约组合
 *
 * <p>将两个规约通过逻辑与（AND）组合，只有当两个规约都满足时才满足。
 *
 * @param <T> 规约适用的类型
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see Specification
 */
public record AndSpecification<T>(Specification<T> left, Specification<T> right) implements Specification<T> {

    /**
     * 构造 AND 规约
     *
     * @param left  左侧规约
     * @param right 右侧规约
     */
    public AndSpecification {
        Objects.requireNonNull(left, "left specification must not be null");
        Objects.requireNonNull(right, "right specification must not be null");
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
        return left.isSatisfiedBy(candidate) && right.isSatisfiedBy(candidate);
    }
}
