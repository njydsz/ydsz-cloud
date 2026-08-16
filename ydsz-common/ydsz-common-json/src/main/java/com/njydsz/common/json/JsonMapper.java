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
import java.util.Objects;
import java.util.Set;
import com.njydsz.common.json.exception.JsonException;
import com.njydsz.common.json.internal.JsonConfig;
import com.njydsz.common.json.internal.JsonRuntimeConfig;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.parser.JsonParserUtil;
import com.njydsz.common.json.provider.DeserializationProvider;
import com.njydsz.common.json.provider.FieldMetadataLoader;
import com.njydsz.common.json.provider.SerializationProvider;
import com.njydsz.common.json.provider.SerializationProvider.SerializationContext;
import com.njydsz.common.json.provider.ThreadLocalSnapshot;
import com.njydsz.common.json.reader.JSONReader;
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

    /** 此 Mapper 实例的配置（独立副本，不可变） */
    private final JsonConfig config;

    /** 此 Mapper 实例的预计算运行时配置（从 config 派生，不可变快照） */
    private final JsonRuntimeConfig runtimeConfig;

    /**
     * 创建默认配置的 Mapper 实例。
     */
    public JsonMapper() {
        this(JsonConfig.getInstance());
    }

    /**
     * 创建指定配置的 Mapper 实例。
     *
     * @param config 配置（会被复制为独立副本，并生成预计算运行时配置）
     */
    public JsonMapper(JsonConfig config) {
        this.config = JsonConfig.copyOf(config);
        this.runtimeConfig = JsonRuntimeConfig.from(this.config);
    }

    /**
     * 创建指定运行时配置的 Mapper 实例（内部使用，跳过 JsonConfig 转换）。
     *
     * @param config       源配置对象
     * @param runtimeConfig 预计算运行时配置
     */
    private JsonMapper(JsonConfig config, JsonRuntimeConfig runtimeConfig) {
        this.config = config;
        this.runtimeConfig = runtimeConfig;
    }

    /**
     * 获取此 Mapper 的配置对象（只读）。
     *
     * <p>返回的 JsonConfig 为不可变实例，修改配置请通过 Builder 重新构建新 JsonMapper。</p>
     *
     * @return 配置对象（不可变）
     */
    public JsonConfig getConfig() {
        return config;
    }

    /**
     * 获取此 Mapper 的预计算运行时配置（不可变快照）。
     *
     * <p>运行时配置是从 JsonConfig 派生的预计算对象，所有字段都是 final，
     * 读取无锁无 ThreadLocal 开销。此配置作为序列化/反序列化操作的
     * 配置传递源替代 ThreadLocal 配置字段。</p>
     *
     * @return 运行时配置（只读）
     * @since 1.1.0
     */
    public JsonRuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    /**
     * 通知 Mapper 配置已变更（兼容保留，当前实现为 no-op）。
     *
     * <p>历史上此方法用于重置 {@code configApplied} 优化标志。该优化因在共享 Mapper
     * 场景下跨线程误共享 ThreadLocal 状态而被移除——现在每次序列化都会通过
     * {@link ThreadLocalSnapshot} 显式保存/恢复配置，保证
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
     * <p>新的 Mapper 实例共享相同的运行时配置（不可变，安全共享），
     * 无需重新预计算。</p>
     *
     * @return 新的 Mapper 实例
     */
    public JsonMapper copy() {
        return new JsonMapper(this.config, this.runtimeConfig);
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
     * <p>P0-1 优化：若当前线程的 ThreadLocal 状态已与本 Mapper 的
     * {@link JsonRuntimeConfig} 完全一致（最常见场景：默认配置连续调用、
     * 同一 Mapper 在同线程的重复调用），apply 是语义空操作，直接返回 null
     * 跳过 {@code new ThreadLocalSnapshot()} 的热路径分配与 9 字段 save/restore，
     * 消除默认调用路径的对象分配。调用方均已判空（{@code if (snapshot != null)}），
     * 返回 null 不影响 finally 语义。</p>
     *
     * @return ThreadLocalSnapshot；当前线程已持有本配置时返回 null（无需 restore）
     */
    private ThreadLocalSnapshot applyConfigIfNeeded() {
        if (isRuntimeConfigActive()) {
            // P0-1：配置已生效，apply/restore 为空操作，跳过快照分配
            return null;
        }
        ThreadLocalSnapshot snapshot = new ThreadLocalSnapshot();
        // 1. 预计算配置快速填充当前线程的 SerializationContext
        applyRuntimeConfig();
        // 2. 全局组件（JSONReader maxDepth 等）由 JsonConfig.install() 在配置变更时统一传播；
        //    实例级深度隔离通过 applyRuntimeConfig 中的线程级覆盖实现（P0-3）。
        return snapshot;
    }

    /**
     * 判断当前线程的 ThreadLocal 状态是否已与本 Mapper 的运行时配置完全一致。
     *
     * <p>P0-1：一致时 {@link #applyConfigIfNeeded()} 可安全跳过快照创建。
     * 覆盖 SerializationContext 配置字段、命名策略、深度覆盖三类状态
     * （与 {@link ThreadLocalSnapshot} 保存的字段集合对齐，除 useBigDecimal 外
     * ——useBigDecimal 由 JsonParserUtil 独立管理，仅在显式配置差异时变化，
     * 一致性判定纳入其值比较）。</p>
     *
     * @return true 表示当前线程已持有本 Mapper 的全部配置
     * @since 1.2.3
     */
    private boolean isRuntimeConfigActive() {
        SerializationContext ctx = SerializationContext.CONTEXT.get();
        return ctx.writeNulls == runtimeConfig.writeNulls()
            && ctx.prettyPrint == runtimeConfig.prettyPrint()
            && Objects.equals(ctx.circularRefStrategy, runtimeConfig.circularRefStrategy())
            && ctx.serializeEnumUsingOrdinal == runtimeConfig.serializeEnumUsingOrdinal()
            && Objects.equals(ctx.dateFormat, runtimeConfig.dateFormat())
            && ctx.failOnError == runtimeConfig.failOnError()
            && FieldMetadataLoader.NAMING_STRATEGY.get() == runtimeConfig.namingStrategy()
            && JsonParserUtil.isUseBigDecimal() == runtimeConfig.useBigDecimal()
            && depthOverridesMatch();
    }

    /**
     * 判断当前线程的深度覆盖状态是否与 apply 将产生的状态一致。
     *
     * <p>P0-3 语义分层：</p>
     * <ul>
     *   <li><b>自定义深度的 Mapper</b>（maxDepth/maxGenericDepth 与已安装全局配置不同）：
     *       调用期间设置线程级覆盖，实现实例隔离；已生效判定要求覆盖值等于本 Mapper 配置</li>
     *   <li><b>继承全局深度的 Mapper</b>：不设置覆盖，回退静态全局值——保留
     *       {@code JSONReader.setMaxDepth()} 运行期临时调整的兼容语义
     *       （对照 YdszJsonSecurityTest.configurableDepthLimit）；已生效判定要求覆盖为空</li>
     * </ul>
     *
     * @return true 表示当前线程深度状态已与目标一致
     * @since 1.2.3
     */
    private boolean depthOverridesMatch() {
        if (isCustomDepth()) {
            return Objects.equals(JSONReader.getCallMaxDepthOverride(), runtimeConfig.maxDepth())
                && Objects.equals(JSONReader.getCallMaxGenericDepthOverride(), runtimeConfig.maxGenericDepth())
                && Objects.equals(JsonParserUtil.getCallParseDepthOverride(), runtimeConfig.maxDepth());
        }
        return JSONReader.getCallMaxDepthOverride() == null
            && JSONReader.getCallMaxGenericDepthOverride() == null
            && JsonParserUtil.getCallParseDepthOverride() == null;
    }

    /**
     * 判断本 Mapper 是否显式自定义了深度配置（区别于继承已安装全局配置）。
     *
     * <p>以 {@link JsonConfig#getInstance()}（已安装配置）为基准而非
     * {@code JSONReader.getMaxDepth()} 静态值——后者可被运行期临时调整，
     * 临时调整不应使继承型 Mapper 被误判为自定义型。</p>
     *
     * @return true 表示本 Mapper 深度配置与全局已安装配置不同
     * @since 1.2.3
     */
    private boolean isCustomDepth() {
        JsonConfig installed = JsonConfig.getInstance();
        return runtimeConfig.maxDepth() != installed.getMaxDepth()
            || runtimeConfig.maxGenericDepth() != installed.getMaxGenericDepth();
    }

    /**
     * 将预计算运行时配置快速填充到当前线程的 ThreadLocal 上下文中。
     *
     * <p>目标是最终消除 ThreadLocal 依赖（P0-5/P0-6），当前作为过渡方案，
     * 利用已预计算的 runtimeConfig 避免重复从 JsonConfig 读取。
     *
     * @since 1.1.0
     */
    private void applyRuntimeConfig() {
        SerializationContext ctx = SerializationContext.CONTEXT.get();
        // 使用预计算运行时配置快速填充配置字段
        ctx.writeNulls = runtimeConfig.writeNulls();
        ctx.prettyPrint = runtimeConfig.prettyPrint();
        ctx.circularRefStrategy = runtimeConfig.circularRefStrategy();
        ctx.serializeEnumUsingOrdinal = runtimeConfig.serializeEnumUsingOrdinal();
        ctx.dateFormat = runtimeConfig.dateFormat();
        ctx.failOnError = runtimeConfig.failOnError();
        // namingStrategy 设置到独立 ThreadLocal（与 BeanSerializerCache 二级 Key 对齐）
        FieldMetadataLoader.NAMING_STRATEGY.set(runtimeConfig.namingStrategy());
        // P0-3：实例级精度模式隔离——此前仅 JsonConfig.install() 全局传播，
        // mapper 级 useBigDecimal 配置实际不生效，多实例场景互相覆盖
        JsonParserUtil.setUseBigDecimal(runtimeConfig.useBigDecimal());
        // P0-3：实例级深度隔离——仅对显式自定义深度的 Mapper 设置线程级覆盖；
        // 继承全局深度的 Mapper 回退静态全局值，保留运行期临时调整兼容语义
        // （详见 depthOverridesMatch 的语义分层说明）
        if (isCustomDepth()) {
            JSONReader.setCallDepthOverride(runtimeConfig.maxDepth(), runtimeConfig.maxGenericDepth());
            JsonParserUtil.setCallParseDepthOverride(runtimeConfig.maxDepth());
        } else {
            // 清理可能残留的其他 Mapper 覆盖值（快照 restore 负责恢复原值）
            JSONReader.setCallDepthOverride(null, null);
            JsonParserUtil.setCallParseDepthOverride(null);
        }
    }

    /**
     * 使用预计算运行时配置初始化当前线程的 SerializationContext（完整替换模式）。
     *
     * <p>通过 {@link SerializationContext#from(JsonRuntimeConfig)} 一次性创建并关联上下文，
     * 避免逐个字段赋值。调用方需负责清理（使用现有清理机制）。</p>
     *
     * @since 1.1.0
     */
    static void installSerializationContext(JsonRuntimeConfig runtimeConfig) {
        SerializationContext ctx = SerializationContext.from(runtimeConfig);
        SerializationContext.CONTEXT.set(ctx);
    }

    /**
     * 恢复配置快照（ThreadLocal 序列化参数）。
     */
    private void restoreConfig(ThreadLocalSnapshot snapshot) {
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
        ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            return SerializationProvider.serialize(obj);
        } finally {
            if (snapshot != null) {
                restoreConfig(snapshot);
            }
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
            ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
            try {
                return SerializationProvider.format(obj);
            } finally {
                if (snapshot != null) {
                    restoreConfig(snapshot);
                }
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
        ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            return SerializationProvider.serializeWithView(obj, viewClass);
        } finally {
            if (snapshot != null) {
                restoreConfig(snapshot);
            }
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
        ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            return SerializationProvider.serializeToBytes(obj);
        } finally {
            if (snapshot != null) {
                restoreConfig(snapshot);
            }
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
        ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            return DeserializationProvider.deserialize(json, clazz);
        } finally {
            if (snapshot != null) {
                restoreConfig(snapshot);
            }
        }
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
        ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            return DeserializationProvider.deserialize(json, type);
        } finally {
            if (snapshot != null) {
                restoreConfig(snapshot);
            }
        }
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
        ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            return DeserializationProvider.deserialize(json, typeRef.getType());
        } finally {
            if (snapshot != null) {
                restoreConfig(snapshot);
            }
        }
    }

    /**
     * 反序列化 UTF-8 字节数组为指定类型。
     *
     * @param bytes JSON 字节数组（UTF-8 编码）
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return 反序列化后的对象
     * @since 1.1.0
     */
    public <T> T toObject(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        validateJsonSizeBytes(bytes.length);
        ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            return DeserializationProvider.deserialize(bytes, clazz);
        } finally {
            if (snapshot != null) {
                restoreConfig(snapshot);
            }
        }
    }

    /**
     * 反序列化 UTF-8 字节数组为泛型类型。
     *
     * @param bytes JSON 字节数组（UTF-8 编码）
     * @param type  目标类型
     * @param <T>   类型参数
     * @return 反序列化后的对象
     * @since 1.1.0
     */
    @SuppressWarnings("unchecked")
    public <T> T toObject(byte[] bytes, Type type) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        validateJsonSizeBytes(bytes.length);
        ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            return (T) DeserializationProvider.deserializeToObject(bytes, type);
        } finally {
            if (snapshot != null) {
                restoreConfig(snapshot);
            }
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
            return toObject(bytes, clazz);
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
            ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
            try {
                return DeserializationProvider.deserialize(bytes, typeRef.getType());
            } finally {
                if (snapshot != null) {
                    restoreConfig(snapshot);
                }
            }
        } catch (Exception e) {
            if (e instanceof JsonException) {
                throw (JsonException) e;
            }
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
        ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            Object result = DeserializationProvider.deserialize(json, Map.class);
            if (result instanceof Map<?, ?> map) {
                Map<String, Object> typedMap = new LinkedHashMap<>(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    typedMap.put((String) entry.getKey(), entry.getValue());
                }
                return typedMap;
            }
            return new LinkedHashMap<String, Object>();
        } finally {
            if (snapshot != null) {
                restoreConfig(snapshot);
            }
        }
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
        ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
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
        } finally {
            if (snapshot != null) {
                restoreConfig(snapshot);
            }
        }
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
     * <p>基于 {@link #readTree} 解析后按 RFC 6901 路径定位（等价于 {@link JsonNode#path}）。
     *
     * @param json    JSON 字符串
     * @param pointer JSON Pointer 路径
     * @return 指针指向的值
     * @since 1.0.0
     */
    public Object getByPointer(String json, String pointer) {
        if (json == null || pointer == null || pointer.isBlank()) {
            return null;
        }
        try {
            JsonNode node = readTree(json);
            JsonNode target = node.path(pointer.startsWith("/") ? pointer.substring(1) : pointer);
            if (target.isMissing() || target.isNull()) {
                return null;
            }
            return target.asValue();
        } catch (Exception e) {
            return null;
        }
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
        // F-2 直绑：JsonNode 源走 treeToValue，跳过 toJson 字符串中转
        if (fromValue instanceof JsonNode node) {
            return treeToValue(node, toValueTypeRef);
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
        // F-2 直绑优化：标量 / Map / List / Object 目标类型直接从树取值，
        // 跳过"树 → 字符串 → 再解析"的两次结构转换（对标 Jackson TokenBuffer）
        if (clazz == String.class) {
            return clazz.cast(node.asText());
        }
        if (clazz == int.class || clazz == Integer.class) {
            @SuppressWarnings("unchecked")
            T result = (T) Integer.valueOf(node.asInt());
            return result;
        }
        if (clazz == long.class || clazz == Long.class) {
            @SuppressWarnings("unchecked")
            T result = (T) Long.valueOf(node.asLong());
            return result;
        }
        if (clazz == double.class || clazz == Double.class) {
            @SuppressWarnings("unchecked")
            T result = (T) Double.valueOf(node.asDouble());
            return result;
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            @SuppressWarnings("unchecked")
            T result = (T) Boolean.valueOf(node.asBoolean());
            return result;
        }
        if (clazz == Map.class || clazz == List.class || clazz == Object.class) {
            @SuppressWarnings("unchecked")
            T result = (T) TreeConverter.convertToJavaObject(node);
            return result;
        }
        // Bean 目标类型：走现有字符串管道（Map 版 BeanReader 直绑为 F-2 二期）
        String json = node.toString();
        return toObject(json, clazz);
    }

    /**
     * 将 JsonNode 树转换为指定泛型类型的对象（对标 Jackson ObjectMapper.treeToValue）。
     *
     * <p>目标为具体类时走 {@link #treeToValue(JsonNode, Class)} 直绑路径；
     * 泛型类型（如 {@code List<User>}）仍走字符串管道。</p>
     *
     * @param node     JsonNode 树
     * @param typeRef  目标类型引用（可为泛型）
     * @param <T>      目标类型参数
     * @return 转换后的对象
     * @since 1.2.2
     */
    public <T> T treeToValue(JsonNode node, JsonType<T> typeRef) {
        if (node == null) {
            return null;
        }
        if (typeRef.getType() instanceof Class<?> clazz) {
            @SuppressWarnings("unchecked")
            T direct = (T) treeToValue(node, clazz);
            return direct;
        }
        String json = node.toString();
        return toObject(json, typeRef);
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
        ThreadLocalSnapshot snapshot = applyConfigIfNeeded();
        try {
            Set<String> previous = SerializationProvider.getExcludedFields();
            SerializationProvider.setExcludedFields(excludedFieldNames);
            try {
                return SerializationProvider.serialize(obj);
            } finally {
                SerializationProvider.setExcludedFields(previous);
            }
        } finally {
            if (snapshot != null) {
                restoreConfig(snapshot);
            }
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

    /**
     * 校验 JSON 字节数组大小是否超过全局限制。
     *
     * @param byteLength JSON 字节数组长度（字节）
     */
    private void validateJsonSizeBytes(int byteLength) {
        long maxSize = config.getMaxJsonSize();
        if (byteLength > maxSize) {
            throw new JsonException(
                "JSON size exceeds limit: " + byteLength + " > " + maxSize);
        }
    }

    /**
     * 获取默认 Mapper 实例。
     *
     * <p>与 {@link YdszJson#getDefaultMapper()} 同源：{@link JsonConfig#install(JsonConfig)}
     * 热更新后，此入口返回的实例即携带最新配置（P0-2 修复）。</p>
     *
     * @return 当前默认 Mapper 实例，永不为 null
     */
    public static JsonMapper getDefault() {
        return YdszJson.getDefaultMapper();
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

        /** 内部委托给 JsonConfig.Builder，消除双重 Builder 字段重复（1.2.1） */
        private final JsonConfig.Builder configBuilder = JsonConfig.builder();

        private Builder() {
        }

        /**
         * 从现有 JsonMapper 创建 Builder（创建修改版的便捷方式）。
         *
         * <p>新的 Builder 预填充源 Mapper 的全部配置，可在此基础上修改部分配置后
         * {@code build()} 创建新 Mapper。这是 {@link JsonMapper#copy()} 的增强版，
         * 支持"复制并修改"模式：</p>
         * <pre>
         * // 将全局 Mapper 复制并调整为 SNAKE_CASE 命名
         * JsonMapper snakeMapper = JsonMapper.builder()
         *     .from(defaultMapper)
         *     .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
         *     .build();
         * </pre>
         *
         * @param mapper 源 JsonMapper 实例（不可为 null）
         * @return 预填充源 Mapper 配置的 Builder 实例
         * @since 1.1.0
         */
        public Builder from(JsonMapper mapper) {
            if (mapper == null) {
                throw new IllegalArgumentException("Builder.from: mapper must not be null");
            }
            configBuilder.from(mapper.getConfig());
            return this;
        }

        /**
         * 设置字段命名策略。
         *
         * @param strategy 命名策略（如 SNAKE_CASE / LOWER_CAMEL_CASE），不可为 {@code null}
         */
        public Builder namingStrategy(PropertyNamingStrategy strategy) {
            configBuilder.namingStrategy(strategy);
            return this;
        }

        /**
         * 设置日期类型序列化格式。
         *
         * @param dateFormat SimpleDateFormat 模式串；空串或 {@code null} 表示使用默认格式
         */
        public Builder dateFormat(String dateFormat) {
            configBuilder.dateFormat(dateFormat);
            return this;
        }

        /**
         * 设置是否输出 null 字段。
         *
         * @param writeNulls {@code true} 保留 null 字段，{@code false} 跳过 null 字段
         */
        public Builder writeNulls(boolean writeNulls) {
            configBuilder.writeNulls(writeNulls);
            return this;
        }

        /**
         * 设置是否格式化（缩进）输出。
         *
         * @param prettyPrint {@code true} 启用美化输出
         */
        public Builder prettyPrint(boolean prettyPrint) {
            configBuilder.prettyPrint(prettyPrint);
            return this;
        }

        /**
         * 设置循环引用处理策略。
         *
         * @param strategy {@code REF} 输出引用路径 / {@code IGNORE} 忽略 / {@code ERROR} 抛异常
         */
        public Builder circularReferenceStrategy(JsonConfig.CircularReferenceStrategy strategy) {
            configBuilder.circularReferenceStrategy(strategy);
            return this;
        }

        /**
         * 设置枚举序列化方式。
         *
         * @param ordinal {@code true} 用枚举 ordinal 序号，{@code false} 用 name 名称
         */
        public Builder serializeEnumUsingOrdinal(boolean ordinal) {
            configBuilder.serializeEnumUsingOrdinal(ordinal);
            return this;
        }

        /**
         * 设置是否将浮点数解析为 BigDecimal 以保留精度。
         *
         * @param useBigDecimal {@code true} 启用（金融等高精度场景推荐）
         */
        public Builder useBigDecimal(boolean useBigDecimal) {
            configBuilder.useBigDecimal(useBigDecimal);
            return this;
        }

        /**
         * 设置是否启用根名称包裹。
         *
         * @param wrapRootValue {@code true} 启用（配合 {@code @JsonRootName} 注解）
         */
        public Builder wrapRootValue(boolean wrapRootValue) {
            configBuilder.wrapRootValue(wrapRootValue);
            return this;
        }

        /**
         * 设置序列化遇错时是否抛出异常。
         *
         * @param failOnError {@code true} 抛异常，{@code false} 降级为容错输出
         */
        public Builder failOnError(boolean failOnError) {
            configBuilder.failOnError(failOnError);
            return this;
        }

        /**
         * 设置单次 JSON 处理的最大字节数上限。
         *
         * @param maxJsonSize 上限（字节），超过将抛出 {@link JsonException}
         */
        public Builder maxJsonSize(long maxJsonSize) {
            configBuilder.maxJsonSize(maxJsonSize);
            return this;
        }

        /**
         * 设置序列化/反序列化的最大嵌套深度。
         *
         * @param maxDepth 最大深度，防止过深结构导致栈溢出
         */
        public Builder maxDepth(int maxDepth) {
            configBuilder.maxDepth(maxDepth);
            return this;
        }

        /**
         * 设置泛型递归深度上限。
         *
         * @param maxGenericDepth 最大深度，防止嵌套泛型参数递归过深导致栈溢出，默认 64
         */
        public Builder maxGenericDepth(int maxGenericDepth) {
            configBuilder.maxGenericDepth(maxGenericDepth);
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
            JsonMapper mapper = new JsonMapper(configBuilder.build());
            mapper.configChanged();
            return mapper;
        }
    }

}
