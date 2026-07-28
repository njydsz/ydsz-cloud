package com.njydsz.common.json;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.json.config.YdszJsonConfig;
import com.njydsz.common.json.exception.YdszJsonException;
import com.njydsz.common.json.provider.DeserializationProvider;
import com.njydsz.common.json.provider.SerializationProvider;
import com.njydsz.common.json.type.YdszJsonType;

/**
 * YdszJson 实例化 Mapper（对标 Jackson ObjectMapper）
 *
 * <p>提供实例化的 JSON 序列化/反序列化能力，每个实例持有独立的 {@link YdszJsonConfig} 配置副本，
 * 允许在同一 JVM 中创建多个不同配置的 Mapper 实例，互不干扰。
 *
 * <p><b>与 {@link YdszJson} 的关系：</b></p>
 * <ul>
 *   <li>{@code YdszJson} 静态方法委托给内部默认 {@code YdszJsonMapper} 实例，保持向后兼容</li>
 *   <li>需要独立配置的场景应创建新的 {@code YdszJsonMapper} 实例</li>
 *   <li>{@link #copy()} 方法创建配置副本，修改不影响原实例</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 创建默认 Mapper
 * YdszJsonMapper mapper = new YdszJsonMapper();
 *
 * // 创建配置副本并自定义
 * YdszJsonMapper prettyMapper = mapper.copy();
 * prettyMapper.getConfig().setWriteNulls(true);
 *
 * // 独立配置序列化，不影响全局
 * String json = prettyMapper.toJson(obj);
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class YdszJsonMapper {

    /** 默认单例实例（YdszJson 静态方法委托给此实例） */
    private static final YdszJsonMapper DEFAULT = new YdszJsonMapper();

    /** 此 Mapper 实例的配置（独立副本） */
    private final YdszJsonConfig config;

    /**
     * 创建默认配置的 Mapper 实例。
     */
    public YdszJsonMapper() {
        this(YdszJsonConfig.getInstance());
    }

    /**
     * 创建指定配置的 Mapper 实例。
     *
     * @param config 配置（会被复制为独立副本）
     */
    public YdszJsonMapper(YdszJsonConfig config) {
        this.config = YdszJsonConfig.copyOf(config);
    }

    /**
     * 获取此 Mapper 的配置对象（可直接修改，不影响全局配置）。
     *
     * @return 配置对象
     */
    public YdszJsonConfig getConfig() {
        return config;
    }

    /**
     * 创建配置副本（独立实例，修改不影响原 Mapper）。
     *
     * @return 新的 Mapper 实例
     */
    public YdszJsonMapper copy() {
        return new YdszJsonMapper(this.config);
    }

    // ==================== 序列化方法 ====================

    /**
     * 序列化对象为 JSON 字符串。
     *
     * @param obj 要序列化的对象
     * @return JSON 字符串
     */
    public String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();
        try {
            config.apply();
            return SerializationProvider.serialize(obj);
        } finally {
            snapshot.restore();
        }
    }

    /**
     * 序列化对象为 JSON 字符串（可选格式化）。
     *
     * @param obj   要序列化的对象
     * @param pretty 是否格式化
     * @return JSON 字符串
     */
    public String toJson(Object obj, boolean pretty) {
        if (obj == null) {
            return "null";
        }
        if (pretty) {
            SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();
            try {
                config.apply();
                return SerializationProvider.format(obj);
            } finally {
                snapshot.restore();
            }
        }
        return toJson(obj);
    }

    /**
     * 序列化对象为 UTF-8 字节数组。
     *
     * @param obj 要序列化的对象
     * @return UTF-8 编码的字节数组
     */
    public byte[] toJsonBytes(Object obj) {
        if (obj == null) {
            return new byte[]{'n', 'u', 'l', 'l'};
        }
        SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();
        try {
            config.apply();
            return SerializationProvider.serializeToBytes(obj);
        } finally {
            snapshot.restore();
        }
    }

    /**
     * 序列化对象并直接写入 OutputStream。
     *
     * @param obj 要序列化的对象
     * @param out 输出流
     */
    public void writeValue(Object obj, OutputStream out) {
        byte[] bytes = toJsonBytes(obj);
        try {
            out.write(bytes);
        } catch (Exception e) {
            throw new YdszJsonException("Failed to write to OutputStream", e);
        }
    }

    /**
     * 序列化对象并直接写入 Writer。
     *
     * @param obj    要序列化的对象
     * @param writer 字符输出流
     */
    public void writeValue(Object obj, Writer writer) {
        String json = toJson(obj);
        try {
            writer.write(json);
        } catch (Exception e) {
            throw new YdszJsonException("Failed to write to Writer", e);
        }
    }

    // ==================== 反序列化方法 ====================

    /**
     * 反序列化 JSON 字符串为指定类型。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return 反序列化后的对象
     */
    public <T> T toObject(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return DeserializationProvider.deserialize(json, clazz);
    }

    /**
     * 反序列化 JSON 字符串为泛型类型。
     *
     * @param json     JSON 字符串
     * @param type     目标类型
     * @param <T>      类型参数
     * @return 反序列化后的对象
     */
    public <T> T toObject(String json, Type type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return DeserializationProvider.deserialize(json, type);
    }

    /**
     * 反序列化 JSON 字符串为泛型类型（YdszJsonType）。
     *
     * @param json    JSON 字符串
     * @param typeRef 类型引用
     * @param <T>     类型参数
     * @return 反序列化后的对象
     */
    public <T> T toObject(String json, YdszJsonType<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return DeserializationProvider.deserialize(json, typeRef.getType());
    }

    /**
     * 从 InputStream 读取 JSON 并反序列化。
     *
     * @param in    输入流
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return 反序列化后的对象
     */
    public <T> T readValue(InputStream in, Class<T> clazz) {
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
        } catch (Exception e) {
            throw new YdszJsonException("Failed to read from InputStream", e);
        }
    }

    /**
     * 解析 JSON 字符串为 Map。
     *
     * @param json JSON 字符串
     * @return Map 对象
     */
    public Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        Object result = DeserializationProvider.deserialize(json, Map.class);
        if (result instanceof Map<?, ?> map) {
            Map<String, Object> typedMap = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                typedMap.put((String) entry.getKey(), entry.getValue());
            }
            return typedMap;
        }
        return new LinkedHashMap<>();
    }

    /**
     * 解析 JSON 数组为指定类型的列表。
     *
     * @param json       JSON 字符串
     * @param elementClass 元素类型
     * @param <T>        元素类型
     * @return 列表
     */
    public <T> List<T> parseArray(String json, Class<T> elementClass) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        Object result = DeserializationProvider.deserialize(json, new java.lang.reflect.ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() { return new Type[]{elementClass}; }
            @Override
            public Type getRawType() { return List.class; }
            @Override
            public Type getOwnerType() { return null; }
        });
        if (result instanceof List<?> list) {
            List<T> typedList = new ArrayList<>(list.size());
            for (Object item : list) {
                typedList.add(elementClass.cast(item));
            }
            return typedList;
        }
        return new ArrayList<>();
    }

    // ==================== 字段排除（列权限） ====================

    /**
     * 序列化对象并排除指定字段（自动清理 ThreadLocal）。
     *
     * @param obj               要序列化的对象
     * @param excludedFieldNames 需要排除的字段名集合
     * @return JSON 字符串
     */
    public String toJsonExcludeFields(Object obj, Set<String> excludedFieldNames) {
        if (obj == null) {
            return "null";
        }
        Set<String> previous = SerializationProvider.getExcludedFields();
        try {
            SerializationProvider.setExcludedFields(excludedFieldNames);
            return SerializationProvider.serialize(obj);
        } finally {
            SerializationProvider.setExcludedFields(previous);
        }
    }

    // ==================== 内部方法 ====================

    private void validateJsonSize(String json) {
        long maxSize = config.getMaxJsonSize();
        if (json.length() > maxSize) {
            throw new YdszJsonException(
                "JSON size exceeds limit: " + json.length() + " > " + maxSize);
        }
    }

    /**
     * 获取默认 Mapper 实例。
     *
     * @return 默认单例实例
     */
    public static YdszJsonMapper getDefault() {
        return DEFAULT;
    }
}
