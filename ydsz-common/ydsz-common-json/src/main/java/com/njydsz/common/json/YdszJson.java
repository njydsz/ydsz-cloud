package com.njydsz.common.json;

import com.njydsz.common.json.deserializer.JsonDeserializer;
import com.njydsz.common.json.exception.JsonException;
import com.njydsz.common.json.internal.JsonConfig;
import com.njydsz.common.json.module.JsonModuleRegistry;
import com.njydsz.common.json.parser.JsonParserUtil;
import com.njydsz.common.json.provider.SerializationProvider;
import com.njydsz.common.json.reader.JSONReader;
import com.njydsz.common.json.serializer.JsonSerializer;
import com.njydsz.common.json.serializer.SerializerRegistry;
import com.njydsz.common.json.tree.*;
import com.njydsz.common.json.type.JsonType;
import com.njydsz.common.json.type.TypeFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
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
 * @author ydsz-team
 * @since 1.0.0
 */
public class YdszJson {

    private YdszJson() {
        throw new UnsupportedOperationException("YdszJson is a utility class and cannot be instantiated");
    }

    /**
     * 默认 JsonMapper 实例（持有全局配置）。
     *
     * <p>YdszJson 静态方法委托给此实例，确保：</p>
     * <ul>
     *   <li>配置变更（通过 {@link JsonConfig#install(JsonConfig)}）自动生效</li>
     *   <li>与实例化 {@code JsonMapper} API 共享相同代码路径（消除代码路径分叉）</li>
     * </ul>
     *
     * <p>volatile 保证 {@link #reloadDefaultMapper()} 后的可见性。</p>
     *
     * @since 1.1.0
     */
    private static volatile JsonMapper defaultMapper = new JsonMapper();

    /**
     * 重新构建默认 JsonMapper 实例。
     *
     * <p>当通过 {@link JsonConfig#install(JsonConfig)} 变更全局配置后调用此方法，
     * 使 YdszJson 静态方法立即使用新配置。
     * 正常情况下无需显式调用——{@link JsonConfig#install(JsonConfig)} 内部会自动触发。</p>
     *
     * @since 1.1.0
     */
    public static void reloadDefaultMapper() {
        defaultMapper = new JsonMapper(JsonConfig.getInstance());
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
        return defaultMapper.toJson(obj);
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
        return defaultMapper.toJson(obj, true);
    }

    /**
     * 美化一个已序列化的 JSON 字符串（格式化 + 缩进）。
     *
     * <p>对标 Jackson 的"二次格式化"场景：输入一个紧凑 JSON 字符串，输出带缩进的格式化字符串。
     * 内部通过 parseTree 后 toJson(pretty) 实现。</p>
     *
     * <p>如果输入为 null/空/非法 JSON，降级返回原始字符串（不抛异常），
     * 避免在日志、调试等场景引发二次异常。</p>
     *
     * @param json 紧凑 JSON 字符串
     * @return 格式化后的 JSON 字符串；解析失败时返回原始字符串
     * @since 1.2.0
     */
    public static String format(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        try {
            JsonNode tree = defaultMapper.readTree(json, JsonNode.class);
            return defaultMapper.toJson(tree, true);
        } catch (Exception e) {
            // 格式化失败（非法 JSON 等）时降级返回原始输入，避免二次异常
            return json;
        }
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
        return defaultMapper.toJsonBytes(obj);
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
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return defaultMapper.toObject(json, clazz);
    }

    /**
     * JSON 字符串转对象（支持 JsonType）
     *
     * @param json JSON 字符串
     * @param typeRef 类型引用
     * @param <T> 类型参数
     * @return 反序列化后的对象
     */
    public static <T> T fromJson(String json, JsonType<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return defaultMapper.toObject(json, typeRef.getType());
    }

    /**
     * JSON 字符串转对象（支持 {@link Type}，如泛型 {@code List<User>}）。
     *
     * <p>供持有 {@code java.lang.reflect.Type} 的框架适配场景使用（如 Feign Decoder 传入的
     * 泛型反序列化目标类型），避免调用方自行将 Type 包装为 {@link JsonType}。
     *
     * @param json JSON 字符串
     * @param type 目标类型（可为泛型，如 {@code List<User>}）
     * @return 反序列化后的对象
     */
    public static Object fromJson(String json, Type type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return defaultMapper.toObject(json, type);
    }

    // ==================== 自定义序列化器注册 ====================

    // 注意：历史版本提供 YdszJson.register(Class, JsonSerializer/JsonDeserializer) 静态注册方法，
    // 自 1.1.0 起废弃并由 SerializerRegistry / JsonModule 替代，本方法已移除。
    // 自定义序列化器/反序列化器请实现 JsonModule 接口并标注 @Component，由 SPI 自动发现注册。

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
     * 获取已注册的自定义序列化器（来自 {@code SerializerRegistry} 或 {@code JsonModule} 模块）。
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
     * 获取已注册的自定义反序列化器（来自 {@code SerializerRegistry} 或 {@code JsonModule} 模块）。
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

    // ==================== 类型转换 ====================

    /**
     * 将一个对象转换为指定类型的实例。
     *
     * <p>内部通过 JSON 序列化/反序列化实现，适用于 Map&lt;?, ?&gt; 到 POJO、对象深拷贝等场景。
     * 推荐替代 {@code YdszJson.fromJson(YdszJson.toJson(obj), TargetClass.class)} 这种链式调用。</p>
     *
     * <p><b>典型用法：</b></p>
     * <pre>{@code
     * // Map 转 POJO
     * User user = YdszJson.convertValue(userMap, User.class);
     *
     * // 泛型类型转换
     * List<Order> orders = YdszJson.convertValue(rawData,
     *         new JsonType<List<Order>>() {});
     *
     * // 对象深拷贝
     * User copy = YdszJson.convertValue(original, User.class);
     * }</pre>
     *
     * @param fromValue 源对象（可为 Map、POJO、基本类型等）
     * @param toValueType 目标类型
     * @param <T> 目标类型泛型
     * @return 转换后的对象实例
     * @throws JsonException 如果转换失败
     * @since 1.1.0
     */
    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        return defaultMapper.convertValue(fromValue, toValueType);
    }

    /**
     * 将一个对象转换为指定泛型类型的实例。
     *
     * <p>适用于带泛型参数的目标类型（如 {@code List<User>}、{@code Map<String, Order>}），
     * 推荐使用 {@link JsonType} 匿名内部类传递完整的泛型信息。</p>
     *
     * @param fromValue      源对象
     * @param toValueTypeRef 目标泛型类型引用
     * @param <T>            目标类型泛型
     * @return 转换后的对象实例
     * @throws JsonException 如果转换失败
     * @since 1.1.0
     */
    public static <T> T convertValue(Object fromValue, JsonType<T> toValueTypeRef) {
        return defaultMapper.convertValue(fromValue, toValueTypeRef);
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

    // ==================== 类型安全 Map / List 便捷 API ====================

    /**
     * JSON 字符串转 Map（类型安全，key 为 String，value 为 Object）。
     *
     * @param json JSON 字符串
     * @return Map 对象，json 为 null/blank 时返回 null
     */
    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        Map<String, Object> result = defaultMapper.toObject(json, Map.class);
        return toStringObjectMap(result);
    }

    /**
     * JSON 字符串转 List（指定元素类型）。
     *
     * @param json  JSON 字符串
     * @param clazz 元素类型
     * @param <T>   元素类型泛型
     * @return List 对象，json 为 null/blank 时返回 null
     */
    public static <T> List<T> parseArray(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        // 复用 TypeFactory 缓存的参数化类型，避免每次调用新建匿名 ParameterizedType
        Type type = TypeFactory.getInstance().constructCollectionType(List.class, clazz);
        List<T> result = defaultMapper.toObject(json, type);
        return result != null ? result : new ArrayList<>();
    }

    /**
     * JSON 字符串转 List（元素类型为 Object）。
     *
     * @param json JSON 字符串
     * @return List 对象，json 为 null/blank 时返回 null
     */
    public static List<Object> parseArray(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        List<Object> result = defaultMapper.toObject(json, List.class);
        return toObjectList(result);
    }

    /**
     * 从 JSON 字符串反序列化为指定 key/value 类型的 Map。
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
        // 复用 TypeFactory 缓存的参数化类型，避免每次调用新建匿名 ParameterizedType
        Type mapType = TypeFactory.getInstance().constructMapType(Map.class, keyClass, valueClass);
        Map<K, V> result = defaultMapper.toObject(json, mapType);
        if (result != null) {
            return result;
        }
        return new LinkedHashMap<>();
    }

    /**
     * 将 Map 中嵌套的 List/Map 值转换为类型安全的 Object 表示（与旧版 parseMap 行为一致）。
     */
    private static Map<String, Object> toStringObjectMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nestedMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> converted = toStringObjectMap((Map<String, Object>) nestedMap);
                entry.setValue(converted);
            } else if (value instanceof List<?> nestedList) {
                entry.setValue(toObjectList(nestedList));
            }
        }
        return map;
    }

    /**
     * 将 List 中嵌套的 List/Map 值转换为类型安全的 Object 表示（与旧版 parseArray 行为一致）。
     */
    private static List<Object> toObjectList(List<?> list) {
        if (list == null) {
            return null;
        }
        List<Object> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> nestedMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> converted = toStringObjectMap((Map<String, Object>) nestedMap);
                result.add(converted);
            } else if (item instanceof List<?> nestedList) {
                result.add(toObjectList(nestedList));
            } else {
                result.add(item);
            }
        }
        return result;
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
        return defaultMapper.toObject(bytes, clazz);
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
        return defaultMapper.toObject(bytes, typeRef.getType());
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

    // ==================== 预热 ====================

    /**
     * 预热指定类型的序列化/反序列化缓存。
     *
     * <p>在应用启动时调用，提前为指定类型构建字段元数据、序列化器与反序列化器缓存，
     * 避免首次请求时的延迟尖峰。预热失败不影响启动（内部吞掉异常）。</p>
     *
     * <p>注：原 ASM 字节码生成（AsmCodecCache）已随 {@code AsmCodecCache} 移除，
     * 现改为通过一次真实的序列化/反序列化触发 {@code SerializationProvider} /
     * {@code DeserializationProvider} 的元数据缓存构建，语义保持一致。</p>
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
                // 触发序列化侧缓存构建（字段元数据 / BeanSerializerInfo / BeanSerializer）
                Object instance = createInstanceForWarmup(clazz);
                if (instance != null) {
                    defaultMapper.toJson(instance);
                }
            } catch (Exception ignored) {
                // 预热失败不影响启动
            }
            try {
                // 触发反序列化侧缓存构建（BeanReader / Creator 解析）
                defaultMapper.toObject("{}", clazz);
            } catch (Exception ignored) {
                // 预热失败不影响启动
            }
        }
    }

    /**
     * 为预热创建实例（仅支持无参构造的可实例化类型，否则返回 null）。
     */
    private static Object createInstanceForWarmup(Class<?> clazz) {
        if (clazz.isInterface() || clazz.isEnum() || clazz.isArray()
                || clazz.isPrimitive()
                || Modifier.isAbstract(clazz.getModifiers())) {
            return null;
        }
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            return null;
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
        // 使用预计算运行时配置避免每次查询全局 JsonConfig
        long maxSize = defaultMapper.getRuntimeConfig().maxJsonSize();
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
        // 使用预计算运行时配置避免每次查询全局 JsonConfig
        long maxSize = defaultMapper.getRuntimeConfig().maxJsonSize();
        if (byteLength > maxSize) {
            throw new JsonException(
                    "JSON size exceeds limit: " + byteLength + " > " + maxSize);
        }
    }

    /**
     * 校验字符串是否为合法 JSON（解析成功即为合法）。
     *
     * <p>内部通过尝试解析实现，适合在校验场景使用，不抛出异常。</p>
     *
     * @param json 待校验字符串
     * @return true 如果字符串为合法 JSON
     * @since 1.1.0
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        try {
            JSONReader reader = new JSONReader(json);
            reader.skipValue();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== JSON Patch (RFC 6902) ====================

    /**
     * 解析 JSON Patch 操作列表。
     *
     * <p>JSON Patch 格式示例：</p>
     * <pre>
     * [
     *   {"op": "replace", "path": "/name", "value": "newName"},
     *   {"op": "remove", "path": "/age"},
     *   {"op": "add", "path": "/email", "value": "test@example.com"}
     * ]
     * </pre>
     *
     * @param patchJson Patch JSON 数组字符串
     * @return Patch 操作列表
     * @since 1.2.0
     * @see com.njydsz.common.json.tree.JsonPatch#parse(String)
     */
    public static List<com.njydsz.common.json.tree.JsonPatch.PatchOp> parsePatch(String patchJson) {
        return com.njydsz.common.json.tree.JsonPatch.parse(patchJson);
    }

    /**
     * 应用 JSON Patch (RFC 6902) 到目标对象，返回新对象。
     *
     * @param patchJson Patch JSON 数组字符串
     * @param target    目标对象（不会被修改）
     * @param clazz     目标类型
     * @param <T>       目标类型参数
     * @return Patch 后的新对象
     * @since 1.2.0
     */
    public static <T> T applyPatch(String patchJson, T target, Class<T> clazz) {
        return com.njydsz.common.json.tree.JsonPatch.apply(patchJson, target, clazz);
    }

    /**
     * 应用 JSON Merge Patch (RFC 7396) 到目标对象，返回新对象。
     *
     * <p>Merge Patch 更简单的语义：null 值表示删除字段，其他值替换或添加。</p>
     *
     * @param mergeJson Merge Patch JSON 字符串，如 {"name":"new","age":null}
     * @param target    目标对象（不会被修改）
     * @param clazz     目标类型
     * @param <T>       目标类型参数
     * @return Patch 后的新对象
     * @since 1.2.0
     */
    public static <T> T applyMergePatch(String mergeJson, T target, Class<T> clazz) {
        return com.njydsz.common.json.tree.JsonPatch.applyMerge(mergeJson, target, clazz);
    }
}
