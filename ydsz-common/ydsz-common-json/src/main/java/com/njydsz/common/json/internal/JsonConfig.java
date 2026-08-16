package com.njydsz.common.json.internal;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.provider.SerializationProvider;
import com.njydsz.common.json.reader.JSONReader;
/**
 * YdszJson 全局配置类（不可变）。
 *
 * <p><b>内部 API：</b>此类主要供 {@code JsonAutoConfiguration} 和框架内部使用。
 * 业务代码请通过 {@code ydsz.json.*} 配置属性调整 JSON 行为，不要直接操作 JsonConfig。</p>
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
 * <p><b>不可变设计：</b>所有字段均为 {@code final}，通过 Builder 一次性构建后不可修改。
 * 如需修改配置，请通过 {@link Builder#from(JsonConfig) Builder.from()} 创建新实例，
 * 或使用 {@link #install(JsonConfig)} 原子替换全局单例。
 * 对标 Jackson {@code ObjectMapper} 实例级别的不可变语义。</p>
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
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JsonConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private static volatile JsonConfig instance;

    /**
     * 全局配置版本号，每次 install() 自增。
     *
     * <p>缓存组件可通过 {@link #getConfigVersion()} 或轮询此值判断配置是否变更，
     * 从而实现自动失效（如命名策略切换后清理 SerializerCache 中已烘焙的字段名）。</p>
     */
    private static final AtomicLong CONFIG_VERSION = new AtomicLong(0);

    /**
     * 配置变更监听器列表（线程安全，CopyOnWrite 保证迭代期间可注册/移除）。
     *
     * <p>监听器在 {@link #install(JsonConfig)} 完成后回调，可用于清理缓存、记录审计日志等。</p>
     */
    private static final List<ConfigChangeListener> CHANGE_LISTENERS = new CopyOnWriteArrayList<>();

    /** 默认 JSON 最大字节数上限：10 MB */
    private static final long DEFAULT_MAX_JSON_SIZE = 10L * 1024 * 1024;

    /** 默认序列化/反序列化最大嵌套深度 */
    private static final int DEFAULT_MAX_DEPTH = 256;

    /** 默认泛型递归深度上限 */
    private static final int DEFAULT_MAX_GENERIC_DEPTH = 64;

    /** 字段不可变（final），通过构造函数一次性赋值 */
    private final PropertyNamingStrategy namingStrategy;

    private final CircularReferenceStrategy circularReferenceStrategy;

    private final boolean writeNulls;

    private final String dateFormat;

    private final boolean serializeEnumUsingOrdinal;

    private final boolean prettyPrint;

    private final boolean failOnError;

    private final String defaultDateFormat;

    private final long maxJsonSize;

    private final int maxDepth;

    private final int maxGenericDepth;

    private final boolean useBigDecimal;

    private final boolean wrapRootValue;

    /**
     * 全参数构造函数（包可见，仅供 Builder 内部使用）。
     *
     * <p>所有字段通过此构造函数一次性写入，之后不可修改。
     * 默认值由 {@link Builder} 预先填充。</p>
     *
     * @param namingStrategy            字段命名策略
     * @param circularReferenceStrategy 循环引用处理策略
     * @param writeNulls                是否输出 null 字段
     * @param dateFormat                日期格式字符串
     * @param serializeEnumUsingOrdinal 是否使用枚举序号序列化
     * @param prettyPrint               是否格式化输出
     * @param failOnError               遇错时是否抛出异常
     * @param defaultDateFormat         默认日期解析格式
     * @param maxJsonSize               最大 JSON 字节数上限
     * @param maxDepth                  最大序列化深度
     * @param maxGenericDepth           泛型递归深度上限
     * @param useBigDecimal             是否使用 BigDecimal 解析浮点数
     * @param wrapRootValue             是否启用根名称包裹
     */
    JsonConfig(
            PropertyNamingStrategy namingStrategy,
            CircularReferenceStrategy circularReferenceStrategy,
            boolean writeNulls,
            String dateFormat,
            boolean serializeEnumUsingOrdinal,
            boolean prettyPrint,
            boolean failOnError,
            String defaultDateFormat,
            long maxJsonSize,
            int maxDepth,
            int maxGenericDepth,
            boolean useBigDecimal,
            boolean wrapRootValue
    ) {
        this.namingStrategy = namingStrategy;
        this.circularReferenceStrategy = circularReferenceStrategy;
        this.writeNulls = writeNulls;
        this.dateFormat = dateFormat;
        this.serializeEnumUsingOrdinal = serializeEnumUsingOrdinal;
        this.prettyPrint = prettyPrint;
        this.failOnError = failOnError;
        this.defaultDateFormat = defaultDateFormat;
        this.maxJsonSize = maxJsonSize;
        this.maxDepth = maxDepth;
        this.maxGenericDepth = maxGenericDepth;
        this.useBigDecimal = useBigDecimal;
        this.wrapRootValue = wrapRootValue;
    }

    /**
     * 替代 {@link #getInstance()} 并一次性安装配置到全局单例（原子替换）。
     *
     * <p>标榜"构建后不可变"：外部通过 {@code JsonConfig.builder().build()} 构建完整配置后，
     * 调用此方法原子替换全局单例，后续只读不修改。
     * 如确需修改配置，请重新构建新的 {@link JsonConfig} 并再次调用 {@code install(newConfig)}。</p>
     *
     * <p>此方法等价于 {@code instance = newConfig; newConfig.apply()}，
     * 保证配置立即生效（传播到 JSONReader / SerializationProvider 等全局组件），
     * 并同步刷新 {@link com.njydsz.common.json.YdszJson} 内部的默认 Mapper 实例。
     * 安装后会自增全局配置版本号 {@link #CONFIG_VERSION} 并通知所有注册的
     * {@link ConfigChangeListener}。</p>
     *
     * @param newConfig 新的全局配置实例（由 Builder 构建）
     * @since 1.0.0
     */
    public static void install(JsonConfig newConfig) {
        if (newConfig == null) {
            throw new IllegalArgumentException("JsonConfig.install: config must not be null");
        }
        JsonConfig oldConfig;
        synchronized (JsonConfig.class) {
            oldConfig = instance;
            instance = newConfig;
        }
        instance.apply();
        // 触发 YdszJson 静态方法委托的默认 Mapper 重建，使配置变更立即生效
        com.njydsz.common.json.YdszJson.reloadDefaultMapper();
        // 自增版本号，供缓存组件检测配置变更
        long newVersion = CONFIG_VERSION.incrementAndGet();
        // 通知监听器（异步不阻塞安装流程）
        notifyConfigChanged(oldConfig, newConfig, newVersion);
    }

    /**
     * 注册配置变更监听器。
     *
     * <p>监听器在 {@link #install(JsonConfig)} 完成后回调，典型的使用场景：</p>
     * <ul>
     *   <li>SerializerCache：检测到命名策略变更时清理已烘焙字段名的缓存条目</li>
     *   <li>监控指标：记录配置变更审计日志</li>
     * </ul>
     *
     * @param listener 监听器实例，null 忽略
     * @since 1.1.0
     */
    public static void addChangeListener(ConfigChangeListener listener) {
        if (listener != null) {
            CHANGE_LISTENERS.add(listener);
        }
    }

    /**
     * 移除配置变更监听器。
     *
     * @param listener 待移除的监听器
     * @since 1.1.0
     */
    public static void removeChangeListener(ConfigChangeListener listener) {
        CHANGE_LISTENERS.remove(listener);
    }

    /**
     * 获取全局配置版本号。
     *
     * <p>每次 install() 自增。缓存组件可存储创建时的版本号，
     * 用于检测配置是否已变更并触发自动失效。</p>
     *
     * @return 当前配置版本号
     * @since 1.1.0
     */
    public static long getConfigVersion() {
        return CONFIG_VERSION.get();
    }

    /**
     * 通知所有监听器配置已变更。
     *
     * @param oldConfig  旧配置实例
     * @param newConfig  新配置实例
     * @param newVersion 新版本号
     */
    private static void notifyConfigChanged(JsonConfig oldConfig, JsonConfig newConfig, long newVersion) {
        if (CHANGE_LISTENERS.isEmpty()) {
            return;
        }
        for (ConfigChangeListener listener : CHANGE_LISTENERS) {
            try {
                listener.onConfigChanged(oldConfig, newConfig, newVersion);
            } catch (Exception e) {
                // 监听器异常不应影响配置安装
                // 使用 SLF4J 日志记录，但这里无法获取 Logger（静态初始化顺序），暂不记录
            }
        }
    }

    /**
     * 获取配置实例（单例）
     *
     * <p><b>推荐用法：</b>业务代码只读获取配置。如需修改全局配置，请使用
     * {@link #install(JsonConfig)} 替换为新的不可变实例。</p>
     *
     * @return JsonConfig 实例（默认配置）
     */
    public static JsonConfig getInstance() {
        if (instance == null) {
            synchronized (JsonConfig.class) {
                if (instance == null) {
                    instance = builder().build();
                }
            }
        }
        return instance;
    }

    /**
     * 创建指定配置的独立副本（供 JsonMapper 实例使用）。
     *
     * @param config 源配置
     * @return 独立副本（默认配置，当 config 为 null 时）
     * @since 1.0.0
     */
    public static JsonConfig copyOf(JsonConfig config) {
        if (config == null) {
            return builder().build();
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

    /**
     * 是否遇错时抛出异常。
     *
     * @return {@code true} 抛异常，{@code false} 降级为容错输出
     */
    public boolean isFailOnError() {
        return failOnError;
    }

    /**
     * 获取反序列化时未显式指定格式的日期默认解析模式。
     *
     * @return 日期默认解析模式串
     */
    public String getDefaultDateFormat() {
        return defaultDateFormat;
    }

    /**
     * 获取最大 JSON 大小限制（字节）
     *
     * @return 最大 JSON 字节数上限
     */
    public long getMaxJsonSize() {
        return maxJsonSize;
    }

    /**
     * 获取最大序列化深度
     *
     * @return 最大嵌套深度
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * 获取泛型递归深度上限。
     *
     * <p>防止恶意构造的嵌套泛型类型（如 {@code List<List<List...>>}）导致
     * {@link com.njydsz.common.json.provider.DeserializationProvider DeserializationProvider}
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
        private long maxJsonSize = DEFAULT_MAX_JSON_SIZE;
        private int maxDepth = DEFAULT_MAX_DEPTH;
        private int maxGenericDepth = DEFAULT_MAX_GENERIC_DEPTH;
        private boolean useBigDecimal = false;
        private boolean wrapRootValue = false;

        /**
         * 私有构造函数，通过 {@link JsonConfig#builder()} 创建实例。
         */
        private Builder() {
        }

        /**
         * 设置字段命名策略。
         *
         * @param namingStrategy 命名策略（如 SNAKE_CASE / LOWER_CAMEL_CASE），不可为 {@code null}
         * @return this（链式调用）
         */
        public Builder namingStrategy(PropertyNamingStrategy namingStrategy) {
            this.namingStrategy = namingStrategy;
            return this;
        }

        /**
         * 设置循环引用处理策略。
         *
         * @param strategy {@code REF} 输出引用路径 / {@code IGNORE} 忽略 / {@code ERROR} 抛异常
         * @return this（链式调用）
         */
        public Builder circularReferenceStrategy(CircularReferenceStrategy strategy) {
            this.circularReferenceStrategy = strategy;
            return this;
        }

        /**
         * 设置是否输出 null 字段。
         *
         * @param writeNulls {@code true} 保留 null 字段，{@code false} 跳过 null 字段
         * @return this（链式调用）
         */
        public Builder writeNulls(boolean writeNulls) {
            this.writeNulls = writeNulls;
            return this;
        }

        /**
         * 设置日期类型序列化格式。
         *
         * @param dateFormat SimpleDateFormat 模式串；{@code null} 按空串处理（使用默认格式）
         * @return this（链式调用）
         */
        public Builder dateFormat(String dateFormat) {
            this.dateFormat = dateFormat != null ? dateFormat : "";
            return this;
        }

        /**
         * 设置枚举序列化方式。
         *
         * @param ordinal {@code true} 用枚举 ordinal 序号，{@code false} 用 name 名称
         * @return this（链式调用）
         */
        public Builder serializeEnumUsingOrdinal(boolean ordinal) {
            this.serializeEnumUsingOrdinal = ordinal;
            return this;
        }

        /**
         * 设置是否格式化（缩进）输出。
         *
         * @param prettyPrint {@code true} 启用美化输出
         * @return this（链式调用）
         */
        public Builder prettyPrint(boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        /**
         * 设置序列化遇错时是否抛出异常。
         *
         * @param failOnError {@code true} 抛异常，{@code false} 降级为容错输出
         * @return this（链式调用）
         */
        public Builder failOnError(boolean failOnError) {
            this.failOnError = failOnError;
            return this;
        }

        /**
         * 设置反序列化时未显式指定格式的日期默认解析模式。
         *
         * @param defaultDateFormat 日期默认解析模式串
         * @return this（链式调用）
         */
        public Builder defaultDateFormat(String defaultDateFormat) {
            this.defaultDateFormat = defaultDateFormat;
            return this;
        }

        /**
         * 设置单次 JSON 处理的最大字节数上限。
         *
         * @param maxJsonSize 上限（字节），超过将抛出 {@link JsonException}
         * @return this（链式调用）
         */
        public Builder maxJsonSize(long maxJsonSize) {
            this.maxJsonSize = maxJsonSize;
            return this;
        }

        /**
         * 设置序列化/反序列化的最大嵌套深度。
         *
         * @param maxDepth 最大深度，防止过深结构导致栈溢出
         * @return this（链式调用）
         */
        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        /**
         * 设置泛型递归深度上限。
         *
         * @param maxGenericDepth 最大深度，防止嵌套泛型参数递归过深导致栈溢出，默认 64
         * @return this（链式调用）
         */
        public Builder maxGenericDepth(int maxGenericDepth) {
            this.maxGenericDepth = maxGenericDepth;
            return this;
        }

        /**
         * 设置是否将浮点数解析为 BigDecimal 以保留精度。
         *
         * @param useBigDecimal {@code true} 启用（金融等高精度场景推荐）
         * @return this（链式调用）
         */
        public Builder useBigDecimal(boolean useBigDecimal) {
            this.useBigDecimal = useBigDecimal;
            return this;
        }

        /**
         * 设置是否启用根名称包裹。
         *
         * @param wrapRootValue {@code true} 启用（配合 {@code @JsonRootName} 注解）
         * @return this（链式调用）
         */
        public Builder wrapRootValue(boolean wrapRootValue) {
            this.wrapRootValue = wrapRootValue;
            return this;
        }

        /**
         * 从现有配置创建 Builder（用于修改已有配置）。
         *
         * <p>通过 getter 读取源配置值，而非直接访问字段，
         * 保持封装性并兼容未来的字段变更。</p>
         *
         * @param config 源配置
         * @return 新的 Builder，预填充源配置的值
         */
        public Builder from(JsonConfig config) {
            if (config != null) {
                this.namingStrategy = config.getNamingStrategy();
                this.circularReferenceStrategy = config.getCircularReferenceStrategy();
                this.writeNulls = config.isWriteNulls();
                this.dateFormat = config.getDateFormat();
                this.serializeEnumUsingOrdinal = config.isSerializeEnumUsingOrdinal();
                this.prettyPrint = config.isPrettyPrint();
                this.failOnError = config.isFailOnError();
                this.defaultDateFormat = config.getDefaultDateFormat();
                this.maxJsonSize = config.getMaxJsonSize();
                this.maxDepth = config.getMaxDepth();
                this.maxGenericDepth = config.getMaxGenericDepth();
                this.useBigDecimal = config.isUseBigDecimal();
                this.wrapRootValue = config.isWrapRootValue();
            }
            return this;
        }

        /**
         * 构建不可变的 JsonConfig 实例。
         *
         * <p>通过全参数构造函数一次性写入所有字段，构建后字段值不可修改。
         * 线程安全，无锁可见性保证（final 字段语义）。</p>
         *
         * @return 新的不可变 JsonConfig 实例
         */
        public JsonConfig build() {
            return new JsonConfig(
                    this.namingStrategy,
                    this.circularReferenceStrategy,
                    this.writeNulls,
                    this.dateFormat,
                    this.serializeEnumUsingOrdinal,
                    this.prettyPrint,
                    this.failOnError,
                    this.defaultDateFormat,
                    this.maxJsonSize,
                    this.maxDepth,
                    this.maxGenericDepth,
                    this.useBigDecimal,
                    this.wrapRootValue
            );
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

    /**
     * 配置变更监听器接口。
     *
     * <p>在 {@link JsonConfig#install(JsonConfig)} 完成后回调，接收旧配置、新配置和新版本号。
     * 实现类可使用此接口清理缓存、记录审计日志、刷新状态等。</p>
     *
     * <p><b>线程安全：</b>监听器可能被并发回调，实现需保证线程安全。
     * <b>执行约束：</b>监听器不应执行耗时操作，避免阻塞配置安装流程。</p>
     *
     * @since 1.1.0
     */
    @FunctionalInterface
    public interface ConfigChangeListener {

        /**
         * 配置已变更回调。
         *
         * @param oldConfig  旧配置实例（首次安装时为 null）
         * @param newConfig  新配置实例（不为 null）
         * @param newVersion 新版本号（从 1 开始递增）
         */
        void onConfigChanged(JsonConfig oldConfig, JsonConfig newConfig, long newVersion);
    }
}
