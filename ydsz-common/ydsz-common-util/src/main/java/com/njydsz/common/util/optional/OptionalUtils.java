package com.njydsz.common.util.optional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@link Optional} 工具类 — 补全 JDK Optional 的高频缺失能力。
 *
 * <p>JDK 的 {@link Optional} 在企业级开发中常遇以下痛点：
 *
 * <ul>
 *   <li>链式"或"操作：语义不直观</li>
 *   <li>与 Stream 互转：无法直接 {@code Optional → Stream}</li>
 *   <li>集合筛选：缺少一次性过滤 {@code Collection<Optional<T>} → Collection<T} 的能力</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // Optional → Stream（常用于 flatMap 链）
 * Stream<String> stream = OptionalUtils.stream(Optional.of("hello")); // ["hello"]
 *
 * // 链式或
 * Optional<String> first = OptionalUtils.or(Optional.empty(), () -> Optional.of("fallback"));
 *
 * // 集合中的 Optional 过滤
 * List<String> nonEmpty = OptionalUtils.presentOnly(listOfOptionals);
 *
 * // 缺省时抛出自定义异常
 * String value = OptionalUtils.orElseThrowOr(opt, () -> new BizException("missing"));
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public final class OptionalUtils {

  private OptionalUtils() {
    throw new UnsupportedOperationException(
        "OptionalUtils is a utility class and cannot be instantiated");
  }

  // ==================== Optional ↔ Stream 互转 ====================

  /**
   * 将 {@link Optional} 转换为 {@code Stream}。
   *
   * <p>若 Optional 有值则返回包含该值的单元素流，否则返回空流。
   *
   * <p>典型场景：在 {@code flatMap} 链中统一处理 Optional 值。
   *
   * <pre>{@code
   * list.stream()
   *     .flatMap(item -> OptionalUtils.stream(maybeTransform(item)))
   *     .collect(Collectors.toList());
   * }</pre>
   *
   * @param optional 待转换的 Optional（不可为 null）
   * @param <T> 元素类型
   * @return 包含 Optional 值的单元素流或空流
   * @throws NullPointerException 如果 optional 为 null
   * @since 26.09.19
   */
  public static <T> Stream<T> stream(Optional<T> optional) {
    Objects.requireNonNull(optional, "optional must not be null");
    return optional.map(Stream::of).orElseGet(Stream::empty);
  }

  // ==================== 链式 OR 操作 ====================

  /**
   * 链式"或"操作：当前 Optional 有值时返回自身，否则返回备选 Optional。
   *
   * <p>语义与 JDK 9+ {@code Optional.or(Supplier)} 一致，但为兼容性自实现。
   *
   * <pre>{@code
   * OptionalUtils.or(Optional.empty(), () -> Optional.of("fallback"));
   * // => Optional["fallback"]
   *
   * OptionalUtils.or(Optional.of("primary"), () -> Optional.of("fallback"));
   * // => Optional["primary"]
   * }</pre>
   *
   * @param primary 优先 Optional（不可为 null）
   * @param fallbackSupplier 备选 Optional 提供者（不可为 null）
   * @param <T> 元素类型
   * @return primary 非空时返回 primary，否则返回 fallbackSupplier 的获取结果
   * @throws NullPointerException 如果 primary 或 fallbackSupplier 为 null
   * @since 26.09.19
   */
  public static <T> Optional<T> or(Optional<T> primary, Supplier<Optional<T>> fallbackSupplier) {
    Objects.requireNonNull(primary, "primary must not be null");
    Objects.requireNonNull(fallbackSupplier, "fallbackSupplier must not be null");
    return primary.isPresent() ? primary : fallbackSupplier.get();
  }

  // ==================== 集合过滤（presence-only） ====================

  /**
   * 从集合中过滤出所有非空 Optional 的值。
   *
   * <p>典型场景：批量查询后有部分返回 null/empty，只想保留有值的结果。
   *
   * <pre>{@code
   * List<Optional<User>> optionals = ids.stream()
   *     .map(userRepo::findById)
   *     .collect(toList());
   * List<User> users = OptionalUtils.presentOnly(optionals);
   * }</pre>
   *
   * @param optionals Optional 集合（不可为 null）
   * @param <T> 元素类型
   * @return 仅包含非空值的新 ArrayList；输入为空集合时返回空 List
   * @throws NullPointerException 如果 optionals 为 null
   * @since 26.09.19
   */
  public static <T> List<T> presentOnly(Collection<Optional<T>> optionals) {
    Objects.requireNonNull(optionals, "optionals must not be null");
    return optionals.stream()
        .filter(Objects::nonNull)
        .flatMap(OptionalUtils::stream)
        .collect(Collectors.toList());
  }

  // ==================== 缺省时抛出自定义异常 ====================

  /**
   * 若 Optional 有值则返回值，否则抛出由 {@link Supplier} 提供的异常。
   *
   * <p>等价于 JDK {@link Optional#orElseThrow(Supplier)}，
   * 但本方法额外提供统一的 null 检查。
   *
   * @param optional 待检查的 Optional（不可为 null）
   * @param exceptionSupplier 异常提供者（不可为 null）
   * @param <T> 元素类型
   * @param <X> 异常类型
   * @return Optional 中的值（非空时）
   * @throws X Optional 为空时抛出
   * @throws NullPointerException 如果 optional 或 exceptionSupplier 为 null
   * @since 26.09.19
   */
  public static <T, X extends Throwable> T orElseThrowOr(
      Optional<T> optional, Supplier<? extends X> exceptionSupplier) throws X {
    Objects.requireNonNull(optional, "optional must not be null");
    Objects.requireNonNull(exceptionSupplier, "exceptionSupplier must not be null");
    if (optional.isPresent()) {
      return optional.get();
    }
    throw exceptionSupplier.get();
  }
}
