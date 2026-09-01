package com.njydsz.common.safe.resilience;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * 熔断器注册表（自研引擎）。
 *
 * <p>以名称为键管理熔断器实例：默认配置（构造时给定）+ 实例级配置覆盖。
 * 同名实例全局唯一（{@link #circuitBreaker(String, CircuitBreakerConfig)} 幂等）。
 *
 * <p>替换 Spring 容器场景：将本类注册为共享 Bean，各模块按资源名（路由 ID / 服务名 /
 * 通道名 / 规则编码）获取或创建熔断器，即可获得统一的指标与事件出口。
 *
 * <p><b>线程安全：</b>底层 {@link ConcurrentHashMap#computeIfAbsent}，创建操作幂等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CircuitBreakerRegistry {

  private final CircuitBreakerConfig defaultConfig;
  private final ConcurrentMap<String, CircuitBreaker> circuitBreakers;

  /**
   * 以指定默认配置创建注册表。
   *
   * @param defaultConfig 默认配置（未提供实例级配置时使用）
   */
  public CircuitBreakerRegistry(CircuitBreakerConfig defaultConfig) {
    this.defaultConfig = defaultConfig;
    this.circuitBreakers = new ConcurrentHashMap<>();
  }

  /**
   * 以默认配置创建注册表。
   *
   * @return 注册表实例
   */
  public static CircuitBreakerRegistry ofDefaults() {
    return new CircuitBreakerRegistry(CircuitBreakerConfig.ofDefaults());
  }

  /**
   * 以指定配置创建注册表。
   *
   * @param config 默认配置
   * @return 注册表实例
   */
  public static CircuitBreakerRegistry of(CircuitBreakerConfig config) {
    return new CircuitBreakerRegistry(config);
  }

  /**
   * 获取或创建指定名称的熔断器（使用注册表默认配置；已存在则忽略配置参数，保持幂等）。
   *
   * @param name 熔断器名称
   * @return 熔断器实例
   */
  public CircuitBreaker circuitBreaker(String name) {
    return circuitBreaker(name, defaultConfig);
  }

  /**
   * 获取或创建指定名称的熔断器（实例级配置覆盖；已存在则忽略配置参数，保持幂等）。
   *
   * @param name 熔断器名称
   * @param config 实例级配置
   * @return 熔断器实例
   */
  public CircuitBreaker circuitBreaker(String name, CircuitBreakerConfig config) {
    return circuitBreakers.computeIfAbsent(name, key -> new CircuitBreaker(key, config));
  }

  /**
   * 查找指定名称的熔断器（不创建）。
   *
   * @param name 熔断器名称
   * @return 熔断器实例；不存在时为空
   */
  public Optional<CircuitBreaker> find(String name) {
    return Optional.ofNullable(circuitBreakers.get(name));
  }

  /**
   * 获取指定名称的熔断器（不存在时返回 null，等价 {@code find(name).orElse(null)}）。
   *
   * @param name 熔断器名称
   * @return 熔断器实例；不存在时为 null
   */
  public CircuitBreaker get(String name) {
    return circuitBreakers.get(name);
  }

  /**
   * 移除指定名称的熔断器（释放资源）。
   *
   * @param name 熔断器名称
   * @return 被移除的熔断器；不存在时为 null
   */
  public CircuitBreaker remove(String name) {
    return circuitBreakers.remove(name);
  }

  /**
   * 获取注册表默认配置。
   *
   * @return 默认配置
   */
  public CircuitBreakerConfig getDefaultConfig() {
    return defaultConfig;
  }

  /**
   * 获取当前已注册的熔断器名称集合。
   *
   * @return 不可变名称集合
   */
  public java.util.Set<String> getRegisteredNames() {
    return java.util.Collections.unmodifiableSet(circuitBreakers.keySet());
  }

  /**
   * 惰性获取或创建（供函数式调用方使用）。
   *
   * @param name 熔断器名称
   * @param configSupplier 配置提供者（仅在首次创建时调用）
   * @return 熔断器实例
   */
  public CircuitBreaker computeIfAbsent(String name, Supplier<CircuitBreakerConfig> configSupplier) {
    return circuitBreakers.computeIfAbsent(
        name, key -> new CircuitBreaker(key, configSupplier.get()));
  }
}
