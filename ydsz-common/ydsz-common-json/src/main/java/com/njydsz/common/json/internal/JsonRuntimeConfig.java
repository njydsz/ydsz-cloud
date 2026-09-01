package com.njydsz.common.json.internal;

import java.io.Serializable;

import com.njydsz.common.json.naming.PropertyNamingStrategy;

/**
 * JSON 运行时预计算配置（不可变快照）。
 *
 * <p>从 {@link JsonConfig} 派生的预计算配置对象，用于替代 ThreadLocal 中的配置字段传递。 所有字段均为 {@code final}，天然线程安全，读取无锁无
 * ThreadLocal 开销。
 *
 * <p><b>设计目标：</b>
 *
 * <ul>
 *   <li>将不可变配置从 {@code ThreadLocal<SerializationContext>} 中分离出来， 减少 ThreadLocal 体积和内存泄漏风险
 *   <li>允许显式传递配置给序列化/反序列化组件（{@code JSONWriter} / {@code JSONReader}）， 消除对 ThreadLocal 查询的依赖
 *   <li>生命周期绑定到 {@link JsonMapper} 实例，而非线程，支持多配置并行
 * </ul>
 *
 * <p><b>不可变语义：</b>对标 Jackson {@code SerializationConfig} / {@code DeserializationConfig}，
 * 一次构建后只读，如需修改配置请构建新的 {@link JsonConfig} 并重新生成。
 *
 * @param namingStrategy 字段命名策略（如 SNAKE_CASE / LOWER_CAMEL_CASE）
 * @param writeNulls 是否输出 null 值
 * @param prettyPrint 是否格式化输出
 * @param circularRefStrategy 循环引用处理策略名称（REF / IGNORE / ERROR）
 * @param serializeEnumUsingOrdinal 枚举是否使用序号序列化
 * @param dateFormat 全局日期格式（空串表示使用默认 ISO 格式）
 * @param failOnError 序列化失败时是否抛出异常
 * @param defaultDateFormat 反序列化时未显式指定格式的日期默认解析模式
 * @param maxJsonSize 单次 JSON 处理的最大字节数上限
 * @param maxDepth 最大嵌套深度（防止栈溢出）
 * @param maxGenericDepth 泛型递归深度上限
 * @param useBigDecimal 是否将浮点数解析为 BigDecimal
 * @param wrapRootValue 是否启用根名称包裹
 * @since 26.09.01
 * @author ydsz-team
 */
public record JsonRuntimeConfig(
    PropertyNamingStrategy namingStrategy,
    boolean writeNulls,
    boolean prettyPrint,
    String circularRefStrategy,
    boolean serializeEnumUsingOrdinal,
    String dateFormat,
    boolean failOnError,
    String defaultDateFormat,
    long maxJsonSize,
    int maxDepth,
    int maxGenericDepth,
    boolean useBigDecimal,
    boolean wrapRootValue)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * 默认运行时配置快照（从全局已安装配置派生）。
   *
   * <p>不可变，可安全全局共享；对应无自定义配置时的默认序列化/反序列化行为。
   */
  public static final JsonRuntimeConfig DEFAULT = from(JsonConfig.copyOf(null));

  /**
   * 从 {@link JsonConfig} 创建预计算运行时配置。
   *
   * <p>不可变快照：后续对源 JsonConfig 的修改不会反映到此实例中。 适用于 {@link JsonMapper} 实例化时一次性捕获配置状态。
   *
   * @param config 源配置对象（不可为 null） * @return 运行时配置快照
   * @throws IllegalArgumentException 当 config 为 null 时
   */
  public static JsonRuntimeConfig from(JsonConfig config) {
    if (config == null) {
      throw new IllegalArgumentException("JsonRuntimeConfig.from: config must not be null");
    }
    return new JsonRuntimeConfig(
        config.getNamingStrategy(),
        config.isWriteNulls(),
        config.isPrettyPrint(),
        config.getCircularReferenceStrategy().name(),
        config.isSerializeEnumUsingOrdinal(),
        config.getDateFormat(),
        config.isFailOnError(),
        config.getDefaultDateFormat(),
        config.getMaxJsonSize(),
        config.getMaxDepth(),
        config.getMaxGenericDepth(),
        config.isUseBigDecimal(),
        config.isWrapRootValue());
  }

  /**
   * 解析循环引用策略字符串为枚举值。
   *
   * @return 循环引用策略枚举，解析失败返回 {@link JsonConfig.CircularReferenceStrategy#REF}
   */
  public JsonConfig.CircularReferenceStrategy resolveCircularRefStrategy() {
    try {
      return JsonConfig.CircularReferenceStrategy.valueOf(circularRefStrategy);
    } catch (IllegalArgumentException | NullPointerException e) {
      return JsonConfig.CircularReferenceStrategy.REF;
    }
  }

  /**
   * 判断是否需要格式化输出（考虑 Mapper 级别和调用级别）。
   *
   * <p>当 Mapper 配置开启 prettyPrint 时为 true；调用级别可通过 {@code override} 参数强制控制。
   *
   * @param override 调用级别覆盖值（null 表示使用 Mapper 配置）
   * @return 是否格式化输出
   */
  public boolean resolvePrettyPrint(Boolean override) {
    return override != null ? override : prettyPrint;
  }

  /**
   * 判断是否需要输出 null 值（考虑 Mapper 级别和调用级别）。
   *
   * @param override 调用级别覆盖值（null 表示使用 Mapper 配置）
   * @return 是否输出 null 值
   */
  public boolean resolveWriteNulls(Boolean override) {
    return override != null ? override : writeNulls;
  }
}
