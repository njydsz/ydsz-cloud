package com.njydsz.common.domain.specification;

/**
 * NOT 取反规约。
 *
 * <p>对委托规约的评估结果取反。
 *
 * @param <T> 被规约评估的对象类型
 * @author ydsz-team
 * @since 26.09.13
 */
class NotSpecification<T> implements Specification<T> {

  private final Specification<T> delegate;

  /**
   * 构造 NOT 取反规约。
   *
   * @param delegate 被取反的规约
   */
  NotSpecification(Specification<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean isSatisfiedBy(T candidate) {
    return !delegate.isSatisfiedBy(candidate);
  }
}
