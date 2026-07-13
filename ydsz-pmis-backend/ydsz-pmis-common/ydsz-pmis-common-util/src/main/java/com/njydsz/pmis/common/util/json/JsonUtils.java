package com.njydsz.pmis.common.util.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * 统一 JSON 序列化工具类（基于 Jackson）
 *
 * <p>提供对象与 JSON 字符串/字节数组之间的双向转换。
 * 遵循大厂标准，全项目统一使用 Jackson 作为 JSON 引擎。
 *
 * <p><b>默认行为：</b>
 * <ul>
 *   <li>输出 null 值字段（WriteMapNullValue）</li>
 *   <li>日期格式：yyyy-MM-dd HH:mm:ss（Java 8 时间 API）</li>
 *   <li>忽略未知字段（FAIL_ON_UNKNOWN_PROPERTIES = false）</li>
 *   <li>忽略空 Bean 序列化错误（FAIL_ON_EMPTY_BEANS = false）</li>
 *   <li>序列化失败抛出 {@link JsonException}，不吞错误</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 */
public final class JsonUtils {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String TIME_PATTERN = "HH:mm:ss";

    /**
     * 全局共享的 ObjectMapper 单例（线程安全）
     */
    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.setTimeZone(TimeZone.getDefault());
        MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MAPPER.setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);

        // 安全加固：明确禁用全局默认类型（多态反序列化），防止反序列化漏洞
        MAPPER.deactivateDefaultTyping();

        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)));
        javaTimeModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
        javaTimeModule.addSerializer(LocalTime.class,
                new LocalTimeSerializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)));
        javaTimeModule.addDeserializer(LocalDate.class,
                new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
        javaTimeModule.addDeserializer(LocalTime.class,
                new LocalTimeDeserializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));
        MAPPER.registerModule(javaTimeModule);
    }

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

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static <T> T recordSerialize(ThrowingSupplier<T> supplier) {
        long start = System.nanoTime();
        try {
            T result = supplier.get();
            recordSerializeSuccess(System.nanoTime() - start);
            return result;
        } catch (JsonException e) {
            recordSerializeFail();
            throw e;
        } catch (Exception e) {
            recordSerializeFail();
            throw new JsonException("JSON序列化失败: " + e.getMessage(), e);
        }
    }

    private static <T> T recordDeserialize(ThrowingSupplier<T> supplier) {
        long start = System.nanoTime();
        try {
            T result = supplier.get();
            recordDeserializeSuccess(System.nanoTime() - start);
            return result;
        } catch (JsonException e) {
            recordDeserializeFail();
            throw e;
        } catch (Exception e) {
            recordDeserializeFail();
            throw new JsonException("JSON反序列化失败: " + e.getMessage(), e);
        }
    }

    private static void recordSerializeSuccess(long nanos) {
        JsonMetrics m = metrics;
        if (m != null) {
            m.recordSerializeSuccess(nanos);
        }
    }

    private static void recordSerializeFail() {
        JsonMetrics m = metrics;
        if (m != null) {
            m.recordSerializeFail();
        }
    }

    private static void recordDeserializeSuccess(long nanos) {
        JsonMetrics m = metrics;
        if (m != null) {
            m.recordDeserializeSuccess(nanos);
        }
    }

    private static void recordDeserializeFail() {
        JsonMetrics m = metrics;
        if (m != null) {
            m.recordDeserializeFail();
        }
    }

    /**
     * 获取全局共享的 ObjectMapper 实例
     *
     * <p>注意：此 ObjectMapper 是全局共享的，不应修改其配置。
     * 如需自定义配置，请通过 {@link ObjectMapper#copy()} 创建副本后修改。
     *
     * @return 全局 ObjectMapper 实例
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    // ==================== 对象 → JSON 字符串 ====================

    /**
     * 对象转 JSON 字符串
     *
     * @param obj 待序列化对象
     * @return JSON 字符串，对象为 null 时返回 null
     * @throws JsonException 如果序列化失败
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return recordSerialize(() -> MAPPER.writeValueAsString(obj));
    }

    /**
     * 对象转美化格式的 JSON 字符串
     *
     * @param obj 待序列化对象
     * @return 美化格式的 JSON 字符串
     * @throws JsonException 如果序列化失败
     */
    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return recordSerialize(() -> MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj));
    }

    // ==================== JSON 字符串 → 对象 ====================

    /**
     * JSON 字符串转对象（兼容别名，等价于 {@link #fromJson}）
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
     * JSON 字符串转对象
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
        return recordDeserialize(() -> MAPPER.readValue(json, clazz));
    }

    /**
     * JSON 字符串转泛型对象
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
        return recordDeserialize(() -> MAPPER.readValue(json, typeReference));
    }

    /**
     * JSON 字符串转对象（支持 java.lang.reflect.Type）
     *
     * <p>适用于 Feign 解码器等需要动态类型反序列化的场景。
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
        return recordDeserialize(() -> MAPPER.readValue(json, MAPPER.constructType(type)));
    }

    /**
     * JSON 字符串转 List
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
        return recordDeserialize(() ->
                MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, clazz)));
    }

    /**
     * JSON 字符串转 Map
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
        return recordDeserialize(() ->
                MAPPER.readValue(json, MAPPER.getTypeFactory().constructMapType(java.util.HashMap.class, keyClass, valueClass)));
    }

    // ==================== 对象 → 字节数组 ====================

    /**
     * 对象转 JSON 字节数组（UTF-8 编码）
     *
     * @param obj 待序列化对象
     * @return JSON 字节数组
     * @throws JsonException 如果序列化失败
     */
    public static byte[] toJsonBytes(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        return recordSerialize(() -> MAPPER.writeValueAsBytes(obj));
    }

    // ==================== 字节数组 → 对象 ====================

    /**
     * 字节数组转对象（UTF-8 编码）
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
        return recordDeserialize(() -> MAPPER.readValue(bytes, clazz));
    }

    /**
     * 字节数组转泛型对象（UTF-8 编码）
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
        return recordDeserialize(() -> MAPPER.readValue(bytes, typeReference));
    }
}
