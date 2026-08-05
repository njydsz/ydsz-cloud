package com.remisoft.common.json.internal;

import java.io.Serializable;

import com.remisoft.common.json.naming.PropertyNamingStrategy;
import com.remisoft.common.json.parser.JsonParserUtil;
import com.remisoft.common.json.provider.SerializationProvider;

import com.remisoft.common.json.reader.JSONReader;
/**
 * RemiJson 全局配置类
 *
 * <p><b>内部 API：</b>此类主要供 {@code JsonAutoConfiguration} 和框架内部使用。
 * 业务代码请通过 {@code remi.json.*} 配置属性调整 JSON 行为，不要直接操作 JsonConfig。</p>
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
 * <p><b>Builder 模式（框架内部使用）：</b></p>
 * <pre>
 * // JsonAutoConfiguration 内部使用 Builder 创建配置
 * JsonConfig config = JsonConfig.builder()
 *     .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
 *     .dateFormat("yyyy-MM-dd HH:mm:ss")
 *     .build();
 * config.apply();
 * </pre>
 *
 * <p><b>线程安全：</b>所有可变字段均为 {@code volatile}，保证单字段读写的可见性。
 * Builder 构建的实例字段值在 {@code build()} 时一次性写入，之后不应再原地修改。
 * 如需"不可变"语义的强保证，请通过 Builder 重新构建新实例。</p>
 *
 * @author remi-team
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

    private volatile int maxGenericDepth = 64;

    private volatile boolean useBigDecimal = false;

    /** 是否启用根名称包裹（配合 @JsonRootName 注解使用） */
    private volatile boolean wrapRootValue = false;

    private JsonConfig() {
    }

    /**
     * 获取配置实例（单例）
     *
     * <p>全局单例配置，由 Spring {@code remi.json.*} 属性初始化。
     * 业务代码请通过 {@code JsonMapper.builder()} 创建独立配置的 Mapper 实例。</p>
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
     * 创建指定配置的独立副本（供 JsonMapper 实例使用）。
     *
     * @param config 源配置
     * @return 独立副本
     * @since 1.0.0
     */
    public static JsonConfig copyOf(JsonConfig config) {
        if (config == null) {
            return new JsonConfig();
        }
        return builder().from(config).build();
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
     * 获取泛型递归深度上限。
     *
     * <p>防止恶意构造的嵌套泛型类型（如 {@code List<List<List...>>}）导致
     * {@link com.remisoft.common.json.provider.DeserializationProvider DeserializationProvider}
     * 递归过深触发 StackOverflow。默认 64，与 FastJSON2 默认值对齐。
     *
     * @return 泛型递归深度上限
     */
    public int getMaxGenericDepth() {
        return maxGenericDepth;
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
     * <p>启用后，带有 {@link com.remisoft.common.json.annotation.JsonRootName} 注解的类
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
        SerializationProvider.setUseBigDecimal(useBigDecimal);
        // 传播 maxDepth 到反序列化路径（JSONReader 全局配置）
        JSONReader.setMaxDepth(maxDepth);
        JSONReader.setMaxGenericDepth(maxGenericDepth);
        // wrapRootValue 不需要传播到 SerializationContext，因为它在 serialize() 入口处检查
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
                ", maxGenericDepth=" + maxGenericDepth +
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
     * 提供类型安全的链式配置构建方式。构建后的 JsonConfig 实例字段在 {@code build()} 时
     * 一次性写入，建议作为不可变实例使用（如需修改请重新构建新实例）。</p>
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
        private int maxGenericDepth = 64;
        private boolean useBigDecimal = false;
        private boolean wrapRootValue = false;

        private Builder() {
        }

        /**
         * 设置字段命名策略。
         *
         * @param namingStrategy 命名策略（如 SNAKE_CASE / LOWER_CAMEL_CASE），不可为 {@code null}
         */
        public Builder namingStrategy(PropertyNamingStrategy namingStrategy) {
            this.namingStrategy = namingStrategy;
            return this;
        }

        /**
         * 设置循环引用处理策略。
         *
         * @param strategy {@code REF} 输出引用路径 / {@code IGNORE} 忽略 / {@code ERROR} 抛异常
         */
        public Builder circularReferenceStrategy(CircularReferenceStrategy strategy) {
            this.circularReferenceStrategy = strategy;
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
         * 设置日期类型序列化格式。
         *
         * @param dateFormat SimpleDateFormat 模式串；{@code null} 按空串处理（使用默认格式）
         */
        public Builder dateFormat(String dateFormat) {
            this.dateFormat = dateFormat != null ? dateFormat : "";
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
         * 设置是否格式化（缩进）输出。
         *
         * @param prettyPrint {@code true} 启用美化输出
         */
        public Builder prettyPrint(boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
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
         * 设置反序列化时未显式指定格式的日期默认解析模式。
         *
         * @param defaultDateFormat 日期默认解析模式串
         */
        public Builder defaultDateFormat(String defaultDateFormat) {
            this.defaultDateFormat = defaultDateFormat;
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
         * 设置泛型递归深度上限。
         *
         * @param maxGenericDepth 最大深度，防止嵌套泛型参数递归过深导致栈溢出，默认 64
         */
        public Builder maxGenericDepth(int maxGenericDepth) {
            this.maxGenericDepth = maxGenericDepth;
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
                this.maxGenericDepth = config.maxGenericDepth;
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
            config.maxGenericDepth = this.maxGenericDepth;
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