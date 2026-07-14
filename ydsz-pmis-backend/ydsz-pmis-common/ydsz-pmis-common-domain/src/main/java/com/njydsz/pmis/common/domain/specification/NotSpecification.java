package com.njydsz.pmis.common.domain.specification;

import java.util.Objects;

/**
 * NOT 规约取反
 *
 * <p>对目标规约取逻辑非（NOT），满足时返回不满足，不满足时返回满足。
 *
 * @param <T> 规约适用的类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 *
 * @see Specification
 */
public class NotSpecification<T> implements Specification<T> {

    private final Specification<T> specification;

    /**
     * 构造取反规约
     *
     * @param specification 目标规约
     */
    public NotSpecification(Specification<T> specification) {
        this.specification = Objects.requireNonNull(specification, "specification must not be null");
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
        return !specification.isSatisfiedBy(candidate);
    }

    /**
     * 获取被取反的规约
     *
     * @return 目标规约
     */
    public Specification<T> getSpecification() {
        return specification;
    }
}
