package com.njydsz.pmis.common.util.json;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.pmis.common.json.YdszJson;
import com.njydsz.pmis.common.json.config.YdszJsonConfig;
import com.njydsz.pmis.common.json.exception.YdszJsonException;
import com.njydsz.pmis.common.json.type.YdszJsonType;

/**
 * 统一 JSON 序列化工具类（基于 YdszJson 引擎）
 *
 * <p>提供对象与 JSON 字符串/字节数组之间的双向转换。
 * 全项目统一使用 YdszJson 作为 JSON 引擎，零第三方 JSON 库依赖。
 *
 * <p><b>默认行为：</b>
 * <ul>
 *   <li>日期格式：yyyy-MM-dd HH:mm:ss（Java 8 时间 API）</li>
 *   <li>忽略未知字段</li>
 *   <li>忽略空 Bean 序列化错误</li>
 *   <li>序列化失败抛出 {@link JsonException}，不吞错误</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 5.0.0
 */
public final class JsonUtils {

    static {
        // 初始化 YdszJson 全局配置
        YdszJsonConfig config = YdszJsonConfig.getInstance();
        config.setDateFormat("yyyy-MM-dd HH:mm:ss");
        config.setIgnoreUnknownProperties(true);
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
        return recordSerialize(() -> YdszJson.toJson(obj));
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
        return recordSerialize(() -> YdszJson.format(obj));
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
        return recordDeserialize(() -> YdszJson.toObject(json, clazz));
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
     * Map&lt;String, Object&gt; map = JsonUtils.fromJson(json, new YdszJsonType&lt;Map&lt;String, Object&gt;&gt;() {});
     * List&lt;UserDTO&gt; list = JsonUtils.fromJson(json, new YdszJsonType&lt;List&lt;UserDTO&gt;&gt;() {});
     * </pre>
     */
    public static <T> T fromJson(String json, YdszJsonType<T> typeReference) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return recordDeserialize(() -> YdszJson.toObject(json, typeReference));
    }

    /**
     * JSON 字符串转对象（支持 Type）
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
        return recordDeserialize(() -> YdszJson.toObject(json, type));
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
        return recordDeserialize(() -> YdszJson.parseArray(json, clazz));
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
            Object result = YdszJson.toObject(json, mapType);
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

    /**
     * JSON 字符串转 Map&lt;String, Object&gt;（兼容别名）
     *
     * @param json JSON 字符串
     * @return 反序列化 Map，json 为空时返回 null
     * @throws JsonException 如果反序列化失败
     */
    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return recordDeserialize(() -> YdszJson.parseObject(json));
    }

    /**
     * JSON 字符串转 List&lt;Object&gt;（兼容别名）
     *
     * @param json JSON 字符串
     * @return 反序列化 List，json 为空时返回 null
     * @throws JsonException 如果反序列化失败
     */
    public static List<Object> parseList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return recordDeserialize(() -> YdszJson.parseArray(json));
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
        return recordSerialize(() -> YdszJson.toJsonBytes(obj));
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
        return recordDeserialize(() -> {
            String json = new String(bytes, StandardCharsets.UTF_8);
            return YdszJson.toObject(json, clazz);
        });
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
    public static <T> T fromJsonBytes(byte[] bytes, YdszJsonType<T> typeReference) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return recordDeserialize(() -> {
            String json = new String(bytes, StandardCharsets.UTF_8);
            return YdszJson.toObject(json, typeReference);
        });
    }

    // ==================== 辅助方法 ====================

    /**
     * 验证字符串是否为合法 JSON
     *
     * @param json 待验证的字符串
     * @return 如果是合法 JSON 返回 true，否则返回 false
     */
    public static boolean isValidJson(String json) {
        return YdszJson.isValid(json);
    }
}
