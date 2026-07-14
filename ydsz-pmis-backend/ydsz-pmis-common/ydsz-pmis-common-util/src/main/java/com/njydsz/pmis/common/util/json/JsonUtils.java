package com.njydsz.pmis.common.util.json;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.njydsz.pmis.common.json.YdszJson;
import com.njydsz.pmis.common.json.config.YdszJsonConfig;
import com.njydsz.pmis.common.json.tree.JsonNode;

/**
 * 统一 JSON 序列化工具类（基于 YdszJson，保持 Jackson 兼容性）
 *
 * <p>提供对象与 JSON 字符串/字节数组之间的双向转换。
 * 内部委托 {@link YdszJson} 高性能引擎，同时保留 Jackson ObjectMapper 用于向后兼容。
 *
 * <p><b>默认行为（YdszJson 引擎）：</b>
 * <ul>
 *   <li>ASM 字节码加速 + LRU 字段缓存</li>
 *   <li>零拷贝反序列化</li>
 *   <li>日期格式：yyyy-MM-dd HH:mm:ss（Java 8 时间 API）</li>
 *   <li>忽略未知字段（容错解析）</li>
 *   <li>序列化失败抛出 {@link JsonException}</li>
 * </ul>
 *
 * <p><b>保留 Jackson ObjectMapper 用于：</b>
 * <ul>
 *   <li>Spring MVC HttpMessageConverter 集成（@JsonProperty, @JsonFormat 注解）</li>
 *   <li>XSS 安全模块的自定义序列化器</li>
 *   <li>向后兼容 {@link #getMapper()} 调用方</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 5.0.0（迁移到 YdszJson 引擎）
 */
public final class JsonUtils {

    /**
     * Jackson ObjectMapper（保留用于框架集成和向后兼容）
     * <p>注意：此类仅用于 getMapper() 返回，实际序列化/反序列化使用 YdszJson</p>
     */
    private static final ObjectMapper JACKSON_MAPPER;

    /**
     * JSON 处理指标（内部计数器 + 可选 Micrometer）
     */
    private static volatile JsonMetrics metrics;

    /**
     * JSON 序列化/反序列化异常
     */
    public static class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JsonException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static {
        // 初始化 Jackson ObjectMapper（保留用于框架集成）
        JACKSON_MAPPER = new ObjectMapper();
        JACKSON_MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        JACKSON_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        JACKSON_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JACKSON_MAPPER.setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
        JACKSON_MAPPER.deactivateDefaultTyping();
        JACKSON_MAPPER.registerModule(new JavaTimeModule());

        // 初始化 YdszJson 配置（日期格式与 Jackson 保持一致）
        YdszJsonConfig config = YdszJsonConfig.getInstance();
        config.setWriteNulls(true);
        config.setDateFormat("yyyy-MM-dd HH:mm:ss");
        config.setMaxJsonSize(10L * 1024 * 1024);
        config.setMaxDepth(256);
        config.apply();
    }

    private JsonUtils() {
        throw new UnsupportedOperationException("JsonUtils is a utility class");
    }

    /**
     * 设置 JSON 处理指标收集器
     *
     * @param jsonMetrics 指标收集器
     */
    public static void setMetrics(JsonMetrics jsonMetrics) {
        JsonUtils.metrics = jsonMetrics;
    }

    /**
     * 获取 JSON 处理指标收集器
     *
     * @return 指标收集器，未设置时返回 null
     */
    public static JsonMetrics getMetrics() {
        return metrics;
    }

    /**
     * 获取 Jackson ObjectMapper 实例（向后兼容）
     *
     * <p><b>注意：</b>此方法返回的 Jackson ObjectMapper 仅用于：
     * <ul>
     *   <li>Spring MVC HttpMessageConverter 集成（XSS 安全、注解处理）</li>
     *   <li>树操作：readTree, createObjectNode, createArrayNode</li>
     *   <li>向后兼容现有调用方</li>
     * </ul>
     *
     * <p>新的序列化/反序列化代码请直接使用 {@link #toJson(Object)} 和 {@link #fromJson} 方法，
     * 这些方法内部使用高性能的 {@link YdszJson} 引擎。
     *
     * @return Jackson ObjectMapper 实例
     */
    public static ObjectMapper getMapper() {
        return JACKSON_MAPPER;
    }

    // ==================== 对象 → JSON 字符串 ====================

    /**
     * 对象转 JSON 字符串（使用 YdszJson 引擎）
     *
     * @param obj 待序列化对象
     * @return JSON 字符串，对象为 null 时返回 null
     * @throws JsonException 如果序列化失败
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return YdszJson.toJson(obj);
        } catch (Exception e) {
            recordSerializeFail();
            throw new JsonException("JSON序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 对象转美化格式的 JSON 字符串（使用 YdszJson 引擎）
     *
     * @param obj 待序列化对象
     * @return 美化格式的 JSON 字符串
     * @throws JsonException 如果序列化失败
     */
    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return YdszJson.format(obj);
        } catch (Exception e) {
            recordSerializeFail();
            throw new JsonException("JSON序列化失败: " + e.getMessage(), e);
        }
    }

    // ==================== JSON 字符串 → 对象 ====================

    /**
     * JSON 字符串转对象（使用 YdszJson 引擎，兼容别名）
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   目标类型泛型
     * @return 反序列化对象，json 为空时返回 null
     * @throws JsonException 如果反序列化失败
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        return fromJson(json, clazz);
    }

    /**
     * JSON 字符串转对象（使用 YdszJson 引擎）
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   目标类型泛型
     * @return 反序列化对象，json 为空时返回 null
     * @throws JsonException 如果反序列化失败
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return YdszJson.toObject(json, clazz);
        } catch (Exception e) {
            recordDeserializeFail();
            throw new JsonException("JSON反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * JSON 字符串转泛型对象（使用 YdszJson 引擎）
     *
     * <p>兼容 Jackson 的 TypeReference API，内部自动转换为 YdszJsonType。</p>
     *
     * @param json          JSON 字符串
     * @param typeReference 类型引用（用于泛型擦除场景）
     * @param <T>           目标类型泛型
     * @return 反序列化对象，json 为空时返回 null
     * @throws JsonException 如果反序列化失败
     *
     * <p>示例：
     * <pre>
     * Map&lt;String, Object&gt; map = JsonUtils.fromJson(json, new TypeReference&lt;Map&lt;String, Object&gt;&gt;() {});
     * List&lt;UserDTO&gt; list = JsonUtils.fromJson(json, new TypeReference&lt;List&lt;UserDTO&gt;&gt;() {});
     * </pre>
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Type type = typeReference.getType();
            return YdszJson.toObject(json, type);
        } catch (Exception e) {
            recordDeserializeFail();
            throw new JsonException("JSON反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * JSON 字符串转对象（使用 YdszJson 引擎，支持 Type）
     *
     * <p>适用于 Feign 解码器等需要动态类型反序列化的场景。</p>
     *
     * @param json JSON 字符串
     * @param type 目标类型（支持 Class、ParameterizedType 等）
     * @return 反序列化对象，json 为空时返回 null
     * @throws JsonException 如果反序列化失败
     */
    public static <T> T fromJson(String json, Type type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return YdszJson.toObject(json, type);
        } catch (Exception e) {
            recordDeserializeFail();
            throw new JsonException("JSON反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * JSON 字符串转 List（使用 YdszJson 引擎）
     *
     * @param json  JSON 字符串
     * @param clazz 列表元素类型
     * @param <T>   元素类型泛型
     * @return 反序列化列表，json 为空时返回 null
     * @throws JsonException 如果反序列化失败
     */
    public static <T> List<T> fromJsonToList(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return YdszJson.parseArray(json, clazz);
        } catch (Exception e) {
            recordDeserializeFail();
            throw new JsonException("JSON反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * JSON 字符串转 Map（使用 YdszJson 引擎）
     *
     * @param json      JSON 字符串
     * @param keyClass  Map key 类型
     * @param valueClass Map value 类型
     * @param <K>       key 类型泛型
     * @param <V>       value 类型泛型
     * @return 反序列化 Map，json 为空时返回 null
     * @throws JsonException 如果反序列化失败
     */
    public static <K, V> Map<K, V> fromJsonToMap(String json, Class<K> keyClass, Class<V> valueClass) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            // YdszJson 返回原始 Map，需要类型转换
            Map<?, ?> rawMap = YdszJson.parseObject(json);
            Map<K, V> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                K key = keyClass.cast(entry.getKey());
                V value = valueClass.cast(entry.getValue());
                result.put(key, value);
            }
            return result;
        } catch (Exception e) {
            recordDeserializeFail();
            throw new JsonException("JSON反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * JSON 字符串转 Map&lt;String, Object&gt;（使用 YdszJson 引擎，兼容别名）
     *
     * @param json JSON 字符串
     * @return 反序列化 Map，json 为空时返回 null
     * @throws JsonException 如果反序列化失败
     */
    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return YdszJson.parseObject(json);
        } catch (Exception e) {
            recordDeserializeFail();
            throw new JsonException("JSON反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * JSON 字符串转 List&lt;Object&gt;（使用 YdszJson 引擎，兼容别名）
     *
     * @param json JSON 字符串
     * @return 反序列化 List，json 为空时返回 null
     * @throws JsonException 如果反序列化失败
     */
    public static List<Object> parseList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return YdszJson.parseArray(json);
        } catch (Exception e) {
            recordDeserializeFail();
            throw new JsonException("JSON反序列化失败: " + e.getMessage(), e);
        }
    }

    // ==================== 对象 → 字节数组 ====================

    /**
     * 对象转 JSON 字节数组（UTF-8 编码，使用 YdszJson 引擎）
     *
     * @param obj 待序列化对象
     * @return JSON 字节数组
     * @throws JsonException 如果序列化失败
     */
    public static byte[] toJsonBytes(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        try {
            return YdszJson.toJsonBytes(obj);
        } catch (Exception e) {
            recordSerializeFail();
            throw new JsonException("JSON序列化失败: " + e.getMessage(), e);
        }
    }

    // ==================== 字节数组 → 对象 ====================

    /**
     * 字节数组转对象（UTF-8 编码，使用 YdszJson 引擎）
     *
     * @param bytes JSON 字节数组
     * @param clazz 目标类型
     * @param <T>   目标类型泛型
     * @return 反序列化对象
     * @throws JsonException 如果反序列化失败
     */
    public static <T> T fromJsonBytes(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            String json = new String(bytes, StandardCharsets.UTF_8);
            return YdszJson.toObject(json, clazz);
        } catch (Exception e) {
            recordDeserializeFail();
            throw new JsonException("JSON反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 字节数组转泛型对象（UTF-8 编码，使用 YdszJson 引擎）
     *
     * @param bytes         JSON 字节数组
     * @param typeReference 类型引用
     * @param <T>           目标类型泛型
     * @return 反序列化对象
     * @throws JsonException 如果反序列化失败
     */
    public static <T> T fromJsonBytes(byte[] bytes, TypeReference<T> typeReference) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            String json = new String(bytes, StandardCharsets.UTF_8);
            Type type = typeReference.getType();
            return YdszJson.toObject(json, type);
        } catch (Exception e) {
            recordDeserializeFail();
            throw new JsonException("JSON反序列化失败: " + e.getMessage(), e);
        }
    }

    // ==================== 树模型支持（YdszJson 引擎） ====================

    /**
     * 将 JSON 字符串解析为 YdszJson JsonNode 树
     *
     * <p>使用 YdszJson 引擎，对标 Jackson 的 readTree()。</p>
     *
     * @param json JSON 字符串
     * @return JsonNode 树
     * @throws JsonException 如果解析失败
     */
    public static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return com.njydsz.pmis.common.json.tree.MissingNode.getInstance();
        }
        try {
            return YdszJson.readTree(json);
        } catch (Exception e) {
            throw new JsonException("JSON树解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将对象转换为 YdszJson JsonNode 树
     *
     * @param obj 待转换对象
     * @return JsonNode 树
     * @throws JsonException 如果转换失败
     */
    public static JsonNode valueToTree(Object obj) {
        if (obj == null) {
            return com.njydsz.pmis.common.json.tree.NullNode.getInstance();
        }
        try {
            String json = YdszJson.toJson(obj);
            return YdszJson.readTree(json);
        } catch (Exception e) {
            throw new JsonException("对象转JSON树失败: " + e.getMessage(), e);
        }
    }

    // ==================== 指标记录 ====================

    private static void recordSerializeFail() {
        JsonMetrics m = metrics;
        if (m != null) {
            m.recordSerializeFail();
        }
    }

    private static void recordDeserializeFail() {
        JsonMetrics m = metrics;
        if (m != null) {
            m.recordDeserializeFail();
        }
    }
}