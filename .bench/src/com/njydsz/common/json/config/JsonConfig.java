package com.njydsz.common.json.config;

import java.io.Serializable;

import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.provider.SerializationProvider;

import com.njydsz.common.json.parser.JsonParserUtil;
import com.njydsz.common.json.reader.JSONReader;
/**
 * YdszJson 全局配置类
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
 * <p><b>推荐使用 Builder 模式创建不可变配置：</b></p>
 * <pre>
 * // 推荐：使用 Builder 创建配置
 * JsonConfig config = JsonConfig.builder()
 *     .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
 *     .circularReferenceStrategy(CircularReferenceStrategy.REF)
 *     .dateFormat("yyyy-MM-dd HH:mm:ss")
 *     .build();
 * config.apply();
 * </pre>
 *
 * <p><b>兼容模式（已废弃）：</b></p>
 * <pre>
 * // 已废弃：使用 getInstance() + setter 链式调用
 * JsonConfig config = JsonConfig.getInstance();
 * config.setNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
 * config.apply();
 * </pre>
 *
 * <p><b>线程安全：</b>Builder 构建的实例是不可变的，天然线程安全。
 * 单例模式（{@link #getInstance()}）的 setter 仍保留 volatile 字段
 * 保证可见性，但已标记 {@link Deprecated}，建议迁移到 Builder。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JsonConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private static volatile JsonConfig instance;

    /** 所有可变字段均为 volatile，保证单字段读写的可见性与原子性 */
    private volatile PropertyNamingStrategy namingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE;

    private volatile CircularReferenceStrategy circularReferenceStrategy = CircularReferenceStrategy.REF;

    private volatile boolean writeNulls = false;

    private volatile String dateFormat = "";

    private volatile boolean serializeEnumUsingOrdinal = false;

    private volatile boolean prettyPrint = false;

    private volatile boolean failOnError = false;

    private volatile String defaultDateFormat = "yyyy-MM-dd'T'HH:mm:ss";

    private volatile long maxJsonSize = 10L * 1024 * 1024;

    private volatile int maxDepth = 256;

    private volatile boolean useBigDecimal = false;

    /** 是否启用根名称包裹（配合 @JsonRootName 注解使用） */
    private volatile boolean wrapRootValue = false;

    private JsonConfig() {
    }

    /**
     * 创建指定配置的不可变快照副本。
     *
     * <p>用于单次配置场景，不影响全局单例。</p>
     *
     * @param other 源配置
     * @return 新的配置实例，包含与源配置相同的值
     * @since 1.0.0
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
     * 获取循环引用处理策略
     *
     * @return 循环引用处理策略
     */
    public CircularReferenceStrategy getCircularReferenceStrategy() {
        return circularReferenceStrategy;
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
     * 获取日期格式
     *
     * @return 日期格式字符串
     */
    public String getDateFormat() {
        return dateFormat;
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
     * 是否格式化输出
     *
     * @return 是否格式化输出
     */
    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public String getDefaultDateFormat() {
        return defaultDateFormat;
    }

    /**
     * 获取最大 JSON 大小限制（字节）
     */
    public long getMaxJsonSize() {
        return maxJsonSize;
    }

    /**
     * 获取最大序列化深度
     */
    public int getMaxDepth() {
        return maxDepth;
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
     * 是否启用根名称包裹。
     *
     * <p>启用后，带有 {@link com.njydsz.common.json.annotation.JsonRootName} 注解的类
     * 在序列化时将被包裹在根名称中，反序列化时自动解包。</p>
     *
     * @return 是否启用根名称包裹
     * @since 1.0.0
     */
    public boolean isWrapRootValue() {
        return wrapRootValue;
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
        SerializationProvider.setDateFormat(dateFormat);
        SerializationProvider.setFailOnError(failOnError);
        JsonParserUtil.setUseBigDecimal(useBigDecimal);
        // 传播 maxDepth 到反序列化路径（JSONReader 全局配置）
        JSONReader.setMaxDepth(maxDepth);
        // wrapRootValue 不需要传播到 SerializationContext，因为它在 serialize() 入口处检查
    }

    /**
     * 重置配置为默认值
     *
     * <p>复合操作，通过 synchronized 保证多字段写入的原子性。</p>
     *
     * @return 当前配置实例（支持链式调用）
     */
    public synchronized JsonConfig reset() {
        this.namingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE;
        this.circularReferenceStrategy = CircularReferenceStrategy.REF;
        this.writeNulls = false;
        this.dateFormat = "";
        this.serializeEnumUsingOrdinal = false;
        this.prettyPrint = false;
        this.failOnError = false;
        this.defaultDateFormat = "yyyy-MM-dd'T'HH:mm:ss";
        this.maxJsonSize = 10L * 1024 * 1024;
        this.maxDepth = 256;
        this.useBigDecimal = false;
        this.wrapRootValue = false;
        return this;
    }

    /**
     * 从另一个配置复制
     *
     * <p>复合操作，通过 synchronized 保证多字段写入的原子性。</p>
     *
     * @param other 另一个配置
     * @return 当前配置实例（支持链式调用）
     */
    public synchronized JsonConfig copyFrom(JsonConfig other) {
        if (other != null) {
            this.namingStrategy = other.namingStrategy;
            this.circularReferenceStrategy = other.circularReferenceStrategy;
            this.writeNulls = other.writeNulls;
            this.dateFormat = other.dateFormat;
            this.serializeEnumUsingOrdinal = other.serializeEnumUsingOrdinal;
            this.prettyPrint = other.prettyPrint;
            this.failOnError = other.failOnError;
            this.defaultDateFormat = other.defaultDateFormat;
            this.maxJsonSize = other.maxJsonSize;
            this.maxDepth = other.maxDepth;
            this.useBigDecimal = other.useBigDecimal;
            this.wrapRootValue = other.wrapRootValue;
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
                ", defaultDateFormat='" + defaultDateFormat + '\'' +
                ", maxJsonSize=" + maxJsonSize +
                ", maxDepth=" + maxDepth +
                ", useBigDecimal=" + useBigDecimal +
                ", wrapRootValue=" + wrapRootValue +
                '}';
    }

    // ==================== Builder 模式 ====================

    /**
     * 创建一个 Builder 用于构建不可变的 JsonConfig 实例。
     *
     * <p>推荐使用 Builder 替代 getInstance() + setter 链式调用。
     * Builder 构建的实例所有字段均为 final，天然线程安全，无需 ThreadLocalSnapshot。</p>
     *
     * <pre>
     * JsonConfig config = JsonConfig.builder()
     *     .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
     *     .writeNulls(true)
     *     .dateFormat("yyyy-MM-dd HH:mm:ss")
     *     .build();
     * config.apply();
     * </pre>
     *
     * @return 新的 Builder 实例
     * @since 1.0.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 不可变配置构建器。
     *
     * <p>对标 Jackson ObjectMapper.Builder 和 FastJSON2 JSON.config() 的 Builder 模式，
     * 提供类型安全的链式配置构建方式。构建后的 JsonConfig 实例不可修改，
     * 无需 ThreadLocalSnapshot 保存/恢复。</p>
     *
     * @since 1.0.0
     */
    public static final class Builder {
        private PropertyNamingStrategy namingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE;
        private CircularReferenceStrategy circularReferenceStrategy = CircularReferenceStrategy.REF;
        private boolean writeNulls = false;
        private String dateFormat = "";
        private boolean serializeEnumUsingOrdinal = false;
        private boolean prettyPrint = false;
        private boolean failOnError = false;
        private String defaultDateFormat = "yyyy-MM-dd'T'HH:mm:ss";
        private long maxJsonSize = 10L * 1024 * 1024;
        private int maxDepth = 256;
        private boolean useBigDecimal = false;
        private boolean wrapRootValue = false;

        private Builder() {
        }

        public Builder namingStrategy(PropertyNamingStrategy namingStrategy) {
            this.namingStrategy = namingStrategy;
            return this;
        }

        public Builder circularReferenceStrategy(CircularReferenceStrategy strategy) {
            this.circularReferenceStrategy = strategy;
            return this;
        }

        public Builder writeNulls(boolean writeNulls) {
            this.writeNulls = writeNulls;
            return this;
        }

        public Builder dateFormat(String dateFormat) {
            this.dateFormat = dateFormat != null ? dateFormat : "";
            return this;
        }

        public Builder serializeEnumUsingOrdinal(boolean ordinal) {
            this.serializeEnumUsingOrdinal = ordinal;
            return this;
        }

        public Builder prettyPrint(boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        public Builder failOnError(boolean failOnError) {
            this.failOnError = failOnError;
            return this;
        }

        public Builder defaultDateFormat(String defaultDateFormat) {
            this.defaultDateFormat = defaultDateFormat;
            return this;
        }

        public Builder maxJsonSize(long maxJsonSize) {
            this.maxJsonSize = maxJsonSize;
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder useBigDecimal(boolean useBigDecimal) {
            this.useBigDecimal = useBigDecimal;
            return this;
        }

        public Builder wrapRootValue(boolean wrapRootValue) {
            this.wrapRootValue = wrapRootValue;
            return this;
        }

        /**
         * 从现有配置创建 Builder（用于修改已有配置）。
         *
         * @param config 源配置
         * @return 新的 Builder，预填充源配置的值
         */
        public Builder from(JsonConfig config) {
            if (config != null) {
                this.namingStrategy = config.namingStrategy;
                this.circularReferenceStrategy = config.circularReferenceStrategy;
                this.writeNulls = config.writeNulls;
                this.dateFormat = config.dateFormat;
                this.serializeEnumUsingOrdinal = config.serializeEnumUsingOrdinal;
                this.prettyPrint = config.prettyPrint;
                this.failOnError = config.failOnError;
                this.defaultDateFormat = config.defaultDateFormat;
                this.maxJsonSize = config.maxJsonSize;
                this.maxDepth = config.maxDepth;
                this.useBigDecimal = config.useBigDecimal;
                this.wrapRootValue = config.wrapRootValue;
            }
            return this;
        }

        /**
         * 构建不可变的 JsonConfig 实例。
         *
         * @return 新的 JsonConfig 实例
         */
        public JsonConfig build() {
            JsonConfig config = new JsonConfig();
            config.namingStrategy = this.namingStrategy;
            config.circularReferenceStrategy = this.circularReferenceStrategy;
            config.writeNulls = this.writeNulls;
            config.dateFormat = this.dateFormat;
            config.serializeEnumUsingOrdinal = this.serializeEnumUsingOrdinal;
            config.prettyPrint = this.prettyPrint;
            config.failOnError = this.failOnError;
            config.defaultDateFormat = this.defaultDateFormat;
            config.maxJsonSize = this.maxJsonSize;
            config.maxDepth = this.maxDepth;
            config.useBigDecimal = this.useBigDecimal;
            config.wrapRootValue = this.wrapRootValue;
            return config;
        }
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