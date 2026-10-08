package com.njydsz.common.cache.aspect;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link L2ValueSerializer} 的默认实现 — 基于回调的轻量级编解码器。
 *
 * <p>参考实现定位：覆盖 String / 基本类型 / toString 友好的场景（如枚举、ID 等）。对于 POJO，
 * 业务方应传入自定义序列化函数（推荐基于 Jackson / Fastjson2），或替换为完整实现。
 *
 * <p><b>默认策略（不设定制函数时）：</b>
 *
 * <ul>
 *   <li>{@link #serialize(Object)} — 直接以 {@link String#valueOf(Object)} 编码</li>
 *   <li>{@link #deserialize(String, Class)} — 仅当目标类型为 {@link String} 时返回原始字符串，否则返回
 *       {@code null}（切面视同 L2 未命中，回退到目标方法执行）</li>
 * </ul>
 *
 * <p><b>定制示例：</b>
 *
 * <pre>{@code
 * @Bean
 * public L2ValueSerializer l2ValueSerializer(ObjectMapper om) {
 *     return DefaultL2ValueSerializer.builder()
 *         .serialize(value -> om.writeValueAsString(value))
 *         .deserialize((raw, type) -> om.readValue(raw, om.constructType(type)))
 *         .build();
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see L2ValueSerializer
 */
public final class DefaultL2ValueSerializer implements L2ValueSerializer {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultL2ValueSerializer.class);

  private final Function<Object, String> serializeFunc;
  private final BiDeserializeFunc deserializeFunc;

  private DefaultL2ValueSerializer(Function<Object, String> serializeFunc, BiDeserializeFunc deserializeFunc) {
    this.serializeFunc = serializeFunc;
    this.deserializeFunc = deserializeFunc;
  }

  @Override
  public String serialize(Object value) {
    if (value == null) {
      return null;
    }
    if (serializeFunc != null) {
      return serializeFunc.apply(value);
    }
    return String.valueOf(value);
  }

  @Override
  public Object deserialize(String raw, Class<?> targetType) {
    if (raw == null) {
      return null;
    }
    if (deserializeFunc != null) {
      try {
        return deserializeFunc.apply(raw, targetType);
      } catch (Exception e) {
        LOG.warn("L2 反序列化失败, targetType={}, error={}", targetType.getName(), e.getMessage());
        return null;
      }
    }
    // 默认：仅 String 类型可安全还原
    if (targetType == String.class) {
      return raw;
    }
    return null;
  }

  /**
   * 创建基于默认策略的编解码器实例。
   *
   * @return 默认 {@link L2ValueSerializer} 实例
   */
  public static DefaultL2ValueSerializer create() {
    return new DefaultL2ValueSerializer(null, null);
  }

  /**
   * 创建构建器，允许定制序列化/反序列化函数。
   *
   * @return 新的构建器
   */
  public static Builder builder() {
    return new Builder();
  }

  /** 双参数反序列化函数式接口（绕过 checked exception 繁琐声明） */
  @FunctionalInterface
  public interface BiDeserializeFunc {
    /**
     * 从原始字符串反序列化。
     *
     * @param raw       原始字符串
     * @param targetType 目标类型
     * @return 反序列化结果
     * @throws Exception 反序列化过程中的异常
     */
    Object apply(String raw, Class<?> targetType) throws Exception;
  }

  /** {@link DefaultL2ValueSerializer} 构建器 */
  public static final class Builder {
    private Function<Object, String> serializeFunc;
    private BiDeserializeFunc deserializeFunc;

    private Builder() {}

    /**
     * 设置序列化函数。
     *
     * @param serializeFunc 对象 → 字符串
     * @return 构建器
     */
    public Builder serialize(Function<Object, String> serializeFunc) {
      this.serializeFunc = serializeFunc;
      return this;
    }

    /**
     * 设置反序列化函数。
     *
     * @param deserializeFunc (字符串, 目标类型) → 对象
     * @return 构建器
     */
    public Builder deserialize(BiDeserializeFunc deserializeFunc) {
      this.deserializeFunc = deserializeFunc;
      return this;
    }

    /**
     * 构建 {@link DefaultL2ValueSerializer} 实例。
     *
     * @return 编解码器实例，不会为 {@code null}
     */
    public DefaultL2ValueSerializer build() {
      return new DefaultL2ValueSerializer(serializeFunc, deserializeFunc);
    }
  }
}
