package com.njydsz.pmis.common.domain.specification;

/**
 * 规约模式接口
 * <p>用于封装业务规则，支持规约组合（AND/OR/NOT）
 *
 * @param <T> 规约适用的类型
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
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
        return candidate -> isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate);
    }

    /**
     * 或操作：当前规约 OR 另一个规约
     *
     * @param other 另一个规约
     * @return 组合后的新规约
     */
    default Specification<T> or(Specification<T> other) {
        return candidate -> isSatisfiedBy(candidate) || other.isSatisfiedBy(candidate);
    }

    /**
     * 非操作：对当前规约取反
     *
     * @return 取反后的新规约
     */
    default Specification<T> not() {
        return candidate -> !isSatisfiedBy(candidate);
    }
}
