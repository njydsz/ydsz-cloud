package com.remisoft.common.json;

import java.io.InputStream;
import java.lang.reflect.Type;

import com.remisoft.common.json.internal.JsonConfig;
import com.remisoft.common.json.type.JsonType;

/**
 * 显式对象反序列化器（线程安全，对标 Jackson 的 ObjectReader）。
 *
 * <p>提供免 ThreadLocal 的显式反序列化能力，适合：</p>
 * <ul>
 *   <li>跨线程场景（每个线程使用独立实例）</li>
 *   <li>需要固定配置快照、不受运行时全局配置影响的场景</li>
 *   <li>需要预热和复用以提升性能的批量反序列化场景</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 静态工厂创建
 * ObjectReader reader = ObjectReader.forType(User.class);
 *
 * // 链式配置
 * ObjectReader lenientReader = ObjectReader.forType(User.class)
 *     .withUseBigDecimal(true)
 *     .withFailOnError(false);
 *
 * // 反序列化
 * User user = reader.readValue(json);
 * User fromBytes = reader.readValue(bytes);
 * List&lt;User&gt; users = reader.readValue(json, new JsonType&lt;List&lt;User&gt;&gt;() {});
 * </pre>
 *
 * <p>此类持有 {@link JsonConfig} 的快照，创建后配置即固定，
 * 不受 {@link JsonConfig#install(JsonConfig)} 影响。</p>
 *
 * @author remi-team
 * @since 1.1.0
 */
public class ObjectReader {

    private final JsonMapper mapper;

    private ObjectReader(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 创建用于指定类型的反序列化器。
     *
     * @param type 反序列化的目标类型（仅用于未来扩展，当前不影响行为）
     * @return 新的 ObjectReader 实例
     */
    public static ObjectReader forType(Class<?> type) {
        return new ObjectReader(new JsonMapper(JsonConfig.getInstance()));
    }

    /**
     * 创建用于泛型类型的反序列化器。
     *
     * @param typeRef 泛型类型引用
     * @param <T>     目标类型
     * @return 新的 ObjectReader 实例
     */
    public static <T> ObjectReader forType(JsonType<T> typeRef) {
        return new ObjectReader(new JsonMapper(JsonConfig.getInstance()));
    }

    /**
     * 创建使用显式配置的反序列化器。
     *
     * @param config JsonConfig 配置
     * @return 新的 ObjectReader 实例
     */
    public static ObjectReader of(JsonConfig config) {
        return new ObjectReader(new JsonMapper(config));
    }

    /**
     * 从全局默认配置创建反序列化器。
     *
     * @return 新的 ObjectReader 实例
     */
    public static ObjectReader standard() {
        return new ObjectReader(new JsonMapper(JsonConfig.getInstance()));
    }

    // ==================== 链式配置 ====================

    /**
     * 设置是否使用 BigDecimal 解析浮点数（返回新实例）。
     *
     * @param useBigDecimal true 启用高精度浮点
     * @return 新的 ObjectReader 实例
     */
    public ObjectReader withUseBigDecimal(boolean useBigDecimal) {
        JsonConfig config = JsonConfig.builder()
                .from(this.mapper.getConfig())
                .useBigDecimal(useBigDecimal)
                .build();
        return new ObjectReader(new JsonMapper(config));
    }

    /**
     * 设置最大 JSON 大小（返回新实例）。
     *
     * @param maxSize 最大字节数
     * @return 新的 ObjectReader 实例
     */
    public ObjectReader withMaxJsonSize(long maxSize) {
        JsonConfig config = JsonConfig.builder()
                .from(this.mapper.getConfig())
                .maxJsonSize(maxSize)
                .build();
        return new ObjectReader(new JsonMapper(config));
    }

    /**
     * 设置最大嵌套深度（返回新实例）。
     *
     * @param maxDepth 最大深度
     * @return 新的 ObjectReader 实例
     */
    public ObjectReader withMaxDepth(int maxDepth) {
        JsonConfig config = JsonConfig.builder()
                .from(this.mapper.getConfig())
                .maxDepth(maxDepth)
                .build();
        return new ObjectReader(new JsonMapper(config));
    }

    /**
     * 设置命名策略（返回新实例）。
     *
     * @param strategy 命名策略
     * @return 新的 ObjectReader 实例
     */
    public ObjectReader withNamingStrategy(
            com.remisoft.common.json.naming.PropertyNamingStrategy strategy) {
        JsonConfig config = JsonConfig.builder()
                .from(this.mapper.getConfig())
                .namingStrategy(strategy)
                .build();
        return new ObjectReader(new JsonMapper(config));
    }

    /**
     * 设置遇错行为（返回新实例）。
     *
     * @param failOnError true 抛异常，false 降级输出
     * @return 新的 ObjectReader 实例
     */
    public ObjectReader withFailOnError(boolean failOnError) {
        JsonConfig config = JsonConfig.builder()
                .from(this.mapper.getConfig())
                .failOnError(failOnError)
                .build();
        return new ObjectReader(new JsonMapper(config));
    }

    // ==================== 反序列化方法 ====================

    /**
     * 从 JSON 字符串反序列化为指定类型。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return 反序列化后的对象
     */
    public <T> T readValue(String json, Class<T> clazz) {
        return mapper.toObject(json, clazz);
    }

    /**
     * 从 JSON 字符串反序列化为泛型类型。
     *
     * @param json    JSON 字符串
     * @param typeRef 类型引用
     * @param <T>     类型参数
     * @return 反序列化后的对象
     */
    public <T> T readValue(String json, JsonType<T> typeRef) {
        return mapper.toObject(json, typeRef);
    }

    /**
     * 从 JSON 字符串反序列化为 Type 类型。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T>  类型参数
     * @return 反序列化后的对象
     */
    public <T> T readValue(String json, Type type) {
        return mapper.toObject(json, type);
    }

    /**
     * 从 UTF-8 字节数组反序列化为指定类型。
     *
     * @param bytes UTF-8 编码的字节数组
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return 反序列化后的对象
     */
    public <T> T readValue(byte[] bytes, Class<T> clazz) {
        return mapper.toObject(bytes, clazz);
    }

    /**
     * 从 UTF-8 字节数组反序列化为泛型类型。
     *
     * @param bytes   UTF-8 编码的字节数组
     * @param typeRef 类型引用
     * @param <T>     类型参数
     * @return 反序列化后的对象
     */
    public <T> T readValue(byte[] bytes, JsonType<T> typeRef) {
        return mapper.readValue(new java.io.ByteArrayInputStream(bytes), typeRef);
    }

    /**
     * 从 InputStream 反序列化为指定类型。
     *
     * @param in    输入流
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return 反序列化后的对象
     */
    public <T> T readValue(InputStream in, Class<T> clazz) {
        return mapper.readValue(in, clazz);
    }

    /**
     * 从 InputStream 反序列化为泛型类型。
     *
     * @param in      输入流
     * @param typeRef 类型引用
     * @param <T>     类型参数
     * @return 反序列化后的对象
     */
    public <T> T readValue(InputStream in, JsonType<T> typeRef) {
        return mapper.readValue(in, typeRef);
    }

    /**
     * 从 JSON 字符串解析为 Map。
     *
     * @param json JSON 字符串
     * @return Map 对象
     */
    public java.util.Map<String, Object> readMap(String json) {
        return mapper.parseMap(json);
    }

    /**
     * 从 JSON 数组字符串解析为指定类型的列表。
     *
     * @param json        JSON 字符串
     * @param elementClass 元素类型
     * @param <T>         元素类型
     * @return 列表
     */
    public <T> java.util.List<T> readList(String json, Class<T> elementClass) {
        return mapper.parseArray(json, elementClass);
    }
}
