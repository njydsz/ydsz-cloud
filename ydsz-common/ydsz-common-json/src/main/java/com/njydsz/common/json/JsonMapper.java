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

import com.njydsz.common.json.internal.JsonConfig;
import com.njydsz.common.json.exception.JsonException;
import com.njydsz.common.json.metric.MetricsHelper;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.parser.JsonParserUtil;
import com.njydsz.common.json.pointer.JsonPointer;
import com.njydsz.common.json.provider.DeserializationProvider;
import com.njydsz.common.json.provider.SerializationProvider;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.NullNode;
import com.njydsz.common.json.tree.TreeConverter;
import com.njydsz.common.json.type.JsonType;
import com.njydsz.common.json.type.TypeFactory;

/**
 * YdszJson 实例化 Mapper（对标 Jackson ObjectMapper）
 *
 * <p>提供实例化的 JSON 序列化/反序列化能力，每个实例持有独立的 {@link JsonConfig} 配置副本，
 * 允许在同一 JVM 中创建多个不同配置的 Mapper 实例，互不干扰。
 *
 * <p><b>与 {@link YdszJson} 的关系：</b></p>
 * <ul>
 *   <li>{@code YdszJson} 静态方法委托给内部默认 {@code JsonMapper} 实例，保持向后兼容</li>
 *   <li>需要独立配置的场景应创建新的 {@code JsonMapper} 实例</li>
 *   <li>{@link #copy()} 方法创建配置副本，修改不影响原实例</li>
 *   <li>所有操作均纳入 {@link JsonMetricsCallback} 指标监控（与 YdszJson 静态方法一致）</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 创建默认 Mapper
 * JsonMapper mapper = new JsonMapper();
 *
 * // 通过 Builder 创建独立配置的 Mapper（推荐）
 * JsonMapper prettyMapper = JsonMapper.builder()
 *     .writeNulls(true)
 *     .prettyPrint(true)
 *     .build();
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
 * <p><b>多配置场景规范（R9）：</b>当需要与全局配置不同的序列化策略时（如对外 API 使用
 * SNAKE_CASE 命名、内部 API 使用 LOWER_CAMEL_CASE；或金融场景启用 useBigDecimal），
 * 必须通过 {@code JsonMapper.builder()} 创建独立配置的 Mapper 实例，
 * 避免线程间配置污染。Mapper 实例创建后为只读配置，线程安全，可作为 Spring Bean 单例注入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class JsonMapper {

    /** 默认单例实例（YdszJson 静态方法委托给此实例） */
    private static final JsonMapper DEFAULT = new JsonMapper();

    /** 此 Mapper 实例的配置（独立副本） */
    private final JsonConfig config;

    /**
     * 创建默认配置的 Mapper 实例。
     */
    public JsonMapper() {
        this(JsonConfig.getInstance());
    }

    /**
     * 创建指定配置的 Mapper 实例。
     *
     * @param config 配置（会被复制为独立副本）
     */
    public JsonMapper(JsonConfig config) {
        this.config = JsonConfig.copyOf(config);
    }

    /**
     * 获取此 Mapper 的配置对象（可直接修改，不影响全局配置）。
     *
     * <p>修改配置后，下次序列化会自动重新应用到 ThreadLocal，无需额外通知。</p>
     *
     * @return 配置对象
     */
    public JsonConfig getConfig() {
        return config;
    }

    /**
     * 通知 Mapper 配置已变更（兼容保留，当前实现为 no-op）。
     *
     * <p>历史上此方法用于重置 {@code configApplied} 优化标志。该优化因在共享 Mapper
     * 场景下跨线程误共享 ThreadLocal 状态而被移除——现在每次序列化都会通过
     * {@link SerializationProvider.ThreadLocalSnapshot} 显式保存/恢复配置，保证
     * 多线程共享同一 {@code JsonMapper} 实例时配置正确隔离。</p>
     *
     * @since 1.0.0
     */
    public void configChanged() {
        // no-op：每次序列化都会重新 apply 配置，无需显式通知
    }

    /**
     * 创建配置副本（独立实例，修改不影响原 Mapper）。
     *
     * @return 新的 Mapper 实例
     */
    public JsonMapper copy() {
        return new JsonMapper(this.config);
    }

    // ==================== 序列化方法 ====================

    /**
     * 将此 Mapper 的配置应用到当前线程的 ThreadLocal，返回需要 restore 的快照。
     *
     * <p>每次序列化都执行 save/apply/restore，确保多线程共享同一 {@code JsonMapper}
     * 实例时各线程的 ThreadLocal 配置互不污染。这与 Jackson
     * {@code ObjectMapper}（配置不可变 + 显式传递）的线程安全模型在 ThreadLocal
     * 实现下的等价做法。</p>
     *
     * @return ThreadLocalSnapshot（不为 null，需在 finally 中 restore）
     */
    private SerializationProvider.ThreadLocalSnapshot applyConfigIfNeeded() {
        SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();
        config.apply();
        return snapshot;
    }

    /**
     * 恢复配置快照（ThreadLocal 序列化参数）。
     */
    private void restoreConfig(SerializationProvider.ThreadLocalSnapshot snapshot) {
        if (snapshot != null) {
            snapshot.restore();
        }
    }

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
        SerializationProvider.ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            return recordSerialize(() -> SerializationProvider.serialize(obj));
        } finally {
            if (snapshot != null) restoreConfig(snapshot);
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
            SerializationProvider.ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
            try {
                return recordSerialize(() -> SerializationProvider.format(obj));
            } finally {
                if (snapshot != null) restoreConfig(snapshot);
            }
        }
        return toJson(obj);
    }

    /**
     * 序列化对象为 JSON 字符串（带视图过滤）。
     *
     * <p>根据 @JsonView 注解过滤字段，仅输出指定视图下可见的字段。</p>
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
        SerializationProvider.ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            return recordSerialize(() -> SerializationProvider.serializeWithView(obj, viewClass));
        } finally {
            if (snapshot != null) restoreConfig(snapshot);
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
        SerializationProvider.ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            return recordSerialize(() -> SerializationProvider.serializeToBytes(obj));
        } finally {
            if (snapshot != null) restoreConfig(snapshot);
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
            throw new JsonException("Failed to write to OutputStream", e);
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
            throw new JsonException("Failed to write to Writer", e);
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
     * 反序列化 JSON 字符串为泛型类型（JsonType）。
     *
     * @param json    JSON 字符串
     * @param typeRef 类型引用
     * @param <T>     类型参数
     * @return 反序列化后的对象
     */
    public <T> T toObject(String json, JsonType<T> typeRef) {
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
    public <T> T fromJson(String json, JsonType<T> typeRef) {
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
            throw new JsonException(
                "JSON size exceeds limit: " + bytes.length + " > " + maxSize);
        }
        SerializationProvider.ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            return recordDeserialize(() -> DeserializationProvider.deserialize(bytes, clazz));
        } finally {
            if (snapshot != null) restoreConfig(snapshot);
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
            throw new JsonException("Failed to read from InputStream", e);
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
    public <T> T readValue(InputStream in, JsonType<T> typeRef) {
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
                throw new JsonException(
                    "JSON size exceeds limit: " + bytes.length + " > " + maxSize);
            }
            SerializationProvider.ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
            try {
                return recordDeserialize(() -> DeserializationProvider.deserialize(bytes, typeRef.getType()));
            } finally {
                if (snapshot != null) restoreConfig(snapshot);
            }
        } catch (Exception e) {
            if (e instanceof JsonException) throw (JsonException) e;
            throw new JsonException("Failed to read from InputStream", e);
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
        // 复用 TypeFactory 缓存的参数化类型，避免每次调用新建匿名 ParameterizedType
        Object result = DeserializationProvider.deserialize(json,
            TypeFactory.getInstance().constructCollectionType(List.class, elementClass));
        if (result instanceof List<?> list) {
            // 优化：直接 unchecked cast 返回，消除 O(n) 拷贝
            @SuppressWarnings("unchecked")
            List<T> typedList = (List<T>) list;
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
        Object parsed = JsonParserUtil.parse(json);
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
        if (obj == null) {
            return NullNode.getInstance();
        }
        // JsonNode 直接返回，免序列化
        if (obj instanceof JsonNode) {
            return (JsonNode) obj;
        }
        // Map/List/简单值 Wrapper 直接树转换，免 String 中转
        if (obj instanceof Map || obj instanceof List
                || obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            return TreeConverter.convertToJsonNode(obj);
        }
        String json = toJson(obj);
        return readTree(json);
    }

    // ==================== JSONPointer API ====================

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
     * @since 1.0.0
     */
    public <T> T convertValue(Object fromValue, Class<T> toValueType) {
        if (fromValue == null) {
            return null;
        }
        // 同类型或子类型直接返回，避免序列化/反序列化开销
        if (toValueType.isInstance(fromValue)) {
            return toValueType.cast(fromValue);
        }
        // JsonNode → POJO 走 treeToValue 管道（仅需反序列化，免字符中转）
        if (fromValue instanceof JsonNode) {
            return treeToValue((JsonNode) fromValue, toValueType);
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
     * @since 1.0.0
     */
    public <T> T convertValue(Object fromValue, JsonType<T> toValueTypeRef) {
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
     * @since 1.0.0
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
     * @since 1.0.0
     */
    public String writeValueAsString(Object obj) {
        return toJson(obj);
    }

    /**
     * 序列化对象为 UTF-8 字节数组（对标 Jackson ObjectMapper.writeValueAsBytes）。
     *
     * @param obj 要序列化的对象
     * @return UTF-8 编码的字节数组
     * @since 1.0.0
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
     * @since 1.0.0
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
     * @since 1.0.0
     */
    public String format(Object obj) {
        return toJson(obj, true);
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
        SerializationProvider.ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            Set<String> previous = SerializationProvider.getExcludedFields();
            SerializationProvider.setExcludedFields(excludedFieldNames);
            try {
                return recordSerialize(() -> SerializationProvider.serialize(obj));
            } finally {
                SerializationProvider.setExcludedFields(previous);
            }
        } finally {
            if (snapshot != null) restoreConfig(snapshot);
        }
    }

    // ==================== 内部方法 ====================

    private void validateJsonSize(String json) {
        long maxSize = config.getMaxJsonSize();
        if (json.length() > maxSize) {
            throw new JsonException(
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
    public static JsonMapper getDefault() {
        return DEFAULT;
    }

    // ==================== Builder API ====================

    /**
     * 创建 Builder 实例。
     *
     * @return Builder 实例
     * @since 1.0.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * JsonMapper 链式 Builder（对标 Jackson ObjectMapper.builder()）。
     *
     * <p>使用示例：</p>
     * <pre>
     * JsonMapper mapper = JsonMapper.builder()
     *     .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
     *     .dateFormat("yyyy-MM-dd HH:mm:ss")
     *     .writeNulls(true)
     *     .useBigDecimal(true)
     *     .build();
     * </pre>
     *
     * @since 1.0.0
     */
    public static final class Builder {

        private PropertyNamingStrategy namingStrategy = JsonConfig.getInstance().getNamingStrategy();
        private JsonConfig.CircularReferenceStrategy circularReferenceStrategy = JsonConfig.getInstance().getCircularReferenceStrategy();
        private boolean writeNulls = JsonConfig.getInstance().isWriteNulls();
        private String dateFormat = JsonConfig.getInstance().getDateFormat();
        private boolean serializeEnumUsingOrdinal = JsonConfig.getInstance().isSerializeEnumUsingOrdinal();
        private boolean prettyPrint = JsonConfig.getInstance().isPrettyPrint();
        private boolean failOnError = JsonConfig.getInstance().isFailOnError();
        private boolean useBigDecimal = JsonConfig.getInstance().isUseBigDecimal();
        private boolean wrapRootValue = JsonConfig.getInstance().isWrapRootValue();
        private long maxJsonSize = JsonConfig.getInstance().getMaxJsonSize();
        private int maxDepth = JsonConfig.getInstance().getMaxDepth();

        private Builder() {
        }

        /**
         * 设置字段命名策略。
         *
         * @param strategy 命名策略（如 SNAKE_CASE / LOWER_CAMEL_CASE），不可为 {@code null}
         */
        public Builder namingStrategy(PropertyNamingStrategy strategy) {
            this.namingStrategy = strategy;
            return this;
        }

        /**
         * 设置日期类型序列化格式。
         *
         * @param dateFormat SimpleDateFormat 模式串；空串或 {@code null} 表示使用默认格式
         */
        public Builder dateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
            return this;
        }

        /**
         * 设置是否输出 null 字段。
         *
         * @param writeNulls {@code true} 保留 null 字段，{@code false} 跳过 null 字段
         */
        public Builder writeNulls(boolean writeNulls) {
            this.writeNulls = writeNulls;
            return this;
        }

        /**
         * 设置是否格式化（缩进）输出。
         *
         * @param prettyPrint {@code true} 启用美化输出
         */
        public Builder prettyPrint(boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        /**
         * 设置循环引用处理策略。
         *
         * @param strategy {@code REF} 输出引用路径 / {@code IGNORE} 忽略 / {@code ERROR} 抛异常
         */
        public Builder circularReferenceStrategy(JsonConfig.CircularReferenceStrategy strategy) {
            this.circularReferenceStrategy = strategy;
            return this;
        }

        /**
         * 设置枚举序列化方式。
         *
         * @param ordinal {@code true} 用枚举 ordinal 序号，{@code false} 用 name 名称
         */
        public Builder serializeEnumUsingOrdinal(boolean ordinal) {
            this.serializeEnumUsingOrdinal = ordinal;
            return this;
        }

        /**
         * 设置是否将浮点数解析为 BigDecimal 以保留精度。
         *
         * @param useBigDecimal {@code true} 启用（金融等高精度场景推荐）
         */
        public Builder useBigDecimal(boolean useBigDecimal) {
            this.useBigDecimal = useBigDecimal;
            return this;
        }

        /**
         * 设置是否启用根名称包裹。
         *
         * @param wrapRootValue {@code true} 启用（配合 {@code @JsonRootName} 注解）
         */
        public Builder wrapRootValue(boolean wrapRootValue) {
            this.wrapRootValue = wrapRootValue;
            return this;
        }

        /**
         * 设置序列化遇错时是否抛出异常。
         *
         * @param failOnError {@code true} 抛异常，{@code false} 降级为容错输出
         */
        public Builder failOnError(boolean failOnError) {
            this.failOnError = failOnError;
            return this;
        }

        /**
         * 设置单次 JSON 处理的最大字节数上限。
         *
         * @param maxJsonSize 上限（字节），超过将抛出 {@link JsonException}
         */
        public Builder maxJsonSize(long maxJsonSize) {
            this.maxJsonSize = maxJsonSize;
            return this;
        }

        /**
         * 设置序列化/反序列化的最大嵌套深度。
         *
         * @param maxDepth 最大深度，防止过深结构导致栈溢出
         */
        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        /**
         * 构建最终的 {@link JsonMapper} 实例。
         *
         * <p>将 Builder 上累积的全部配置项转换为 {@link JsonConfig}，
         * 构造 {@code JsonMapper} 并触发 {@code configChanged()} 使新配置生效
         * （例如清空 Bean 序列化缓存、刷新命名策略映射等）。</p>
         *
         * @return 已应用全部构建配置的 JsonMapper 实例
         */
        public JsonMapper build() {
            JsonConfig config = JsonConfig.builder()
                .namingStrategy(namingStrategy)
                .circularReferenceStrategy(circularReferenceStrategy)
                .writeNulls(writeNulls)
                .dateFormat(dateFormat)
                .serializeEnumUsingOrdinal(serializeEnumUsingOrdinal)
                .prettyPrint(prettyPrint)
                .failOnError(failOnError)
                .useBigDecimal(useBigDecimal)
                .wrapRootValue(wrapRootValue)
                .maxJsonSize(maxJsonSize)
                .maxDepth(maxDepth)
                .build();
            JsonMapper mapper = new JsonMapper(config);
            mapper.configChanged();
            return mapper;
        }
    }
}
