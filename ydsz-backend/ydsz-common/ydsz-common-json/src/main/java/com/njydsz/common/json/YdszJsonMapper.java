package com.njydsz.common.json;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.json.config.YdszJsonConfig;
import com.njydsz.common.json.exception.YdszJsonException;
import com.njydsz.common.json.jsonpath.YdszJsonPath;
import com.njydsz.common.json.metric.MetricsHelper;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.parser.YdszJsonParser;
import com.njydsz.common.json.pointer.JsonPointer;
import com.njydsz.common.json.provider.DeserializationProvider;
import com.njydsz.common.json.provider.SerializationProvider;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.TreeConverter;
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
 *   <li>所有操作均纳入 {@link JsonMetricsCallback} 指标监控（与 YdszJson 静态方法一致）</li>
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
 *
 * // 视图过滤序列化
 * String viewJson = mapper.toJson(obj, ViewClass.class);
 *
 * // 树模型
 * JsonNode tree = mapper.readTree(json);
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
            return recordSerialize(() -> SerializationProvider.serialize(obj));
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
                return recordSerialize(() -> SerializationProvider.format(obj));
            } finally {
                snapshot.restore();
            }
        }
        return toJson(obj);
    }

    /**
     * 序列化对象为 JSON 字符串（带视图过滤）。
     *
     * <p>根据 @YdszJsonView 注解过滤字段，仅输出指定视图下可见的字段。</p>
     *
     * @param obj       要序列化的对象
     * @param viewClass 视图类
     * @return JSON 字符串
     * @since 1.0.0
     */
    public String toJson(Object obj, Class<?> viewClass) {
        if (obj == null) {
            return "null";
        }
        SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();
        try {
            config.apply();
            return recordSerialize(() -> SerializationProvider.serializeWithView(obj, viewClass));
        } finally {
            snapshot.restore();
        }
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
            return recordSerialize(() -> SerializationProvider.serializeToBytes(obj));
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
        return recordDeserialize(() -> DeserializationProvider.deserialize(json, clazz));
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
        return recordDeserialize(() -> DeserializationProvider.deserialize(json, type));
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
        return recordDeserialize(() -> DeserializationProvider.deserialize(json, typeRef.getType()));
    }

    /**
     * 从 JSON 字符串反序列化为指定类型（与 {@link #toJson(Object)} 对称的 API）。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return 反序列化后的对象
     * @since 1.0.0
     */
    public <T> T fromJson(String json, Class<T> clazz) {
        return toObject(json, clazz);
    }

    /**
     * 从 JSON 字符串反序列化为指定泛型类型（与 {@link #toJson(Object)} 对称的 API）。
     *
     * @param json    JSON 字符串
     * @param typeRef 类型引用
     * @param <T>     类型参数
     * @return 反序列化后的对象
     * @since 1.0.0
     */
    public <T> T fromJson(String json, YdszJsonType<T> typeRef) {
        return toObject(json, typeRef);
    }

    /**
     * 字节数组转对象（UTF-8 编码）。
     *
     * @param bytes JSON 字节数组
     * @param clazz 目标类型
     * @param <T>   目标类型泛型
     * @return 反序列化对象，bytes 为空时返回 null
     * @since 1.0.0
     */
    public <T> T fromJsonBytes(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        long maxSize = config.getMaxJsonSize();
        if (bytes.length > maxSize) {
            throw new YdszJsonException(
                "JSON size exceeds limit: " + bytes.length + " > " + maxSize);
        }
        SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();
        try {
            config.apply();
            return recordDeserialize(() -> DeserializationProvider.deserialize(bytes, clazz));
        } finally {
            snapshot.restore();
        }
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
            return fromJsonBytes(bytes, clazz);
        } catch (Exception e) {
            throw new YdszJsonException("Failed to read from InputStream", e);
        }
    }

    /**
     * 从 InputStream 读取 JSON 并反序列化为泛型类型。
     *
     * @param in      输入流
     * @param typeRef 类型引用
     * @param <T>     类型参数
     * @return 反序列化后的对象
     * @since 1.0.0
     */
    public <T> T readValue(InputStream in, YdszJsonType<T> typeRef) {
        if (in == null) {
            return null;
        }
        try {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            long maxSize = config.getMaxJsonSize();
            if (bytes.length > maxSize) {
                throw new YdszJsonException(
                    "JSON size exceeds limit: " + bytes.length + " > " + maxSize);
            }
            SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();
            try {
                config.apply();
                return recordDeserialize(() -> DeserializationProvider.deserialize(bytes, typeRef.getType()));
            } finally {
                snapshot.restore();
            }
        } catch (Exception e) {
            if (e instanceof YdszJsonException) throw (YdszJsonException) e;
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
        return recordDeserialize(() -> {
            Object result = DeserializationProvider.deserialize(json, Map.class);
            if (result instanceof Map<?, ?> map) {
                Map<String, Object> typedMap = new LinkedHashMap<>(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    typedMap.put((String) entry.getKey(), entry.getValue());
                }
                return typedMap;
            }
            return new LinkedHashMap<String, Object>();
        });
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
        Object result = DeserializationProvider.deserialize(json, new ParameterizedType() {
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

    // ==================== 树模型 API ====================

    /**
     * 将 JSON 字符串解析为 JsonNode 树。
     *
     * @param json JSON 字符串
     * @return JsonNode 树
     * @since 1.0.0
     */
    public JsonNode readTree(String json) {
        Object parsed = YdszJsonParser.parse(json);
        return TreeConverter.convertToJsonNode(parsed);
    }

    /**
     * 将对象序列化为 JsonNode 树。
     *
     * @param obj 要序列化的对象
     * @return JsonNode 树
     * @since 1.0.0
     */
    public JsonNode valueToTree(Object obj) {
        String json = toJson(obj);
        return readTree(json);
    }

    // ==================== JSONPath / JSONPointer API ====================

    /**
     * 通过 JSONPath 获取值。
     *
     * @param json JSON 字符串
     * @param path JSONPath 表达式
     * @return 匹配的值
     * @since 1.0.0
     */
    public Object getByPath(String json, String path) {
        return YdszJsonPath.get(json, path);
    }

    /**
     * 使用 JSON Pointer 获取值。
     *
     * @param json    JSON 字符串
     * @param pointer JSON Pointer 路径
     * @return 指针指向的值
     * @since 1.0.0
     */
    public Object getByPointer(String json, String pointer) {
        return new JsonPointer(pointer).evaluate(json);
    }

    // ==================== 类型转换 API ====================

    /**
     * 将对象从一种类型转换为另一种类型（对标 Jackson ObjectMapper.convertValue）。
     *
     * <p>通过序列化 -> 反序列化管道实现类型转换。</p>
     *
     * @param fromValue 源对象
     * @param toValueType 目标类型
     * @param <T> 目标类型参数
     * @return 转换后的对象
     * @since 1.3.0
     */
    public <T> T convertValue(Object fromValue, Class<T> toValueType) {
        if (fromValue == null) {
            return null;
        }
        String json = toJson(fromValue);
        return toObject(json, toValueType);
    }

    /**
     * 将对象从一种类型转换为另一种泛型类型（对标 Jackson ObjectMapper.convertValue）。
     *
     * @param fromValue 源对象
     * @param toValueTypeRef 目标类型引用
     * @param <T> 目标类型参数
     * @return 转换后的对象
     * @since 1.3.0
     */
    public <T> T convertValue(Object fromValue, YdszJsonType<T> toValueTypeRef) {
        if (fromValue == null) {
            return null;
        }
        String json = toJson(fromValue);
        return toObject(json, toValueTypeRef);
    }

    /**
     * 将 JsonNode 树转换为指定类型的对象（对标 Jackson ObjectMapper.treeToValue）。
     *
     * @param node JsonNode 树
     * @param clazz 目标类型
     * @param <T> 目标类型参数
     * @return 转换后的对象
     * @since 1.3.0
     */
    public <T> T treeToValue(JsonNode node, Class<T> clazz) {
        if (node == null) {
            return null;
        }
        String json = node.toString();
        return toObject(json, clazz);
    }

    /**
     * 序列化对象为 JSON 字符串（对标 Jackson ObjectMapper.writeValueAsString）。
     *
     * @param obj 要序列化的对象
     * @return JSON 字符串
     * @since 1.3.0
     */
    public String writeValueAsString(Object obj) {
        return toJson(obj);
    }

    /**
     * 序列化对象为 UTF-8 字节数组（对标 Jackson ObjectMapper.writeValueAsBytes）。
     *
     * @param obj 要序列化的对象
     * @return UTF-8 编码的字节数组
     * @since 1.3.0
     */
    public byte[] writeValueAsBytes(Object obj) {
        return toJsonBytes(obj);
    }

    /**
     * 从 JSON 字符串读取指定类型的对象（对标 Jackson ObjectMapper.readValue）。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T> 目标类型参数
     * @return 反序列化后的对象
     * @since 1.3.0
     */
    public <T> T readValue(String json, Type type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return recordDeserialize(() -> DeserializationProvider.deserialize(json, type));
    }

    /**
     * 格式化输出 JSON 字符串（美化模式）。
     *
     * @param obj 要序列化的对象
     * @return 格式化的 JSON 字符串
     * @since 1.3.0
     */
    public String format(Object obj) {
        return toJson(obj, true);
    }

    // ==================== 绑定型读写器（对标 Jackson ObjectReader/ObjectWriter） ====================

    /**
     * 创建绑定指定类型的 JSON 读取器。
     *
     * <p>对标 Jackson {@code ObjectMapper.readerFor(Class)}，
     * 返回的 {@link YdszJsonReader} 绑定了目标类型，可重复使用。</p>
     *
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return 绑定型读取器
     * @since 1.4.0
     */
    public <T> YdszJsonReader<T> readerFor(Class<T> clazz) {
        return new YdszJsonReader<>(this, clazz);
    }

    /**
     * 创建绑定指定类型的 JSON 写入器。
     *
     * <p>对标 Jackson {@code ObjectMapper.writerFor(Class)}，
     * 返回的 {@link YdszJsonWriter} 绑定了目标类型，可重复使用。</p>
     *
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return 绑定型写入器
     * @since 1.4.0
     */
    public <T> YdszJsonWriter<T> writerFor(Class<T> clazz) {
        return new YdszJsonWriter<>(this);
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
    public void warmup(Class<?>... classes) {
        YdszJson.warmup(classes);
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
        SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();
        try {
            config.apply();
            Set<String> previous = SerializationProvider.getExcludedFields();
            SerializationProvider.setExcludedFields(excludedFieldNames);
            try {
                return recordSerialize(() -> SerializationProvider.serialize(obj));
            } finally {
                SerializationProvider.setExcludedFields(previous);
            }
        } finally {
            snapshot.restore();
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

    // ==================== 指标监控包装（委托给 MetricsHelper） ====================

    /**
     * 序列化操作的指标监控包装（委托给 {@link MetricsHelper}）。
     */
    private static <T> T recordSerialize(MetricsHelper.ThrowingSupplier<T> supplier) {
        return MetricsHelper.recordSerialize(supplier, YdszJson.getMetricsCallback());
    }

    /**
     * 反序列化操作的指标监控包装（委托给 {@link MetricsHelper}）。
     */
    private static <T> T recordDeserialize(MetricsHelper.ThrowingSupplier<T> supplier) {
        return MetricsHelper.recordDeserialize(supplier, YdszJson.getMetricsCallback());
    }

    /**
     * 获取默认 Mapper 实例。
     *
     * @return 默认单例实例
     */
    public static YdszJsonMapper getDefault() {
        return DEFAULT;
    }

    // ==================== Builder API ====================

    /**
     * 创建 Builder 实例。
     *
     * @return Builder 实例
     * @since 1.4.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * YdszJsonMapper 链式 Builder（对标 Jackson ObjectMapper.builder()）。
     *
     * <p>使用示例：</p>
     * <pre>
     * YdszJsonMapper mapper = YdszJsonMapper.builder()
     *     .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
     *     .dateFormat("yyyy-MM-dd HH:mm:ss")
     *     .writeNulls(true)
     *     .useBigDecimal(true)
     *     .build();
     * </pre>
     *
     * @since 1.4.0
     */
    public static final class Builder {

        private final YdszJsonConfig config = YdszJsonConfig.copyOf(YdszJsonConfig.getInstance());

        private Builder() {
        }

        public Builder namingStrategy(PropertyNamingStrategy strategy) {
            config.setNamingStrategy(strategy);
            return this;
        }

        public Builder dateFormat(String dateFormat) {
            config.setDateFormat(dateFormat);
            return this;
        }

        public Builder writeNulls(boolean writeNulls) {
            config.setWriteNulls(writeNulls);
            return this;
        }

        public Builder prettyPrint(boolean prettyPrint) {
            config.setPrettyPrint(prettyPrint);
            return this;
        }

        public Builder circularReferenceStrategy(YdszJsonConfig.CircularReferenceStrategy strategy) {
            config.setCircularReferenceStrategy(strategy);
            return this;
        }

        public Builder serializeEnumUsingOrdinal(boolean ordinal) {
            config.setSerializeEnumUsingOrdinal(ordinal);
            return this;
        }

        public Builder useBigDecimal(boolean useBigDecimal) {
            config.setUseBigDecimal(useBigDecimal);
            return this;
        }

        public Builder wrapRootValue(boolean wrapRootValue) {
            config.setWrapRootValue(wrapRootValue);
            return this;
        }

        public Builder failOnError(boolean failOnError) {
            config.setFailOnError(failOnError);
            return this;
        }

        public Builder maxJsonSize(long maxJsonSize) {
            config.setMaxJsonSize(maxJsonSize);
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            config.setMaxDepth(maxDepth);
            return this;
        }

        public YdszJsonMapper build() {
            return new YdszJsonMapper(config);
        }
    }
}
