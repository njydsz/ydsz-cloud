package com.njydsz.pmis.common.entity.specification;

import java.util.ArrayList;
import java.util.List;

/**
 * 复合规约 —— 将多个规约组合为一个 AND 关系的复合规约。
 * <p>
 * 对标 remi-comm CompositeSpecification，适用于需要动态拼接规则的场景。
 * </p>
 *
 * @param <T> 候选对象类型
 * @author njydsz
 * @since 1.0.0
 */
public class CompositeSpecification<T> implements Specification<T> {

    private final List<Specification<T>> specifications = new ArrayList<>();

    public CompositeSpecification<T> add(Specification<T> spec) {
        if (spec != null) {
            this.specifications.add(spec);
        }
        return this;
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
        for (Specification<T> spec : specifications) {
            if (!spec.isSatisfiedBy(candidate)) {
                return false;
            }
        }
        return true;
    }

    public List<Specification<T>> getSpecifications() {
        return List.copyOf(specifications);
    }
}
