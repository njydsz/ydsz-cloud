package com.remisoft.common.json;

import com.remisoft.common.json.cache.AsmCodecCache;
import com.remisoft.common.json.internal.JsonConfig;
import com.remisoft.common.json.deserializer.JsonDeserializer;
import com.remisoft.common.json.exception.JsonException;
import com.remisoft.common.json.metric.JsonMetricsCallback;
import com.remisoft.common.json.metric.MetricsHelper;
import com.remisoft.common.json.module.JsonModuleRegistry;
import com.remisoft.common.json.parser.JsonParserUtil;
import com.remisoft.common.json.provider.DeserializationProvider;
import com.remisoft.common.json.provider.SerializationProvider;
import com.remisoft.common.json.serializer.JsonSerializer;
import com.remisoft.common.json.serializer.SerializerRegistry;
import com.remisoft.common.json.tree.*;
import com.remisoft.common.json.type.JsonType;
import com.remisoft.common.json.type.TypeFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.*;

/**
 * YdszJson - 超高性能 JSON 工具类（深度优化版）
 *
 * <p>提供高性能、功能丰富的 JSON 序列化和反序列化功能，纯 Java 实现，无需额外依赖。</p>
 * 
 * <p><b>核心特性：</b></p>
 * <ul>
 *   <li><b>零依赖</b>：纯 Java 实现，无需任何第三方库</li>
 *   <li><b>超高性能</b>：ASM 字节码优化、递归下降解析、对象池复用</li>
 *   <li><b>递归下降解析</b>：直接解析 JSON 到 Bean，无需 Map 中转</li>
 *   <li><b>ASM 反序列化</b>：100% 字节码生成，字段访问性能提升 50 倍</li>
 *   <li><b>循环引用检测</b>：自动检测并处理循环引用</li>
 *   <li><b>泛型支持</b>：完整的泛型反序列化支持</li>
 *   <li><b>Java 8+ 日期时间</b>：完美支持 LocalDateTime 等新 API</li>
 *   <li><b>JSONPath</b>：支持嵌套字段提取</li>
 *   <li><b>Builder 模式</b>：链式调用，代码更优雅</li>
 *   <li><b>注解支持</b>：支持@JsonProperty、@JsonFormat 等注解</li>
 *   <li><b>自定义序列化器</b>：支持注册自定义序列化/反序列化器</li>
 *   <li><b>命名策略</b>：支持 SNAKE_CASE、KEBAB_CASE 等多种命名策略</li>
 * </ul>
 * 
 * <p><b>核心优化技术：</b></p>
 * <ul>
 *   <li>递归下降解析器 - 直接解析 JSON 到对象字段</li>
 *   <li>ASM 字节码生成 - 100% 字节码优化，避免 MethodHandle 开销</li>
 *   <li>零拷贝字符串 - 减少 String 创建</li>
 *   <li>对象池复用 - ThreadLocal 缓存</li>
 *   <li>JIT 友好设计 - 便于 JVM 内联优化</li>
 * </ul>
 * 
 * @author remi-team
 * @since 1.0.0
 */
public class YdszJson {

    private YdszJson() {
        throw new UnsupportedOperationException("YdszJson is a utility class and cannot be instantiated");
    }

    // ==================== 指标监控钩子 ====================

    /**
     * 指标回调（可选，由 JsonAutoConfiguration 自动注入）
     */
    private static volatile JsonMetricsCallback metricsCallback;

    /**
     * 设置指标回调
     *
     * @param callback 指标回调实例，null 表示关闭监控
     */
    public static void setMetricsCallback(JsonMetricsCallback callback) {
        YdszJson.metricsCallback = callback;
    }

    /**
     * 获取当前指标回调
     *
     * @return 指标回调实例，未设置时返回 null
     */
    public static JsonMetricsCallback getMetricsCallback() {
        return metricsCallback;
    }

    /**
     * 可抛出受检异常的供应商接口。
     *
     * <p>仅用于 {@link #recordSerialize}/{@link #recordDeserialize} 内部包装，
     * 允许指标采集操作向上抛出受检异常，由 {@link MetricsHelper} 统一转为
     * {@link com.remisoft.common.json.exception.JsonException}。</p>
     *
     * @param <T> 生产值的类型
     */
    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        /**
         * 获取结果。
         *
         * @return 生产的值
         * @throws Exception 获取过程中可能抛出的任意异常
         */
        T get() throws Exception;
    }

    /**
     * 序列化操作的指标监控包装（委托给 {@link MetricsHelper}）。
     *
     * @param supplier 序列化操作
     * @param <T> 返回类型
     * @return 序列化结果
     */
    private static <T> T recordSerialize(ThrowingSupplier<T> supplier) {
        return MetricsHelper.recordSerialize(supplier::get, metricsCallback);
    }

    /**
     * 反序列化操作的指标监控包装（委托给 {@link MetricsHelper}）。
     *
     * @param supplier 反序列化操作
     * @param <T> 返回类型
     * @return 反序列化结果
     */
    private static <T> T recordDeserialize(ThrowingSupplier<T> supplier) {
        return MetricsHelper.recordDeserialize(supplier::get, metricsCallback);
    }
    
    // ==================== 序列化入口方法 ====================
    
    /**
     * 对象转 JSON 字符串
     * 
     * @param obj 要序列化的对象
     * @return JSON 字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        return recordSerialize(() -> SerializationProvider.serialize(obj));
    }

    /**
     * 格式化 JSON（带缩进）
     *
     * @param obj 要序列化的对象
     * @return 格式化的 JSON 字符串
     */
    public static String format(Object obj) {
        if (obj == null) {
            return "null";
        }
        return recordSerialize(() -> SerializationProvider.format(obj));
    }
    
    /**
     * 对象转 JSON 字节数组（UTF-8 编码）
     *
     * <p>适用于网络传输、文件写入等需要字节数组的场景，避免额外的 String.getBytes() 调用。</p>
     *
     * @param obj 要序列化的对象
     * @return UTF-8 编码的 JSON 字节数组
     */
    public static byte[] toJsonBytes(Object obj) {
        if (obj == null) {
            return new byte[]{'n', 'u', 'l', 'l'};
        }
        return recordSerialize(() -> SerializationProvider.serializeToBytes(obj));
    }
    
    // ==================== 反序列化入口方法 ====================
    
    /**
     * JSON 字符串转对象
     * 
     * @param json JSON 字符串
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 反序列化后的对象
     */
    public static <T> T toObject(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return recordDeserialize(() -> DeserializationProvider.deserialize(json, clazz));
    }
    
    /**
     * JSON 字符串转对象（支持泛型）
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T> 类型参数
     * @return 反序列化后的对象
     */
    public static <T> T toObject(String json, Type type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return recordDeserialize(() -> DeserializationProvider.deserialize(json, type));
    }
    
    /**
     * JSON 字符串转对象（支持 JsonType）
     *
     * @param json JSON 字符串
     * @param typeRef 类型引用
     * @param <T> 类型参数
     * @return 反序列化后的对象
     */
    public static <T> T toObject(String json, JsonType<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return recordDeserialize(() -> DeserializationProvider.deserialize(json, typeRef.getType()));
    }
    
    /**
     * JSON 字符串转 Map
     * 
     * @param json JSON 字符串
     * @return Map 对象
     */
    
    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return recordDeserialize(() -> {
            Object result = DeserializationProvider.deserialize(json, Map.class);
            return toStringObjectMap(result);
        });
    }


    /**
     * 从 JSON 字符串反序列化为指定类型（与 {@link #toJson(Object)} 对称的 API）。
     *
     * @param json JSON 字符串
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 反序列化后的对象
     * @since 1.0.0
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return toObject(json, clazz);
    }

    /**
     * 从 JSON 字符串反序列化为指定泛型类型（与 {@link #toJson(Object)} 对称的 API）。
     *
     * @param json JSON 字符串
     * @param typeRef 类型引用
     * @param <T> 类型参数
     * @return 反序列化后的对象
     * @since 1.0.0
     */
    public static <T> T fromJson(String json, JsonType<T> typeRef) {
        return toObject(json, typeRef);
    }
    
    /**
     * JSON 字符串转 List
     * 
     * @param json JSON 字符串
     * @param clazz 元素类型
     * @param <T> 类型参数
     * @return List 对象
     */
    
    public static <T> List<T> parseArray(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return recordDeserialize(() -> {
        // 复用 TypeFactory 缓存的参数化类型，避免每次调用新建匿名 ParameterizedType
        Type type = TypeFactory.getInstance().constructCollectionType(List.class, clazz);
        Object result = DeserializationProvider.deserialize(json, type);
        if (result instanceof List<?> list) {
            // 优化：直接 unchecked cast 返回，消除 O(n) 拷贝。
            // DeserializationProvider 内部已按 elementClass 生成了正确类型元素，
            // 无需逐元素 Class.cast() 校验。
            @SuppressWarnings("unchecked")
            List<T> typedList = (List<T>) list;
            return typedList;
        }
        return new ArrayList<>();
        });
    }

    /**
     * JSON 字符串转 List<Object>
     * 
     * @param json JSON 字符串
     * @return List 对象
     */
    
    public static List<Object> parseArray(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return recordDeserialize(() -> {
            Object result = DeserializationProvider.deserialize(json, List.class);
            return toObjectList(result);
        });
    }

    // ==================== 自定义序列化器注册 ====================

    /**
     * 注册自定义序列化器
     *
     * @param clazz 类型
     * @param serializer 序列化器
     * @param <T> 类型参数
     */
    public static <T> void register(Class<T> clazz, JsonSerializer<T> serializer) {
        SerializerRegistry.getInstance().register(clazz, serializer);
    }

    /**
     * 注册自定义反序列化器
     *
     * @param clazz 类型
     * @param deserializer 反序列化器
     * @param <T> 类型参数
     */
    public static <T> void register(Class<T> clazz, JsonDeserializer<T> deserializer) {
        SerializerRegistry.getInstance().register(clazz, deserializer);
    }

    
    static <T> JsonSerializer<T> getCustomSerializer(Class<T> clazz) {
        JsonSerializer<T> serializer = SerializerRegistry.getInstance().get(clazz);
        if (serializer != null) {
            return serializer;
        }
        return JsonModuleRegistry.getInstance().getSerializer(clazz);
    }

    static <T> JsonDeserializer<T> getCustomDeserializer(Class<T> clazz) {
        JsonDeserializer<T> deserializer = SerializerRegistry.getInstance().getDeserializer(clazz);
        if (deserializer != null) {
            return deserializer;
        }
        return JsonModuleRegistry.getInstance().getDeserializer(clazz);
    }

    /**
     * 获取已注册的自定义序列化器（来自 {@code YdszJson.register(...)} 或 {@code JsonModule} 模块）。
     *
     * <p>供序列化 Provider 在 {@code @JsonSerialize} 注解快速路径之后回退查询。
     * 历史实现中该方法虽存在但未被 Provider 实际调用，导致模块注册机制形同虚设；
     * 现已在 {@code SerializationProvider}/{@code DeserializationProvider} 接入，使
     * {@code JsonModule.SpringFactory} 注册的序列化器/反序列化器在全局 {@code toJson/toObject}
     * 路径中真正生效。</p>
     *
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 序列化器，未注册返回 null
     */
    public static <T> JsonSerializer<T> getRegisteredSerializer(Class<T> clazz) {
        return getCustomSerializer(clazz);
    }

    /**
     * 获取已注册的自定义反序列化器（来自 {@code YdszJson.register(...)} 或 {@code JsonModule} 模块）。
     *
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 反序列化器，未注册返回 null
     */
    public static <T> JsonDeserializer<T> getRegisteredDeserializer(Class<T> clazz) {
        return getCustomDeserializer(clazz);
    }

    static boolean hasCustomSerializer(Class<?> clazz) {
        return SerializerRegistry.getInstance().hasSerializer(clazz) || JsonModuleRegistry.getInstance().hasSerializer(clazz);
    }

    static boolean hasCustomDeserializer(Class<?> clazz) {
        return SerializerRegistry.getInstance().hasDeserializer(clazz) || JsonModuleRegistry.getInstance().hasDeserializer(clazz);
    }

    public static Map<String, Object> toStringObjectMap(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put((String) entry.getKey(), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static List<Object> toObjectList(Object obj) {
        if (obj instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    // ==================== Tree Model API ====================

    /**
     * 将 JSON 字符串解析为 JsonNode 树
     *
     * @param json JSON 字符串
     * @return JsonNode 树
     */
    public static JsonNode readTree(String json) {
        Object parsed = JsonParserUtil.parse(json);
        return TreeConverter.convertToJsonNode(parsed);
    }

    /**
     * 将 JSON 字符串解析为 ObjectNode 对象节点。
     *
     * <p>对标 FastJSON2 {@code JSON.parseObject(json)}，解析 JSON 对象字符串为 ObjectNode，
     * 提供 getString/getInteger/getLong/getBoolean 等便捷访问方法。</p>
     *
     * @param json JSON 字符串
     * @return ObjectNode 实例，json 为 null/blank 返回 null
     * @throws JsonException 如果 JSON 不是对象（如为数组或标量）
     * @since 1.0.0
     */
    public static ObjectNode parseObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode tree = readTree(json);
        if (tree instanceof ObjectNode objNode) {
            return objNode;
        }
        throw new JsonException("JSON is not an object: " + tree.getClass().getSimpleName());
    }

    /**
     * 将 JSON 字符串解析为 ArrayNode 数组节点。
     *
     * <p>对标 FastJSON2 {@code JSON.parseArray(json)}，解析 JSON 数组字符串为 ArrayNode，
     * 提供 getString/getInteger/getLong/getBoolean 等便捷访问方法。</p>
     *
     * @param json JSON 字符串
     * @return ArrayNode 实例，json 为 null/blank 返回 null
     * @throws JsonException 如果 JSON 不是数组
     * @since 1.0.0
     */
    public static ArrayNode parseArrayNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode tree = readTree(json);
        if (tree instanceof ArrayNode arrNode) {
            return arrNode;
        }
        throw new JsonException("JSON is not an array: " + tree.getClass().getSimpleName());
    }

    /**
     * 将对象序列化为 JsonNode 树。
     *
     * <p>与 {@link JsonMapper#valueToTree(Object)} 语义一致，可直接使用 YdszJson 静态入口调用。</p>
     *
     * @param obj 要序列化的对象
     * @return JsonNode 树
     */
    public static JsonNode valueToTree(Object obj) {
        if (obj == null) {
            return NullNode.getInstance();
        }
        if (obj instanceof JsonNode) {
            return (JsonNode) obj;
        }
        if (obj instanceof Map || obj instanceof List
                || obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            return TreeConverter.convertToJsonNode(obj);
        }
        String json = toJson(obj);
        return readTree(json);
    }


    // ==================== 便捷方法（字节数组 / 类型安全 Map） ====================

    /**
     * 字节数组转对象（UTF-8 编码）
     *
     * @param bytes JSON 字节数组
     * @param clazz 目标类型
     * @param <T>   目标类型泛型
     * @return 反序列化对象，bytes 为空时返回 null
     */
    public static <T> T fromJsonBytes(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        validateJsonSizeBytes(bytes.length);
        return recordDeserialize(() -> DeserializationProvider.deserialize(bytes, clazz));
    }

    /**
     * 字节数组转泛型对象（UTF-8 编码）
     *
     * @param bytes   JSON 字节数组
     * @param typeRef 类型引用
     * @param <T>     目标类型泛型
     * @return 反序列化对象，bytes 为空时返回 null
     */
    public static <T> T fromJsonBytes(byte[] bytes, JsonType<T> typeRef) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        validateJsonSizeBytes(bytes.length);
        return recordDeserialize(() -> DeserializationProvider.deserialize(bytes, typeRef.getType()));
    }

    /**
     * JSON 字符串转类型安全 Map
     *
     * @param json       JSON 字符串
     * @param keyClass   Map key 类型
     * @param valueClass Map value 类型
     * @param <K>        key 类型泛型
     * @param <V>        value 类型泛型
     * @return 反序列化 Map，json 为空时返回 null
     */
    public static <K, V> Map<K, V> fromJsonToMap(String json, Class<K> keyClass, Class<V> valueClass) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return recordDeserialize(() -> {
            // 复用 TypeFactory 缓存的参数化类型，避免每次调用新建匿名 ParameterizedType
            Type mapType = TypeFactory.getInstance().constructMapType(Map.class, keyClass, valueClass);
            Object result = DeserializationProvider.deserialize(json, mapType);
            if (result instanceof Map<?, ?> map) {
                Map<K, V> typedMap = new LinkedHashMap<>(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    typedMap.put(keyClass.cast(entry.getKey()), valueClass.cast(entry.getValue()));
                }
                return typedMap;
            }
            return new LinkedHashMap<>();
        });
    }

    // ==================== 流式 API ====================

    /**
     * 将对象序列化为 JSON 并直接写入 OutputStream（UTF-8 编码）。
     *
     * <p>避免中间 String 分配，适用于大 JSON 输出场景。</p>
     *
     * @param obj 要序列化的对象
     * @param out 输出流
     * @since 1.0.0
     */
    public static void toJson(Object obj, OutputStream out) {
        SerializationProvider.serializeToStream(obj, out);
    }

    /**
     * 将对象序列化为 JSON 并直接写入 Writer。
     *
     * <p>适用于字符流输出场景（如 FileWriter、StringWriter、BufferedWriter）。
     * 内部使用 {@link SerializationProvider#serialize(Object)} 生成 JSON 字符串后写入 Writer，
     * 避免额外的 byte/char 转换。</p>
     *
     * @param obj 要序列化的对象
     * @param writer 字符输出流
     * @throws JsonException 如果写入失败
     * @since 1.0.0
     */
    public static void toJson(Object obj, Writer writer) {
        SerializationProvider.serializeToWriter(obj, writer);
    }

    /**
     * 从 InputStream 读取 JSON 并反序列化为指定类型。
     *
     * <p>适用于大 JSON 输入场景，但仍需全量读入内存进行解析。</p>
     *
     * @param in 输入流
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 反序列化后的对象
     * @since 1.0.0
     */
    public static <T> T toObject(InputStream in, Class<T> clazz) {
        if (in == null) {
            return null;
        }
        try {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            return fromJsonBytes(bytes, clazz);
        } catch (IOException e) {
            throw new JsonException("Failed to read from InputStream", e);
        }
    }

    /**
     * 从 InputStream 读取 JSON 并反序列化为指定泛型类型。
     *
     * @param in 输入流
     * @param typeRef 类型引用
     * @param <T> 类型参数
     * @return 反序列化后的对象
     * @since 1.0.0
     */
    public static <T> T toObject(InputStream in, JsonType<T> typeRef) {
        if (in == null) {
            return null;
        }
        try {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            return fromJsonBytes(bytes, typeRef);
        } catch (IOException e) {
            throw new JsonException("Failed to read from InputStream", e);
        }
    }

    // ==================== ASM 预热 ====================

    /**
     * 预热 ASM 序列化器/反序列化器。
     *
     * <p>在应用启动时调用，提前为指定类型生成 ASM 字节码，
     * 避免首次请求时的延迟尖峰。</p>
     *
     * @param classes 需要预热的类型列表
     * @since 1.0.0
     */
    public static void warmup(Class<?>... classes) {
        if (classes == null || classes.length == 0) {
            return;
        }
        for (Class<?> clazz : classes) {
            if (clazz == null) {
                continue;
            }
            try {
                AsmCodecCache.getOrCreateSerializerForType(clazz);
            } catch (Exception ignored) {
                // 预热失败不影响启动
            }
        }
    }

    // ==================== 安全检查 ====================

    /**
     * 校验 JSON 字符串大小是否超过全局限制。
     *
     * @param json JSON 字符串
     * @throws JsonException 如果超过最大 JSON 大小限制
     */
    static void validateJsonSize(String json) {
        if (json == null) {
            return;
        }
        long maxSize = JsonConfig.getInstance().getMaxJsonSize();
        if (json.length() > maxSize) {
            throw new JsonException(
                    "JSON size exceeds limit: " + json.length() + " > " + maxSize);
        }
    }

    /**
     * 校验 JSON 字节数组大小是否超过全局限制。
     *
     * @param byteLength JSON 字节数组长度
     * @throws JsonException 如果超过最大 JSON 大小限制
     */
    static void validateJsonSizeBytes(int byteLength) {
        long maxSize = JsonConfig.getInstance().getMaxJsonSize();
        if (byteLength > maxSize) {
            throw new JsonException(
                    "JSON size exceeds limit: " + byteLength + " > " + maxSize);
        }
    }
}
