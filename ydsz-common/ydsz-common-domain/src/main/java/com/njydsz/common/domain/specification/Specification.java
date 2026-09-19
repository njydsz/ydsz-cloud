package com.njydsz.common.domain.specification;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

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
 * // 2. 组合规约（二元组合）
 * Specification<User> spec = new ActiveUserSpec().and(new InDeptSpec(deptId));
 * List<User> filtered = users.stream().filter(spec::isSatisfiedBy).toList();
 *
 * // 3. 批量组合（来自动态规则）
 * Specification<User> combined = Specification.allOf(activeSpec, deptSpec, roleSpec);
 *
 * // 4. Lambda 快速构建
 * Specification<User> quick = Specification.fromPredicate(u -> u.getAge() >= 18);
 * }</pre>
 *
 * @param <T> 被规约评估的对象类型
 * @author ydsz-team
 * @since 26.09.13
 * @see #and(Specification)
 * @see #or(Specification)
 * @see #negate()
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
   * 从 Lambda 表达式快速构建规约。
   *
   * <p>适用于简单业务规则，避免为每个规则创建单独的实现类。
   *
   * @param predicate 判断表达式
   * @param <T> 对象类型
   * @return 规约实例（永不为 {@code null}）
   * @throws NullPointerException 当 predicate 为 {@code null} 时
   * @since 26.09.19
   */
  static <T> Specification<T> fromPredicate(Predicate<T> predicate) {
    Objects.requireNonNull(predicate, "Predicate must not be null");
    return predicate::test;
  }

  /**
   * AND 组合所有规约（全部满足才返回 true）。
   *
   * <p>空列表或空数组时返回 {@link #alwaysTrue()}。
   *
   * @param specs 规约列表
   * @param <T> 对象类型
   * @return 组合后的规约（永不为 {@code null}）
   * @throws NullPointerException 当 specs 为 {@code null} 时
   * @since 26.09.19
   */
  @SafeVarargs
  static <T> Specification<T> allOf(Specification<T>... specs) {
    if (specs == null || specs.length == 0) {
      return alwaysTrue();
    }
    Specification<T> result = specs[0];
    for (int i = 1; i < specs.length; i++) {
      result = result.and(specs[i]);
    }
    return result;
  }

  /**
   * OR 组合所有规约（任一满足即返回 true）。
   *
   * <p>空列表或空数组时返回 {@link #alwaysFalse()}。
   *
   * @param specs 规约列表
   * @param <T> 对象类型
   * @return 组合后的规约（永不为 {@code null}）
   * @throws NullPointerException 当 specs 为 {@code null} 时
   * @since 26.09.19
   */
  @SafeVarargs
  static <T> Specification<T> anyOf(Specification<T>... specs) {
    if (specs == null || specs.length == 0) {
      return alwaysFalse();
    }
    Specification<T> result = specs[0];
    for (int i = 1; i < specs.length; i++) {
      result = result.or(specs[i]);
    }
    return result;
  }

  /**
   * OR 组合所有规约后取反（全部不满足才返回 true = 任何一个都不满足）。
   *
   * <p>等价于 {@code anyOf(specs).negate()}，语义更清晰。
   *
   * @param specs 规约列表
   * @param <T> 对象类型
   * @return 组合后的规约
   * @throws NullPointerException 当 specs 为 {@code null} 时
   * @since 26.09.19
   */
  @SafeVarargs
  static <T> Specification<T> noneOf(Specification<T>... specs) {
    return anyOf(specs).negate();
  }

  /**
   * AND 组合规约列表。
   *
   * @param specs 规约列表
   * @param <T> 对象类型
   * @return 组合后的规约
   * @throws NullPointerException 当 specs 为 {@code null} 或列表中包含 {@code null} 元素时
   * @since 26.09.19
   */
  static <T> Specification<T> allOf(List<Specification<T>> specs) {
    if (specs == null || specs.isEmpty()) {
      return alwaysTrue();
    }
    Specification<T> result = Objects.requireNonNull(specs.get(0), "Specification must not be null");
    for (int i = 1; i < specs.size(); i++) {
      result = result.and(Objects.requireNonNull(specs.get(i), "Specification must not be null"));
    }
    return result;
  }

  /**
   * OR 组合规约列表。
   *
   * @param specs 规约列表
   * @param <T> 对象类型
   * @return 组合后的规约
   * @throws NullPointerException 当 specs 为 {@code null} 或列表中包含 {@code null} 元素时
   * @since 26.09.19
   */
  static <T> Specification<T> anyOf(List<Specification<T>> specs) {
    if (specs == null || specs.isEmpty()) {
      return alwaysFalse();
    }
    Specification<T> result = Objects.requireNonNull(specs.get(0), "Specification must not be null");
    for (int i = 1; i < specs.size(); i++) {
      result = result.or(Objects.requireNonNull(specs.get(i), "Specification must not be null"));
    }
    return result;
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
