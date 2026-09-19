package com.njydsz.common.json.internal;

import java.io.Serializable;
import java.util.Objects;

import com.njydsz.common.json.naming.PropertyNamingStrategy;

/**
 * JSON 序列化/反序列化不可变配置载体（P2-A1 引入：对标 Jackson {@code SerializationConfig}）。
 *
 * <p><b>设计意图（A-1 分阶段落地第一阶段）：</b> 将分散在 {@link
 * com.njydsz.common.json.provider.SerializationProvider.SerializationContext} 中的不可变配置字段
 * （writeNulls、prettyPrint、circularRefStrategy 等）集中到本不可变对象中，使其可通过方法参数显式传递，
 * 逐步替代运行时对 {@code ThreadLocal} 的依赖。
 *
 * <p><b>线程安全：</b>本对象所有字段均为 final，可在多线程间安全共享引用。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>通过 {@link #from(JsonRuntimeConfig)} 从预计算配置创建载体
 *   <li>通过 {@link JsonMapper#getConfigCarrier()} 获取当前 Mapper 的配置载体
 *   <li>在需要脱离 ThreadLocal 的显式传参场景中手工传递
 * </ul>
 *
 * @param isWriteNulls 是否输出 null 值字段
 * @param isPrettyPrint 是否格式化输出
 * @param circularRefStrategy 循环引用处理策略名称（REF / IGNORE / ERROR）
 * @param isSerializeEnumUsingOrdinal 枚举是否按序号序列化
 * @param dateFormat 日期格式字符串
 * @param isFailOnError 序列化失败时是否抛出异常
 * @param namingStrategy 字段命名策略
 * @param isUseBigDecimal 是否使用 BigDecimal 解析浮点数
 * @since 26.09.01
 * @author ydsz-team
 */
public record JsonConfigCarrier(
    boolean isWriteNulls,
    boolean isPrettyPrint,
    String circularRefStrategy,
    boolean isSerializeEnumUsingOrdinal,
    String dateFormat,
    boolean isFailOnError,
    PropertyNamingStrategy namingStrategy,
    boolean isUseBigDecimal)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * 从预计算运行时 {@link JsonRuntimeConfig} 创建配置载体。
   *
   * @param runtimeConfig 源运行时配置（不可为 null）
   * @return 配置载体实例
   * @throws NullPointerException 当 runtimeConfig 为 null 时
   */
  public static JsonConfigCarrier from(JsonRuntimeConfig runtimeConfig) {
    Objects.requireNonNull(runtimeConfig, "JsonConfigCarrier.from: runtimeConfig must not be null");
    return new JsonConfigCarrier(
        runtimeConfig.isWriteNulls(),
        runtimeConfig.isPrettyPrint(),
        runtimeConfig.circularRefStrategy(),
        runtimeConfig.isSerializeEnumUsingOrdinal(),
        runtimeConfig.dateFormat(),
        runtimeConfig.isFailOnError(),
        runtimeConfig.namingStrategy(),
        runtimeConfig.isUseBigDecimal());
  }
}
