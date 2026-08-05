package com.remisoft.common.json;

import com.remisoft.common.json.internal.JsonConfig;


/**
 * 显式对象序列化器（线程安全，对标 Jackson 的 ObjectWriter）。
 *
 * <p>提供免 ThreadLocal 的显式序列化能力，适合：</p>
 * <ul>
 *   <li>跨线程场景（每个线程使用独立实例）</li>
 *   <li>需要固定配置快照、不受运行时全局配置影响的场景</li>
 *   <li>需要预热和复用以提升性能的批量序列化场景</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 静态工厂创建
 * ObjectWriter writer = ObjectWriter.forType(User.class);
 *
 * // 链式配置
 * ObjectWriter prettyWriter = ObjectWriter.forType(User.class)
 *     .withPrettyPrint(true)
 *     .withDateFormat("yyyy-MM-dd");
 *
 * // 序列化
 * String json = writer.toJson(user);
 * byte[] bytes = writer.toJsonBytes(user);
 * </pre>
 *
 * <p>此类持有 {@link JsonConfig} 的快照，创建后配置即固定，
 * 不受 {@link JsonConfig#install(JsonConfig)} 影响。</p>
 *
 * @author remi-team
 * @since 1.1.0
 */
public class ObjectWriter {

    private final JsonMapper mapper;

    private ObjectWriter(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 创建用于指定类型的序列化器。
     *
     * @param type 要序列化的类型（仅用于未来扩展，当前不影响行为）
     * @return 新的 ObjectWriter 实例
     */
    public static ObjectWriter forType(Class<?> type) {
        return new ObjectWriter(new JsonMapper(JsonConfig.getInstance()));
    }

    /**
     * 创建使用显式配置的序列化器。
     *
     * @param config JsonConfig 配置
     * @return 新的 ObjectWriter 实例
     */
    public static ObjectWriter of(JsonConfig config) {
        return new ObjectWriter(new JsonMapper(config));
    }

    /**
     * 从全局默认配置创建序列化器。
     *
     * @return 新的 ObjectWriter 实例
     */
    public static ObjectWriter standard() {
        return new ObjectWriter(new JsonMapper(JsonConfig.getInstance()));
    }

    // ==================== 链式配置 ====================

    /**
     * 设置是否格式化输出（返回新实例）。
     *
     * @param pretty true 启用格式化
     * @return 新的 ObjectWriter 实例
     */
    public ObjectWriter withPrettyPrint(boolean pretty) {
        JsonConfig config = JsonConfig.builder()
                .from(this.mapper.getConfig())
                .prettyPrint(pretty)
                .build();
        return new ObjectWriter(new JsonMapper(config));
    }

    /**
     * 设置是否输出 null 值字段（返回新实例）。
     *
     * @param writeNulls true 输出 null
     * @return 新的 ObjectWriter 实例
     */
    public ObjectWriter withWriteNulls(boolean writeNulls) {
        JsonConfig config = JsonConfig.builder()
                .from(this.mapper.getConfig())
                .writeNulls(writeNulls)
                .build();
        return new ObjectWriter(new JsonMapper(config));
    }

    /**
     * 设置日期格式（返回新实例）。
     *
     * @param dateFormat 日期格式化字符串
     * @return 新的 ObjectWriter 实例
     */
    public ObjectWriter withDateFormat(String dateFormat) {
        JsonConfig config = JsonConfig.builder()
                .from(this.mapper.getConfig())
                .dateFormat(dateFormat)
                .build();
        return new ObjectWriter(new JsonMapper(config));
    }

    /**
     * 设置命名策略（返回新实例）。
     *
     * @param strategy 命名策略
     * @return 新的 ObjectWriter 实例
     */
    public ObjectWriter withNamingStrategy(
            com.remisoft.common.json.naming.PropertyNamingStrategy strategy) {
        JsonConfig config = JsonConfig.builder()
                .from(this.mapper.getConfig())
                .namingStrategy(strategy)
                .build();
        return new ObjectWriter(new JsonMapper(config));
    }

    // ==================== 序列化方法 ====================

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 要序列化的对象
     * @return JSON 字符串
     */
    public String toJson(Object value) {
        return mapper.toJson(value);
    }

    /**
     * 将对象序列化为 UTF-8 字节数组。
     *
     * @param value 要序列化的对象
     * @return UTF-8 编码的字节数组
     */
    public byte[] toJsonBytes(Object value) {
        return mapper.toJsonBytes(value);
    }

    /**
     * 将对象序列化为格式化的 JSON 字符串。
     *
     * @param value 要序列化的对象
     * @return 格式化后的 JSON 字符串
     */
    public String toPrettyJson(Object value) {
        return mapper.format(value);
    }
}
