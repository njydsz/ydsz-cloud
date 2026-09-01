package com.njydsz.common.app.util;

import java.util.function.Supplier;

import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * 请求 ID 生成器
 *
 * <p>委托给 {@link SnowflakeIdGenerator} 统一生成分布式唯一 ID，用于在过滤器链中标识单次请求。 本类为工具类，禁止实例化。
 *
 * <p><b>线程安全性：</b>仅包含静态方法，无共享状态，线程安全； 底层 {@link SnowflakeIdGenerator} 通过注册的 {@link Supplier} 获取
 * Bean，无硬依赖。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class RequestIdGenerator {

  /** 由 AutoConfiguration 设置的 Supplier，用于替代 SpringContextHolder 查找 */
  private static volatile Supplier<SnowflakeIdGenerator> generatorSupplier;

  /**
   * 注册 SnowflakeIdGenerator 的 Supplier。
   *
   * @param supplier SnowflakeIdGenerator 提供者，非空
   */
  public static void setGeneratorSupplier(Supplier<SnowflakeIdGenerator> supplier) {
    generatorSupplier = supplier;
  }

  /**
   * 私有构造方法，工具类禁止实例化。
   *
   * @throws UnsupportedOperationException 任何实例化尝试都会抛出
   */
  private RequestIdGenerator() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 生成请求 ID
   *
   * @return 唯一请求 ID（雪花算法生成的 long 值字符串）
   */
  public static String generateId() {
    return String.valueOf(getGenerator().nextId());
  }

  /**
   * 生成带前缀的请求 ID
   *
   * <p>常用于区分多端或多个调用链的 ID 前缀。
   *
   * @param prefix 前缀，非空
   * @return 形如 {@code prefix + snowflakeId} 的字符串
   */
  public static String generateId(String prefix) {
    return prefix + getGenerator().nextId();
  }

  private static SnowflakeIdGenerator getGenerator() {
    Supplier<SnowflakeIdGenerator> supplier = generatorSupplier;
    if (supplier != null) {
      return supplier.get();
    }
    throw new IllegalStateException(
        "SnowflakeIdGenerator not initialized. "
            + "Ensure RequestIdGenerator bean is registered in Spring context.");
  }
}
