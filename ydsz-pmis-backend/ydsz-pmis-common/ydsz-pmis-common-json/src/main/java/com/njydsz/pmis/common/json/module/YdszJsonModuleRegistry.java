package com.njydsz.pmis.common.json.module;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.json.deserializer.JsonDeserializer;
import com.njydsz.pmis.common.json.serializer.JsonSerializer;

/**
 * Json 模块注册中心
 *
 * <p>核心模块管理类，负责模块的注册、排序和查询。
 *
 * <p><b>设计特点：</b>
 * <ul>
 *   <li>模块化架。- 类似 Jackson Module，支持可插拔扩展</li>
 *   <li>优先级排。- 高优先级模块先注册，先注册的序列化器优先级更。</li>
 *   <li>Spring 集成 - 支持自动发现和注入Spring Bean 形式的模。</li>
 *   <li>双重注册。- 分离序列化器和反序列化器注册。</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 1. 手动注册模块
 * JsonModuleRegistry registry = JsonModuleRegistry.getInstance();
 * registry.registerModule(new UserModule());
 * registry.registerModule(new OrderModule());
 *
 * // 2. Spring Boot 环境自动注册
 * // 只需实现 JsonModule.SpringFactory 接口并添。@Component 注解
 * // JsonSpringConfig 会自动发现并注册所有模。
 *
 * // 3. 获取序列化器
 * JsonSerializer serializer = registry.getSerializer(User.class);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 * @since 1.3.0
 */
public final class JsonModuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(JsonModuleRegistry.class);

    private static volatile JsonModuleRegistry instance;

    private final Map<Class<?>, JsonSerializer<?>> serializers = new ConcurrentHashMap<>();

    private final Map<Class<?>, JsonDeserializer<?>> deserializers = new ConcurrentHashMap<>();

    private final List<JsonModule> modules = Collections.synchronizedList(new ArrayList<>());

    private volatile boolean initialized = false;

    private JsonModuleRegistry() {
    }

    /**
     * 获取注册中心实例（单例）
     *
     * @return 注册中心实例
     */
    public static JsonModuleRegistry getInstance() {
        if (instance == null) {
            synchronized (JsonModuleRegistry.class) {
                if (instance == null) {
                    instance = new JsonModuleRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * 注册单个模块
     *
     * @param module 要注册的模块
     */
    public void registerModule(JsonModule module) {
        if (module == null) {
            throw new IllegalArgumentException("Module cannot be null");
        }
        synchronized (this) {
            if (modules.contains(module)) {
                log.warn("Module {} already registered, skipping", module.getModuleName());
                return;
            }
            modules.add(module);
            sortModulesByPriority();
            log.info("Registered Json module: {} (priority={})", module.getModuleName(), module.getPriority());
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
     * <p>Spring Boot 环境下，自动发现所有实与 {@link JsonModule.SpringFactory} 。Bean</p>
     *
     * @param springFactories Spring 工厂模块实例
     */
    public void registerSpringFactories(Collection<JsonModule> springFactories) {
        if (springFactories == null || springFactories.isEmpty()) {
            return;
        }
        log.info("Discovering {} Json Spring Factory modules", springFactories.size());
        registerModules(springFactories);
    }

    /**
     * 初始化所有模。
     *
     * <p>按优先级从高到低依次调用模块的注册方向/p>
     */
    public void initialize() {
        if (initialized) {
            log.debug("JsonModuleRegistry already initialized");
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            log.info("Initializing JsonModuleRegistry with {} modules", modules.size());
            for (JsonModule module : modules) {
                try {
                    registerModuleSerializers(module);
                    registerModuleDeserializers(module);
                } catch (Exception e) {
                    log.error("Failed to initialize module: {}", module.getModuleName(), e);
                }
            }
            for (JsonModule module : modules) {
                if (module.needsCompleteRegistration()) {
                    try {
                        module.onRegisterComplete();
                    } catch (Exception e) {
                        log.error("Failed to complete registration for module: {}", module.getModuleName(), e);
                    }
                }
            }
            initialized = true;
            log.info("JsonModuleRegistry initialized successfully. Serializers: {}, Deserializers: {}",
                    serializers.size(), deserializers.size());
        }
    }

    private void registerModuleSerializers(JsonModule module) {
        ModuleSerializerRegistry registry = new ModuleSerializerRegistry();
        module.setSerializers(registry);
        Map<Class<?>, JsonSerializer<?>> moduleSerializers = registry.getSerializers();
        for (Map.Entry<Class<?>, JsonSerializer<?>> entry : moduleSerializers.entrySet()) {
            Class<?> type = entry.getKey();
            JsonSerializer<?> serializer = entry.getValue();
            JsonSerializer<?> existing = serializers.putIfAbsent(type, serializer);
            if (existing != null) {
                log.debug("Serializer for type {} already exists (from module {}), skipping",
                        type.getName(), module.getModuleName());
            } else {
                log.debug("Registered serializer for type {} from module {}",
                        type.getName(), module.getModuleName());
            }
        }
    }

    private void registerModuleDeserializers(JsonModule module) {
        ModuleDeserializerRegistry registry = new ModuleDeserializerRegistry();
        module.setDeserializers(registry);
        Map<Class<?>, JsonDeserializer<?>> moduleDeserializers = registry.getDeserializers();
        for (Map.Entry<Class<?>, JsonDeserializer<?>> entry : moduleDeserializers.entrySet()) {
            Class<?> type = entry.getKey();
            JsonDeserializer<?> deserializer = entry.getValue();
            JsonDeserializer<?> existing = deserializers.putIfAbsent(type, deserializer);
            if (existing != null) {
                log.debug("Deserializer for type {} already exists (from module {}), skipping",
                        type.getName(), module.getModuleName());
            } else {
                log.debug("Registered deserializer for type {} from module {}",
                        type.getName(), module.getModuleName());
            }
        }
    }

    private void sortModulesByPriority() {
        modules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
    }

    /**
     * 获取序列化器
     *
     * @param type 目标类型
     * @param <T> 类型参数
     * @return 序列化器，如果未找到返回 null
     */
    
    public <T> JsonSerializer<T> getSerializer(Class<T> type) {
        JsonSerializer<?> serializer = serializers.get(type);
        return captureSerializer(serializer);
    }

    private static <T> JsonSerializer<T> captureSerializer(JsonSerializer<?> serializer) {
        return (JsonSerializer<T>) serializer;
    }

    /**
     * 获取反序列化。
     *
     * @param type 目标类型
     * @param <T> 类型参数
     * @return 反序列化器，如果未找到返回null
     */
    
    public <T> JsonDeserializer<T> getDeserializer(Class<T> type) {
        JsonDeserializer<?> deserializer = deserializers.get(type);
        return captureDeserializer(deserializer);
    }

    private static <T> JsonDeserializer<T> captureDeserializer(JsonDeserializer<?> deserializer) {
        return (JsonDeserializer<T>) deserializer;
    }

    /**
     * 检查是否有指定类型的序列化。
     *
     * @param type 目标类型
     * @return 如果有返。true
     */
    public boolean hasSerializer(Class<?> type) {
        return serializers.containsKey(type);
    }

    /**
     * 检查是否有指定类型的反序列化器
     *
     * @param type 目标类型
     * @return 如果有返。true
     */
    public boolean hasDeserializer(Class<?> type) {
        return deserializers.containsKey(type);
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
     * 清空所有模块和注册
     */
    public void clear() {
        synchronized (this) {
            modules.clear();
            serializers.clear();
            deserializers.clear();
            initialized = false;
            log.info("JsonModuleRegistry cleared");
        }
    }

    /**
     * 重新初始。
     *
     * <p>清空所有注册并重新注册所有模。/p>
     */
    public void reinitialize() {
        synchronized (this) {
            serializers.clear();
            deserializers.clear();
            initialized = false;
            initialize();
        }
    }

    /**
     * 获取已注册模块数。
     *
     * @return 模块数量
     */
    public int getModuleCount() {
        return modules.size();
    }

    /**
     * 获取已注册序列化器数。
     *
     * @return 序列化器数量
     */
    public int getSerializerCount() {
        return serializers.size();
    }

    /**
     * 获取已注册反序列化器数量
     *
     * @return 反序列化器数。
     */
    public int getDeserializerCount() {
        return deserializers.size();
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
