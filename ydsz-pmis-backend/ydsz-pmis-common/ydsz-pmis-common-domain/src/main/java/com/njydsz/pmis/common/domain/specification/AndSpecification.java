package com.njydsz.pmis.common.domain.specification;

import java.util.Objects;

/**
 * AND 规约组合
 *
 * <p>将两个规约通过逻辑与（AND）组合，只有当两个规约都满足时才满足。
 *
 * @param <T> 规约适用的类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 *
 * @see Specification
 */
public class AndSpecification<T> implements Specification<T> {

    private final Specification<T> left;
    private final Specification<T> right;

    /**
     * 构造 AND 规约
     *
     * @param left  左侧规约
     * @param right 右侧规约
     */
    public AndSpecification(Specification<T> left, Specification<T> right) {
        this.left = Objects.requireNonNull(left, "left specification must not be null");
        this.right = Objects.requireNonNull(right, "right specification must not be null");
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
        return left.isSatisfiedBy(candidate) && right.isSatisfiedBy(candidate);
    }

    /**
     * 获取左侧规约
     *
     * @return 左侧规约
     */
    public Specification<T> getLeft() {
        return left;
    }

    /**
     * 获取右侧规约
     *
     * @return 右侧规约
     */
    public Specification<T> getRight() {
        return right;
    }
}
