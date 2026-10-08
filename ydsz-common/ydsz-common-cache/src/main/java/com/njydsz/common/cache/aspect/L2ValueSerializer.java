package com.njydsz.common.cache.aspect;

/**
 * L2（Redis）值编解码器 SPI — 将方法返回值序列化为 Redis 可存储的字符串，并在命中 L2 时反序列化还原。
 *
 * <p>切面默认提供 {@link DefaultL2ValueSerializer}（基于 JSON 反射编解码），业务方可按需实现本接口并注册为
 * Spring Bean 以替换默认实现（例如使用 protobuf、Hessian 或定制 Jackson MixIn）。
 *
 * <p>契约：
 *
 * <ul>
 *   <li>{@link #serialize(Object)} 返回 {@code null} 表示「不写入 L2」（例如不可序列化的类型）</li>
 *   <li>{@link #deserialize(String, Class)} 返回 {@code null} 表示「L2 内容无法还原」，
 *       调用方视同 L2 未命中</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see DefaultL2ValueSerializer
 * @see MultiLevelCacheAspect
 */
public interface L2ValueSerializer {

  /**
   * 将缓存值序列化为字符串，写入 L2（Redis StringOps）。
   *
   * @param value 缓存值（非 null）；返回 null 表示跳过 L2 写入
   * @return 序列化后的字符串
   */
  String serialize(Object value);

  /**
   * 从 L2 读取的字符串反序列化还原为缓存值。
   *
   * @param raw       L2 中存储的字符串
   * @param targetType 方法声明的返回类型（由切面提供）
   * @return 还原后的缓存值；返回 null 表示无法还原（调用方视同 L2 未命中）
   */
  Object deserialize(String raw, Class<?> targetType);
}
