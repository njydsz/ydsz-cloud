package com.njydsz.common.json.config;

import java.io.Serializable;

import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.provider.SerializationProvider;

/**
 * Json 全局配置类
 *
 * <p>参考大厂架构设计，提供统一的 JSON 处理配置中心。</p>
 *
 * <p><b>配置功能：</b></p>
 * <ul>
 *   <li>全局命名策略 - 序列化时的字段命名转换</li>
 *   <li>循环引用处理策略 - REF/IGNORE/ERROR</li>
 *   <li>空值处理策略 - 是否输出 null 值</li>
 *   <li>日期格式配置 - 全局日期格式</li>
 *   <li>枚举序列化策略 - ordinal 或 name</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 获取配置实例
 * JsonConfig config = JsonConfig.getInstance();
 *
 * // 设置全局命名策略
 * config.setNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
 *
 * // 设置循环引用处理
 * config.setCircularReferenceStrategy(CircularReferenceStrategy.REF);
 *
 * // 设置日期格式
 * config.setDateFormat("yyyy-MM-dd HH:mm:ss");
 *
 * // 应用配置
 * config.apply();
 * </pre>
 *
 * @since 1.0.0
 */
public final class JsonConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private static volatile JsonConfig instance;

    private PropertyNamingStrategy namingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE;

    private CircularReferenceStrategy circularReferenceStrategy = CircularReferenceStrategy.REF;

    private boolean writeNulls = false;

    private String dateFormat = "";

    private boolean serializeEnumUsingOrdinal = false;

    private boolean prettyPrint = false;

    private boolean failOnError = false;

    private int asmThreshold = 10000;

    private String defaultDateFormat = "yyyy-MM-dd'T'HH:mm:ss";

    private volatile long maxJsonSize = 10L * 1024 * 1024;

    private volatile int maxDepth = 256;

    private volatile boolean useBigDecimal = false;

    private JsonConfig() {
    }

    /**
     * 创建指定配置的不可变快照副本。
     *
     * <p>用于单次配置场景，不影响全局单例。</p>
     *
     * @param other 源配置
     * @return 新的配置实例，包含与源配置相同的值
     * @since 1.4.0
     */
    public static JsonConfig copyOf(JsonConfig other) {
        JsonConfig copy = new JsonConfig();
        if (other != null) {
            copy.copyFrom(other);
        }
        return copy;
    }

    /**
     * 获取配置实例（单例）
     *
     * @return JsonConfig 实例
     */
    public static JsonConfig getInstance() {
        if (instance == null) {
            synchronized (JsonConfig.class) {
                if (instance == null) {
                    instance = new JsonConfig();
                }
            }
        }
        return instance;
    }

    /**
     * 获取命名策略
     *
     * @return 当前的命名策略
     */
    public PropertyNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    /**
     * 设置命名策略
     *
     * @param namingStrategy 命名策略
     * @return 当前配置实例（支持链式调用）
     */
    public JsonConfig setNamingStrategy(PropertyNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
        return this;
    }

    /**
     * 获取循环引用处理策略
     *
     * @return 循环引用处理策略
     */
    public CircularReferenceStrategy getCircularReferenceStrategy() {
        return circularReferenceStrategy;
    }

    /**
     * 设置循环引用处理策略
     *
     * @param circularReferenceStrategy 循环引用处理策略
     * @return 当前配置实例（支持链式调用）
     */
    public JsonConfig setCircularReferenceStrategy(CircularReferenceStrategy circularReferenceStrategy) {
        this.circularReferenceStrategy = circularReferenceStrategy;
        return this;
    }

    /**
     * 是否输出空值
     *
     * @return 是否输出空值
     */
    public boolean isWriteNulls() {
        return writeNulls;
    }

    /**
     * 设置是否输出空值
     *
     * @param writeNulls 是否输出空值
     * @return 当前配置实例（支持链式调用）
     */
    public JsonConfig setWriteNulls(boolean writeNulls) {
        this.writeNulls = writeNulls;
        return this;
    }

    /**
     * 获取日期格式
     *
     * @return 日期格式字符串
     */
    public String getDateFormat() {
        return dateFormat;
    }

    /**
     * 设置日期格式
     *
     * @param dateFormat 日期格式字符串
     * @return 当前配置实例（支持链式调用）
     */
    public JsonConfig setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
        return this;
    }

    /**
     * 是否使用枚举序号序列化
     *
     * @return 是否使用枚举序号序列化
     */
    public boolean isSerializeEnumUsingOrdinal() {
        return serializeEnumUsingOrdinal;
    }

    /**
     * 设置是否使用枚举序号序列化
     *
     * @param serializeEnumUsingOrdinal 是否使用枚举序号序列化
     * @return 当前配置实例（支持链式调用）
     */
    public JsonConfig setSerializeEnumUsingOrdinal(boolean serializeEnumUsingOrdinal) {
        this.serializeEnumUsingOrdinal = serializeEnumUsingOrdinal;
        return this;
    }

    /**
     * 是否格式化输出
     *
     * @return 是否格式化输出
     */
    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    /**
     * 设置是否格式化输出
     *
     * @param prettyPrint 是否格式化输出
     * @return 当前配置实例（支持链式调用）
     */
   public JsonConfig setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
        return this;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public JsonConfig setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
        return this;
    }

    public int getAsmThreshold() {
        return asmThreshold;
    }

    public JsonConfig setAsmThreshold(int asmThreshold) {
        this.asmThreshold = asmThreshold;
        return this;
    }

    public String getDefaultDateFormat() {
        return defaultDateFormat;
    }

    public JsonConfig setDefaultDateFormat(String defaultDateFormat) {
        this.defaultDateFormat = defaultDateFormat;
        return this;
    }

    /**
     * 获取最大 JSON 大小限制（字节）
     */
    public long getMaxJsonSize() {
        return maxJsonSize;
    }

    /**
     * 设置最大 JSON 大小限制（字节）
     */
    public JsonConfig setMaxJsonSize(long maxJsonSize) {
        this.maxJsonSize = maxJsonSize;
        return this;
    }

    /**
     * 获取最大序列化深度
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * 设置最大序列化深度
     */
    public JsonConfig setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
        return this;
    }

    /**
     * 是否使用 BigDecimal 解析浮点数。
     *
     * <p>启用后，包含小数点的数字将被解析为 {@link java.math.BigDecimal}，
     * 避免精度丢失，适用于金融场景。</p>
     *
     * @return 是否使用 BigDecimal
     */
    public boolean isUseBigDecimal() {
        return useBigDecimal;
    }

    /**
     * 设置是否使用 BigDecimal 解析浮点数。
     *
     * @param useBigDecimal 是否使用 BigDecimal
     * @return 当前配置实例（支持链式调用）
     */
    public JsonConfig setUseBigDecimal(boolean useBigDecimal) {
        this.useBigDecimal = useBigDecimal;
        return this;
    }

    /**
     * 应用配置到序列化提供者
     *
     * <p>将当前配置应用到 SerializationProvider</p>
     */
    public void apply() {
        SerializationProvider.setNamingStrategy(namingStrategy);
        SerializationProvider.setWriteNulls(writeNulls);
        SerializationProvider.setPrettyPrint(prettyPrint);
        SerializationProvider.setCircularReferenceStrategy(circularReferenceStrategy.name());
        SerializationProvider.setSerializeEnumUsingOrdinal(serializeEnumUsingOrdinal);
        com.njydsz.common.json.parser.JsonParser.setUseBigDecimal(useBigDecimal);
    }

    /**
     * 重置配置为默认值
     *
     * @return 当前配置实例（支持链式调用）
     */
    public JsonConfig reset() {
        this.namingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE;
        this.circularReferenceStrategy = CircularReferenceStrategy.REF;
        this.writeNulls = false;
        this.dateFormat = "";
        this.serializeEnumUsingOrdinal = false;
        this.prettyPrint = false;
        this.failOnError = false;
        this.asmThreshold = 10000;
        this.defaultDateFormat = "yyyy-MM-dd'T'HH:mm:ss";
        this.maxJsonSize = 10L * 1024 * 1024;
        this.maxDepth = 256;
        this.useBigDecimal = false;
        return this;
    }

    /**
     * 从另一个配置复制
     *
     * @param other 另一个配置
     * @return 当前配置实例（支持链式调用）
     */
    public JsonConfig copyFrom(JsonConfig other) {
        if (other != null) {
            this.namingStrategy = other.namingStrategy;
            this.circularReferenceStrategy = other.circularReferenceStrategy;
            this.writeNulls = other.writeNulls;
            this.dateFormat = other.dateFormat;
            this.serializeEnumUsingOrdinal = other.serializeEnumUsingOrdinal;
            this.prettyPrint = other.prettyPrint;
            this.failOnError = other.failOnError;
            this.asmThreshold = other.asmThreshold;
            this.defaultDateFormat = other.defaultDateFormat;
            this.maxJsonSize = other.maxJsonSize;
            this.maxDepth = other.maxDepth;
            this.useBigDecimal = other.useBigDecimal;
        }
        return this;
    }

    @Override
    public String toString() {
        return "JsonConfig{" +
                "namingStrategy=" + namingStrategy +
                ", circularReferenceStrategy=" + circularReferenceStrategy +
                ", writeNulls=" + writeNulls +
                ", dateFormat='" + dateFormat + '\'' +
                ", serializeEnumUsingOrdinal=" + serializeEnumUsingOrdinal +
                ", prettyPrint=" + prettyPrint +
                ", failOnError=" + failOnError +
                ", asmThreshold=" + asmThreshold +
                ", defaultDateFormat='" + defaultDateFormat + '\'' +
                '}';
    }

    /**
     * 循环引用处理策略
     */
    public enum CircularReferenceStrategy {
        /**
         * 输出引用路径
         */
        REF,

        /**
         * 忽略循环引用
         */
        IGNORE,

        /**
         * 抛出异常
         */
        ERROR
    }
}