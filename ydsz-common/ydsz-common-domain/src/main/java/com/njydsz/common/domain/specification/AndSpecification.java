package com.njydsz.common.domain.specification;

/**
 * AND 组合规约。
 *
 * <p>仅当左右两个规约均满足时返回 true。
 *
 * @param <T> 被规约评估的对象类型
 * @author ydsz-team
 * @since 26.09.13
 */
class AndSpecification<T> implements Specification<T> {

  private final Specification<T> left;
  private final Specification<T> right;

  /**
   * 构造 AND 组合规约。
   *
   * @param left 左规约
   * @param right 右规约
   */
  AndSpecification(Specification<T> left, Specification<T> right) {
    this.left = left;
    this.right = right;
  }

  @Override
  public boolean isSatisfiedBy(T candidate) {
    return left.isSatisfiedBy(candidate) && right.isSatisfiedBy(candidate);
  }
}
