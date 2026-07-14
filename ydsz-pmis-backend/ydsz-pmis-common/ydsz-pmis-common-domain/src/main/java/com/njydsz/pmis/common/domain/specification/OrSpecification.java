package com.njydsz.pmis.common.domain.specification;

import java.util.Objects;

/**
 * OR 规约组合
 *
 * <p>将两个规约通过逻辑或（OR）组合，任一规约满足即满足。
 *
 * @param <T> 规约适用的类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 *
 * @see Specification
 */
public record OrSpecification<T>(Specification<T> left, Specification<T> right) implements Specification<T> {

    /**
     * 构造 OR 规约
     *
     * @param left  左侧规约
     * @param right 右侧规约
     */
    public OrSpecification {
        Objects.requireNonNull(left, "left specification must not be null");
        Objects.requireNonNull(right, "right specification must not be null");
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
        return left.isSatisfiedBy(candidate) || right.isSatisfiedBy(candidate);
    }
}
