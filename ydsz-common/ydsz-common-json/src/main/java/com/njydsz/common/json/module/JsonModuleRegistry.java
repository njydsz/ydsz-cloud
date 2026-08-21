package com.njydsz.common.json.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.deserializer.JsonDeserializer;
import com.njydsz.common.json.serializer.JsonSerializer;
import com.njydsz.common.json.serializer.SerializerRegistry;

/**
 * YdszJson 模块注册中心
 *
 * <p>核心模块管理类，负责模块的注册、排序和查询。
 *
 * <p><b>设计特点：</b>
 *
 * <ul>
 *   <li>模块化架构 - 类似 Jackson Module，支持可插拔扩展
 *   <li>优先级排序 - 高优先级模块先注册，先注册的序列化器优先级更高
 *   <li>Spring 集成 - 支持自动发现和注入 Spring Bean 形式的模块
 *   <li>双重注册 - 分离序列化器和反序列化器注册
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * // 1. 手动注册模块
 * JsonModuleRegistry registry = JsonModuleRegistry.getInstance();
 * registry.registerModule(new UserModule());
 * registry.registerModule(new OrderModule());
 *
 * // 2. Spring Boot 环境自动注册
 * // 只需实现 JsonModule.SpringFactory 接口并添加 @Component 注解
 * // JsonSpringConfig 会自动发现并注册所有模块
 *
 * // 3. 获取序列化器
 * JsonSerializer serializer = registry.getSerializer(User.class);
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JsonModuleRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(JsonModuleRegistry.class);

  private static final AtomicReference<JsonModuleRegistry> INSTANCE = new AtomicReference<>();

  /**
   * 模块来源的序列化器类型集合（P1-6：单一事实源改造）。
   *
   * <p>模块注册的序列化器/反序列化器不再由本类独立维护，而是直接写入 {@link SerializerRegistry}（全局唯一注册中心）；本集合仅记录"由模块注册" 的类型，供
   * {@link #clear()} / {@link #reinitialize()} 精确清理模块来源的 注册项，避免误删用户直接注册（{@code
   * SerializerRegistry.register}）的序列化器。
   */
  private final Set<Class<?>> moduleSerializerTypes = ConcurrentHashMap.newKeySet();

  private final Set<Class<?>> moduleDeserializerTypes = ConcurrentHashMap.newKeySet();

  private final List<JsonModule> modules = Collections.synchronizedList(new ArrayList<>());

  private volatile boolean initialized = false;

  private JsonModuleRegistry() {}

  /**
   * 获取注册中心实例（单例）。
   *
   * <p>P2-9：首次创建时通过 {@link ServiceLoader} 自动发现并注册 {@code
   * META-INF/services/com.njydsz.common.json.module.JsonModule} 声明的模块，使非 Spring 环境（嵌入式引擎、命令行工具）具备与
   * Spring 环境同等的模块自动注册能力。SPI 模块类与 Spring Bean 为同一类时按类去重，不会重复注册。
   *
   * @return 注册中心实例
   */
  public static JsonModuleRegistry getInstance() {
    JsonModuleRegistry registry = instance.get();
    if (registry == null) {
      JsonModuleRegistry created = new JsonModuleRegistry();
      created.loadSpiModules();
      return instance.compareAndSet(null, created) ? created : instance.get();
    }
    return registry;
  }

  /**
   * 通过 ServiceLoader 加载 SPI 娡块（仅单例首次创建时执行一次）。
   *
   * <p>单个模块实例化失败仅告警跳过，不影响其他模块与引擎启动。
   */
  private void loadSpiModules() {
    try {
      ServiceLoader<JsonModule> loader = ServiceLoader.load(JsonModule.class);
      for (JsonModule module : loader) {
        try {
          registerModule(module);
          LOG.info(
              "Discovered YdszJson module via ServiceLoader SPI: {} (priority={})",
              module.getModuleName(),
              module.getPriority());
        } catch (Exception e) {
          LOG.error(
              "Failed to register SPI-discovered YdszJson module: {}",
              module.getClass().getName(),
              e);
        }
      }
    } catch (Exception e) {
      // ServiceLoader 基础设施异常（如非法配置文件）不阻断引擎初始化
      LOG.warn("Failed to scan YdszJson modules via ServiceLoader SPI", e);
    }
  }

  /**
   * 注册单个模块。
   *
   * <p>P2-9：按"实例相等或同类"去重——SPI 发现与 Spring Bean 注册 可能产生同一模块类的两个实例，重复注册仅告警跳过。
   *
   * @param module 要注册的模块
   */
  public void registerModule(JsonModule module) {
    if (module == null) {
      throw new IllegalArgumentException("Module cannot be null");
    }
    synchronized (this) {
      if (modules.contains(module)) {
        LOG.warn("Module {} already registered, skipping", module.getModuleName());
        return;
      }
      for (JsonModule existing : modules) {
        if (existing.getClass() == module.getClass()) {
          LOG.warn(
              "Module class {} already registered as {}, skipping duplicate",
              module.getClass().getName(),
              existing.getModuleName());
          return;
        }
      }
      modules.add(module);
      sortModulesByPriority();
      LOG.info(
          "Registered YdszJson module: {} (priority={})",
          module.getModuleName(),
          module.getPriority());
    }
  }

  /**
   * 批量注册模块
   *
   * @param modules 要注册的模块列表
   */
  public void registerModules(Collection<JsonModule> modules) {
    if (modules == null || modules.isEmpty()) {
      return;
    }
    for (JsonModule module : modules) {
      registerModule(module);
    }
  }

  /**
   * 注册 Spring 工厂模块（自动发现）
   *
   * <p>Spring Boot 环境下，自动发现所有实现 {@link JsonModule.SpringFactory} 的 Bean
   *
   * @param springFactories Spring 工厂模块实例
   */
  public void registerSpringFactories(Collection<JsonModule> springFactories) {
    if (springFactories == null || springFactories.isEmpty()) {
      return;
    }
    LOG.info("Discovering {} YdszJson Spring Factory modules", springFactories.size());
    registerModules(springFactories);
  }

  /**
   * 初始化所有模块。
   *
   * <p>按优先级从高到低依次调用模块的注册方法
   */
  public void initialize() {
    if (initialized) {
      LOG.debug("JsonModuleRegistry already initialized");
      return;
    }
    synchronized (this) {
      if (initialized) {
        return;
      }
      LOG.info("Initializing JsonModuleRegistry with {} modules", modules.size());
      for (JsonModule module : modules) {
        try {
          registerModuleSerializers(module);
          registerModuleDeserializers(module);
        } catch (Exception e) {
          LOG.error("Failed to initialize module: {}", module.getModuleName(), e);
        }
      }
      for (JsonModule module : modules) {
        if (module.needsCompleteRegistration()) {
          try {
            module.onRegisterComplete();
          } catch (Exception e) {
            LOG.error("Failed to complete registration for module: {}", module.getModuleName(), e);
          }
        }
      }
      initialized = true;
      LOG.info(
          "JsonModuleRegistry initialized successfully. Serializers: {}, Deserializers: {}",
          moduleSerializerTypes.size(),
          moduleDeserializerTypes.size());
    }
  }

  private void registerModuleSerializers(JsonModule module) {
    ModuleSerializerRegistry registry = new ModuleSerializerRegistry();
    module.setSerializers(registry);
    Map<Class<?>, JsonSerializer<?>> moduleSerializers = registry.getSerializers();
    SerializerRegistry global = SerializerRegistry.getInstance();
    for (Map.Entry<Class<?>, JsonSerializer<?>> entry : moduleSerializers.entrySet()) {
      Class<?> type = entry.getKey();
      JsonSerializer<?> serializer = entry.getValue();
      JsonSerializer<?> existing = global.registerIfAbsent(type, serializer);
      if (existing != null) {
        LOG.debug(
            "Serializer for type {} already exists (from module {}), skipping",
            type.getName(),
            module.getModuleName());
      } else {
        moduleSerializerTypes.add(type);
        LOG.debug(
            "Registered serializer for type {} from module {}",
            type.getName(),
            module.getModuleName());
      }
    }
  }

  private void registerModuleDeserializers(JsonModule module) {
    ModuleDeserializerRegistry registry = new ModuleDeserializerRegistry();
    module.setDeserializers(registry);
    Map<Class<?>, JsonDeserializer<?>> moduleDeserializers = registry.getDeserializers();
    SerializerRegistry global = SerializerRegistry.getInstance();
    for (Map.Entry<Class<?>, JsonDeserializer<?>> entry : moduleDeserializers.entrySet()) {
      Class<?> type = entry.getKey();
      JsonDeserializer<?> deserializer = entry.getValue();
      JsonDeserializer<?> existing = global.registerIfAbsent(type, deserializer);
      if (existing != null) {
        LOG.debug(
            "Deserializer for type {} already exists (from module {}), skipping",
            type.getName(),
            module.getModuleName());
      } else {
        moduleDeserializerTypes.add(type);
        LOG.debug(
            "Registered deserializer for type {} from module {}",
            type.getName(),
            module.getModuleName());
      }
    }
  }

  private void sortModulesByPriority() {
    modules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
  }

  /**
   * 获取序列化器
   *
   * <p>P1-6：委托全局唯一注册中心 {@link SerializerRegistry}，模块与直接注册的 序列化器共用同一存储，返回结果不再区分来源。
   *
   * @param type 目标类型
   * @param <T> 类型参数
   * @return 序列化器，如果未找到返回 null
   */
  public <T> JsonSerializer<T> getSerializer(Class<T> type) {
    return SerializerRegistry.getInstance().get(type);
  }

  /**
   * 获取反序列化器。
   *
   * <p>P1-6：委托全局唯一注册中心 {@link SerializerRegistry}，模块与直接注册的 反序列化器共用同一存储。
   *
   * @param type 目标类型
   * @param <T> 类型参数
   * @return 反序列化器，如果未找到返回 null
   */
  public <T> JsonDeserializer<T> getDeserializer(Class<T> type) {
    return SerializerRegistry.getInstance().getDeserializer(type);
  }

  /**
   * 检查是否有指定类型的序列化器。
   *
   * @param type 目标类型
   * @return 如果有返回 true
   */
  public boolean hasSerializer(Class<?> type) {
    return SerializerRegistry.getInstance().hasSerializer(type);
  }

  /**
   * 检查是否有指定类型的反序列化器
   *
   * @param type 目标类型
   * @return 如果有返回 true
   */
  public boolean hasDeserializer(Class<?> type) {
    return SerializerRegistry.getInstance().hasDeserializer(type);
  }

  /**
   * 移除模块
   *
   * @param module 要移除的模块
   * @return 如果成功移除返回 true
   */
  public boolean removeModule(JsonModule module) {
    if (module == null) {
      return false;
    }
    synchronized (this) {
      boolean removed = modules.remove(module);
      if (removed) {
        reinitialize();
      }
      return removed;
    }
  }

  /**
   * 清空所有模块和注册。
   *
   * <p>P1-6：仅清理模块来源的序列化器/反序列化器（依据 {@code moduleSerializerTypes} / {@code moduleDeserializerTypes}
   * 精确移除），用户直接注册的注册项不受影响。
   */
  public void clear() {
    synchronized (this) {
      modules.clear();
      clearModuleRegistrations();
      initialized = false;
      LOG.info("JsonModuleRegistry cleared");
    }
  }

  /**
   * 重新初始化。
   *
   * <p>清空模块来源的注册并重新注册所有模块。
   */
  public void reinitialize() {
    synchronized (this) {
      clearModuleRegistrations();
      initialized = false;
      initialize();
    }
  }

  /** 移除所有模块来源的序列化器/反序列化器注册项，并清空模块类型集合。 */
  private void clearModuleRegistrations() {
    SerializerRegistry global = SerializerRegistry.getInstance();
    global.unregisterAll(moduleSerializerTypes);
    global.unregisterAllDeserializers(moduleDeserializerTypes);
    moduleSerializerTypes.clear();
    moduleDeserializerTypes.clear();
  }

  /**
   * 获取已注册模块数量。
   *
   * @return 模块数量
   */
  public int getModuleCount() {
    return modules.size();
  }

  /**
   * 获取已注册序列化器数量。
   *
   * <p>P1-6：返回全局唯一注册中心的序列化器总数（含用户直接注册与模块注册）。
   *
   * @return 序列化器数量
   */
  public int getSerializerCount() {
    return SerializerRegistry.getInstance().getSerializerCount();
  }

  /**
   * 获取已注册反序列化器数量。
   *
   * <p>P1-6：返回全局唯一注册中心的反序列化器总数（含用户直接注册与模块注册）。
   *
   * @return 反序列化器数量
   */
  public int getDeserializerCount() {
    return SerializerRegistry.getInstance().getDeserializerCount();
  }

  /**
   * 获取所有已注册模块
   *
   * @return 只读模块列表
   */
  public List<JsonModule> getModules() {
    return Collections.unmodifiableList(new ArrayList<>(modules));
  }

  /**
   * 获取模块名称列表
   *
   * @return 模块名称列表
   */
  public List<String> getModuleNames() {
    return modules.stream()
        .map(module -> module != null ? module.getModuleName() : null)
        .collect(Collectors.toList());
  }
}
