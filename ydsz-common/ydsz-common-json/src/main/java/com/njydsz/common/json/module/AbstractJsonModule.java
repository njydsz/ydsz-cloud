package com.njydsz.common.json.module;

import com.njydsz.common.json.serializer.JsonSerializer;

/**
 * JsonModule SPI 注册基类（P3-G），简化业务模块自定义序列化器接入成本。
 *
 * <p>基类提供：
 *
 * <ul>
 *   <li>继承 {@link JsonModule} 接口（Spring 容器扫描 + {@link JsonModuleRegistrar} 自动注册）
 *   <li>{@link #registerSerializer(Class, JsonSerializer)} 便捷方法：供给 {@link #configure()} 实现调用
 * </ul>
 *
 * <p>子类示例（参考 ydsz-agent 的 {@code AgentJsonModule}）：
 *
 * <pre>{@code
 * @Component
 * public class AgentJsonModule extends AbstractJsonModule {
 *   &#64;Override
 *   public String getModuleName() {
 *     return "agent";
 *   }
 *
 *   &#64;Override
 *   protected void configure() {
 *     registerSerializer(ToolDefinition.class, new ToolDefinitionSerializer());
 *     registerSerializer(ToolCall.class, new ToolCallSerializer());
 *   }
 * }
 * }</pre>
 *
 * <p>注意：实现类必须覆盖 {@link #getModuleName()} 和标注 {@code @Component}（或其衍生注解）以被 Spring 容器扫描。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public abstract class AbstractJsonModule implements JsonModule {

  /** 模块内的序列化器注册表（由 {@link #setSerializers} 回调传入）。 */
  private ModuleSerializerRegistry serializerRegistry;

  /** 模块内的反序列化器注册表（由 {@link #setDeserializers} 回调传入）。 */
  private ModuleDeserializerRegistry deserializerRegistry;

  /**
   * 注册类型序列化器。
   *
   * <p>便捷等价于 {@code serializerRegistry.register(clazz, serializer)}，
   * 跳过 null 判断；在 {@link #configure()} 方法体中调用。
   *
   * @param clazz 目标类型
   * @param serializer 序列化器实现
   * @param <T> 目标类型泛型
   */
  protected final <T> void registerSerializer(Class<T> clazz, JsonSerializer<T> serializer) {
    if (serializerRegistry != null) {
      serializerRegistry.register(clazz, serializer);
    }
  }

  /**
   * 子类实现此方法，集中注册自定义序列化器。
   *
   * <p>方法体中调用 {@link #registerSerializer(Class, JsonSerializer)} 完成注册。 若需注册反序列化器，覆盖 {@link
   * #configureDeserializers()} 方法通过 {@link #deserializerRegistry} 字段注册。
   */
  protected abstract void configure();

  /**
   * 可选覆盖的反序列化器配置方法。
   *
   * <p>默认空实现；子类可通过 {@link #deserializerRegistry} 字段注册自定义反序列化器。
   */
  protected void configureDeserializers() {
    // 默认空实现，按需覆盖
  }

  @Override
  public final void setSerializers(ModuleSerializerRegistry registry) {
    this.serializerRegistry = registry;
    configure();
  }

  @Override
  public void setDeserializers(ModuleDeserializerRegistry registry) {
    this.deserializerRegistry = registry;
    configureDeserializers();
  }
}
