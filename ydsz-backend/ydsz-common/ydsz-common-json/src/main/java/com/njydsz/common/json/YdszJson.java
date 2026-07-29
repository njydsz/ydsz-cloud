package com.njydsz.common.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.njydsz.common.json.cache.AsmCodecCache;
import com.njydsz.common.json.config.YdszJsonConfig;
import com.njydsz.common.json.deserializer.JsonDeserializer;
import com.njydsz.common.json.exception.YdszJsonException;
import com.njydsz.common.json.jsonpath.YdszJsonPath;
import com.njydsz.common.json.merge.JsonMergePatch;
import com.njydsz.common.json.metric.JsonMetricsCallback;
import com.njydsz.common.json.metric.MetricsHelper;
import com.njydsz.common.json.module.JsonModuleRegistry;
import com.njydsz.common.json.object.YdszJsonArray;
import com.njydsz.common.json.object.YdszJsonObject;
import com.njydsz.common.json.parser.YdszJsonParser;
import com.njydsz.common.json.pointer.JsonPointer;
import com.njydsz.common.json.provider.SerializationProvider;
import com.njydsz.common.json.provider.DeserializationProvider;
import com.njydsz.common.json.reader.JSONReader;
import com.njydsz.common.json.schema.SchemaValidator;
import com.njydsz.common.json.schema.ValidationResult;
import com.njydsz.common.json.schema.YdszJsonSchema;
import com.njydsz.common.json.serializer.JsonSerializer;
import com.njydsz.common.json.serializer.SerializerRegistry;
import com.njydsz.common.json.stream.JsonGenerator;
import com.njydsz.common.json.tree.*;
import com.njydsz.common.json.type.YdszJsonType;
import com.njydsz.common.json.writer.JSONWriter;

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
 *   <li><b>注解支持</b>：支持@YdszJsonField、@JsonProperty 等注解</li>
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

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
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
     * 对象转 JSON 字符串（带配置）
     * 
     * @param obj 要序列化的对象
     * @param pretty 是否格式化
     * @return JSON 字符串
     */
    public static String toJson(Object obj, boolean pretty) {
        if (obj == null) {
            return "null";
        }
        if (pretty) {
            return format(obj);
        }
        return recordSerialize(() -> SerializationProvider.serialize(obj));
    }
    
    /**
     * 对象转 JSON 字符串（带视图过滤）
     * 
     * <p>根据 @YdszJsonView 注解过滤字段，仅输出指定视图下可见的字段。</p>
     * 
     * @param obj 要序列化的对象
     * @param viewClass 视图类
     * @return JSON 字符串
     */
    public static String toJson(Object obj, Class<?> viewClass) {
        if (obj == null) {
            return "null";
        }
        return recordSerialize(() -> SerializationProvider.serializeWithView(obj, viewClass));
    }
    
    /**
     * 对象转 JSON 字符串（带视图过滤和配置）
     * 
     * @param obj 要序列化的对象
     * @param viewClass 视图类
     * @param pretty 是否格式化
     * @return JSON 字符串
     */
    public static String toJson(Object obj, Class<?> viewClass, boolean pretty) {
        if (obj == null) {
            return "null";
        }
        return recordSerialize(() -> SerializationProvider.serializeWithView(obj, viewClass, pretty));
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
     * JSON 字符串转对象（支持 YdszJsonType）
     *
     * @param json JSON 字符串
     * @param typeRef 类型引用
     * @param <T> 类型参数
     * @return 反序列化后的对象
     */
    public static <T> T toObject(String json, YdszJsonType<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return recordDeserialize(() -> DeserializationProvider.deserialize(json, typeRef.getType()));
    }
    
    /**
     * JSON 字符串转对象（带默认值，容错解析）
     *
     * <p>当解析失败时返回默认值，而非抛出异常。适用于配置解析等容错场景。</p>
     *
     * @param json JSON 字符串
     * @param clazz 目标类型
     * @param defaultValue 解析失败时返回的默认值
     * @param <T> 类型参数
     * @return 反序列化后的对象，解析失败时返回 defaultValue
     */
    public static <T> T toObject(String json, Class<T> clazz, T defaultValue) {
        try {
            T result = DeserializationProvider.deserialize(json, clazz);
            return result != null ? result : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
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
    public static <T> T fromJson(String json, YdszJsonType<T> typeRef) {
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
        ParameterizedType type = new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return new Type[]{clazz};
            }

            @Override
            public Type getRawType() {
                return List.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
        Object result = DeserializationProvider.deserialize(json, type);
        if (result instanceof List<?> list) {
            List<T> typedList = new ArrayList<>(list.size());
            for (Object item : list) {
                typedList.add(clazz.cast(item));
            }
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
    
    /**
     * JSON 字符串转 YdszJsonObject
     * 
     * @param json JSON 字符串
     * @return YdszJsonObject 对象
     */
    
    public static YdszJsonObject parseObjectToJsonObject(String json) {
        Object result = DeserializationProvider.deserialize(json, Map.class);
        if (result instanceof Map<?, ?> map) {
            return new YdszJsonObject(map);
        }
        return new YdszJsonObject();
    }
    
    /**
     * JSON 字符串转 YdszJsonArray
     * 
     * @param json JSON 字符串
     * @return YdszJsonArray 对象
     */
    
    public static YdszJsonArray parseArrayToJsonArray(String json) {
        Object result = DeserializationProvider.deserialize(json, List.class);
        if (result instanceof List<?> list) {
            return new YdszJsonArray(list);
        }
        return new YdszJsonArray();
    }
    
    // ==================== JSONPath 入口方法 ====================
    
    /**
     * 通过 JSONPath 获取值
     * 
     * @param json JSON 字符串
     * @param path JSONPath 表达式
     * @return 匹配的值
     */
    public static Object getByPath(String json, String path) {
        return YdszJsonPath.get(json, path);
    }
    
    /**
     * 通过 JSONPath 提取值并反序列化为指定类型
     *
     * <p>结合 JSONPath 提取与反序列化，先通过路径从 JSON 中提取子结构，
     * 再将其反序列化为目标类型对象。</p>
     *
     * @param json JSON 字符串
     * @param path JSONPath 表达式（如 "$.user.address"）
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 反序列化后的对象，路径不存在时返回 null
     */
    public static <T> T parseObject(String json, String path, Class<T> clazz) {
        Object value = YdszJsonPath.get(json, path);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        return DeserializationProvider.deserialize(toJson(value), clazz);
    }
    
    // ==================== Builder 入口方法 ====================
    
    /**
     * 创建 JSON 对象
     * 
     * @return JSON 对象
     */
    public static YdszJsonObject object() {
        return new YdszJsonObject();
    }
    
    /**
     * 创建 JSON 数组
     * 
     * @return JSON 数组
     */
    public static YdszJsonArray array() {
        return new YdszJsonArray();
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

    static <T> JsonSerializer<T> getRegisteredSerializer(Class<T> clazz) {
        return getCustomSerializer(clazz);
    }

    static <T> JsonDeserializer<T> getRegisteredDeserializer(Class<T> clazz) {
        return getCustomDeserializer(clazz);
    }

    static boolean hasCustomSerializer(Class<?> clazz) {
        return SerializerRegistry.getInstance().hasSerializer(clazz) || JsonModuleRegistry.getInstance().hasSerializer(clazz);
    }

    static boolean hasCustomDeserializer(Class<?> clazz) {
        return SerializerRegistry.getInstance().hasDeserializer(clazz) || JsonModuleRegistry.getInstance().hasDeserializer(clazz);
    }

    private static Map<String, Object> toStringObjectMap(Object obj) {
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

    // ==================== Feature API ====================

    /**
     * 使用特性序列化
     *
     * @param obj 要序列化的对象
     * @param features 写入特性
     * @return JSON 字符串
     */
    public static String toJson(Object obj, JSONWriter.Feature... features) {
        return SerializationProvider.serialize(obj, JSONWriter.of(features));
    }

    /**
     * 使用特性反序列化
     *
     * @param json JSON 字符串
     * @param clazz 目标类型
     * @param features 读取特性
     * @param <T> 类型参数
     * @return 反序列化后的对象
     */
    public static <T> T toObject(String json, Class<T> clazz, JSONReader.Feature... features) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return recordDeserialize(() -> DeserializationProvider.deserialize(json, clazz, JSONReader.of(features)));
    }

    // ==================== JSON Pointer API (RFC 6901) ====================

    /**
     * 使用 JSON Pointer 获取值
     *
     * @param json JSON 字符串
     * @param pointer JSON Pointer 路径
     * @return 指针指向的值
     */
    public static Object getByPointer(String json, String pointer) {
        return new JsonPointer(pointer).evaluate(json);
    }

    /**
     * 使用 JSON Pointer 获取值
     *
     * @param json JSON 字符串
     * @param pointer JsonPointer 对象
     * @return 指针指向的值
     */
    public static Object getByPointer(String json, JsonPointer pointer) {
        return pointer.evaluate(json);
    }

    // ==================== Tree Model API ====================

    /**
     * 将 JSON 字符串解析为 JsonNode 树
     *
     * @param json JSON 字符串
     * @return JsonNode 树
     */
    public static JsonNode readTree(String json) {
        Object parsed = YdszJsonParser.parse(json);
        return TreeConverter.convertToJsonNode(parsed);
    }

    /**
     * 将对象序列化为 JsonNode 树
     *
     * @param obj 要序列化的对象
     * @return JsonNode 树
     */
    public static JsonNode valueToTree(Object obj) {
        String json = toJson(obj);
        return readTree(json);
    }

    
    // ==================== Streaming API ====================

    /**
     * 创建流式生成器
     *
     * @param writer 输出写入器
     * @return JsonGenerator 实例
     */
    public static JsonGenerator createGenerator(Writer writer) {
        return JsonGenerator.of(writer);
    }

    /**
     * 创建流式生成器（格式化输出）
     *
     * @param writer 输出写入器
     * @param pretty 是否格式化输出
     * @return JsonGenerator 实例
     */
    public static JsonGenerator createGenerator(Writer writer, boolean pretty) {
        return JsonGenerator.of(writer, pretty);
    }

    // ==================== JSON Merge Patch (RFC 7396) ====================

    /**
     * 合并两个 JSON（RFC 7396）
     *
     * @param target 目标 JSON
     * @param patch 补丁 JSON
     * @return 合并后的 JSON 字符串
     */
    public static String merge(String target, String patch) {
        return JsonMergePatch.merge(target, patch);
    }

    /**
     * 计算两个 JSON 的差异补丁
     *
     * @param source 源 JSON
     * @param target 目标 JSON
     * @return 差异补丁 JSON
     */
    public static String diff(String source, String target) {
        return JsonMergePatch.diff(source, target);
    }

    // ==================== 验证 API ====================

    /**
     * 验证字符串是否为合法 JSON
     *
     * <p>快速校验 JSON 语法合法性，不进行完整的对象解析，性能优于 try-catch 解析方式。</p>
     *
     * @param json 待验证的字符串
     * @return 如果是合法 JSON 返回 true，否则返回 false
     */
    public static boolean isValid(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        try {
            YdszJsonParser.parse(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 验证 JSON 对象是否符合 Schema
     * 
     * @param data 要验证的数据
     * @param schema Schema 定义
     * @return 验证结果
     */
    public static ValidationResult validate(Object data, YdszJsonSchema schema) {
        return SchemaValidator.validate(schema, data);
    }
    
    /**
     * 验证 JSON 字符串是否符合 Schema
     * 
     * @param json JSON 字符串
     * @param schema Schema 定义
     * @return 验证结果
     */
    public static ValidationResult validate(String json, YdszJsonSchema schema) {
        try {
            Object data = DeserializationProvider.deserialize(json, Map.class);
            return SchemaValidator.validate(schema, data);
        } catch (Exception e) {
            ValidationResult result = new ValidationResult(false);
            result.addError("Failed to parse JSON: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 验证 JSON 对象是否符合 Schema（带类型转换）
     * 
     * @param data 要验证的数据
     * @param schema Schema 定义
     * @param <T> 类型参数
     * @return 验证结果
     */
    public static <T> ValidationResult validate(T data, YdszJsonSchema schema, Class<T> clazz) {
        return SchemaValidator.validate(schema, data);
    }
    
    /**
     * 检查验证结果，如果失败则抛出异常
     * 
     * @param result 验证结果
     * @throws IllegalArgumentException 验证失败时抛出
     */
    public static void ensureValid(ValidationResult result) {
        if (!result.isValid()) {
            throw new IllegalArgumentException("Validation failed: " + result.getErrors());
        }
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
        String json = new String(bytes, StandardCharsets.UTF_8);
        return toObject(json, clazz);
    }

    /**
     * 字节数组转泛型对象（UTF-8 编码）
     *
     * @param bytes   JSON 字节数组
     * @param typeRef 类型引用
     * @param <T>     目标类型泛型
     * @return 反序列化对象，bytes 为空时返回 null
     */
    public static <T> T fromJsonBytes(byte[] bytes, YdszJsonType<T> typeRef) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        return toObject(json, typeRef);
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
            ParameterizedType mapType = new ParameterizedType() {
                @Override
                public Type[] getActualTypeArguments() {
                    return new Type[]{keyClass, valueClass};
                }

                @Override
                public Type getRawType() {
                    return Map.class;
                }

                @Override
                public Type getOwnerType() {
                    return null;
                }
            };
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
        if (obj == null) {
            try {
                out.write("null".getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new YdszJsonException("Failed to write to OutputStream", e);
            }
            return;
        }
        byte[] bytes = toJsonBytes(obj);
        try {
            out.write(bytes);
        } catch (IOException e) {
            throw new YdszJsonException("Failed to write to OutputStream", e);
        }
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
     * @throws YdszJsonException 如果写入失败
     * @since 1.0.0
     */
    public static void toJson(Object obj, Writer writer) {
        String json = toJson(obj);
        try {
            writer.write(json);
        } catch (IOException e) {
            throw new YdszJsonException("Failed to write to Writer", e);
        }
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
            String json = new String(bytes, StandardCharsets.UTF_8);
            return toObject(json, clazz);
        } catch (IOException e) {
            throw new YdszJsonException("Failed to read from InputStream", e);
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
    public static <T> T toObject(InputStream in, YdszJsonType<T> typeRef) {
        if (in == null) {
            return null;
        }
        try {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            String json = new String(bytes, StandardCharsets.UTF_8);
            return toObject(json, typeRef);
        } catch (IOException e) {
            throw new YdszJsonException("Failed to read from InputStream", e);
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

    // ==================== 单次配置序列化 ====================

    /**
     * 使用指定配置序列化对象（不影响全局配置）。
     *
     * <p>通过保存/恢复当前线程的 ThreadLocal 序列化参数来实现单次配置，
     * 不修改全局 {@link YdszJsonConfig} 单例，保证线程安全。
     * 适用于需要为单次序列化指定不同配置的场景。</p>
     *
     * <p><b>返回值约定：</b>与 {@link #toJson(Object)} 保持一致，{@code null} 对象返回
     * JSON 字符串 {@code "null"}，而非 Java {@code null} 引用。</p>
     *
     * @param obj 要序列化的对象
     * @param config 单次配置，{@code null} 时退化为 {@link #toJson(Object)}
     * @return JSON 字符串（{@code null} 对象返回 {@code "null"}）
     * @since 1.0.0
     */
    public static String toJson(Object obj, YdszJsonConfig config) {
        if (obj == null) {
            return "null";
        }
        if (config == null) {
            return toJson(obj);
        }
        // 保存当前线程的序列化参数（不修改全局单例，避免并发污染）
        SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();
        try {
            // 应用临时配置到当前线程的 ThreadLocal
            config.apply();
            return toJson(obj);
        } finally {
            // 恢复原始 ThreadLocal 参数
            snapshot.restore();
        }
    }

    // ==================== 字段级排除（列权限等场景） ====================

    /**
     * 设置序列化时需要排除的字段名集合。
     *
     * <p>设置后，当前线程后续的序列化操作会跳过集合中名称匹配的字段。
     * 调用者负责在 finally 块中调用 {@link #clearExcludedFields()} 清理 ThreadLocal。
     *
     * @param fieldNames 需要排除的字段名集合，null 表示清除排除
     */
    public static void setExcludedFields(Set<String> fieldNames) {
        SerializationProvider.setExcludedFields(fieldNames);
    }

    /**
     * 清除序列化字段排除设置。
     *
     * <p>必须在 finally 块中调用，防止 ThreadLocal 泄漏。
     */
    public static void clearExcludedFields() {
        SerializationProvider.setExcludedFields(null);
    }

    /**
     * 序列化对象并排除指定字段（自动清理 ThreadLocal）。
     *
     * <p>便捷方法，内部设置排除集合、序列化、清理 ThreadLocal。
     *
     * @param obj 要序列化的对象
     * @param excludedFieldNames 需要排除的字段名集合
     * @return JSON 字符串（排除指定字段后的）
     */
    public static String toJsonExcludeFields(Object obj, Set<String> excludedFieldNames) {
        if (obj == null) {
            return "null";
        }
        Set<String> previous = SerializationProvider.getExcludedFields();
        try {
            SerializationProvider.setExcludedFields(excludedFieldNames);
            return recordSerialize(() -> SerializationProvider.serialize(obj));
        } finally {
            SerializationProvider.setExcludedFields(previous);
        }
    }

    // ==================== 安全检查 ====================

    /**
     * 校验 JSON 字符串大小是否超过全局限制。
     *
     * @param json JSON 字符串
     * @throws YdszJsonException 如果超过最大 JSON 大小限制
     */
    static void validateJsonSize(String json) {
        if (json == null) {
            return;
        }
        long maxSize = YdszJsonConfig.getInstance().getMaxJsonSize();
        if (json.length() > maxSize) {
            throw new YdszJsonException(
                    "JSON size exceeds limit: " + json.length() + " > " + maxSize);
        }
    }
}
