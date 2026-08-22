package com.njydsz.common.util.config;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 静态工具类到 Spring Bean 的通用桥接器。
 *
 * <p>简化"非 Spring 管理的静态工具类安全获取 Spring Bean"的通用模式。 各静态工具类通过本类持有 Bean {@link Supplier}，由
 * AutoConfiguration 在容器就绪后调用 {@link #registerSupplier} 注入。
 *
 * <p>解析策略（DCL + volatile）：
 *
 * <ul>
 *   <li>首次解析成功后缓存结果，后续调用直接返回缓存（跳过 Supplier 查找）
 *   <li>注意：仅缓存成功的结果。Supplier 返回 null 或抛异常时不缓存，下次调用会重试
 *   <li>Supplier 未注册时返回 null
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 在静态工具类中声明桥接器
 * private static final StaticBridge<MessageSource> MESSAGE_SOURCE_BRIDGE = new StaticBridge<>();
 *
 * // 注册（由 AutoConfiguration 调用）
 * public static void setMessageSourceProvider(Supplier<MessageSource> supplier) {
 *     MESSAGE_SOURCE_BRIDGE.registerSupplier(supplier);
 * }
 *
 * // 使用
 * MessageSource source = MESSAGE_SOURCE_BRIDGE.getIfAvailable();
 * }</pre>
 *
 * <p>典型生命周期：{@code registerSupplier → getIfAvailable(解析+缓存) → 后续直接返回缓存}。
 *
 * <p>线程安全：volatile 保证 happens-before，synchronized 保证原子性。
 *
 * @param <T> Bean 类型
 * @author ydsz-team
 * @since 1.0.0
 */
public final class StaticBridge<T> {

  private final AtomicReference<T> cached = new AtomicReference<>();

  private volatile Supplier<T> supplier;

  /**
   * 注册 Bean 提供者。
   *
   * <p>由 AutoConfiguration 在容器初始化时调用。重新注册时会清空已有缓存， 以便下次 {@link #getIfAvailable()} 重新解析（适用于测试重置场景）。
   *
   * @param supplier Bean 提供者；为 null 时清空 Supplier 与缓存
   */
  public void registerSupplier(Supplier<T> supplier) {
    this.supplier = supplier;
    this.cached.set(null);
  }

  /**
   * 获取 Bean 实例。
   *
   * <p>仅成功结果被永久缓存；Supplier 未注册、返回 null 或抛异常时均返回 null， 调用方应自行降级处理。
   *
   * @return Bean 实例；不可用时返回 null
   */
  public T getIfAvailable() {
    T result = cached.get();
    if (result != null) {
      return result;
    }
    Supplier<T> s = supplier;
    if (s == null) {
      return null;
    }
    try {
      T bean = s.get();
      if (bean != null) {
        cached.compareAndSet(null, bean);
      }
      return bean;
    } catch (Exception e) {
      return null;
    }
  }

  /** 测试用：清空缓存与 Supplier。 */
  public void resetForTesting() {
    cached.set(null);
    supplier = null;
  }
}
