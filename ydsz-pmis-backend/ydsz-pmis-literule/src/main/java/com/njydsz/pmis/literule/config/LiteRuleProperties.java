package com.njydsz.pmis.literule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LiteRule 配置属性
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@ConfigurationProperties(prefix = "pmis.literule")
public class LiteRuleProperties {

    /** 是否启用自动注册内置规则 */
    private boolean autoRegisterBuiltinRules = true;

    /** 是否启用规则热加载（监听 RuleConfigRefreshEvent） */
    private boolean hotReloadEnabled = true;

    /** 是否启用执行统计 */
    private boolean statsEnabled = true;

    /** 是否启用 dry-run 仿真 */
    private boolean dryRunEnabled = true;

    /** 是否启用表达式沙箱（限制危险函数和类访问） */
    private boolean sandboxEnabled = true;

    /**
     * 表达式引擎类型（1.5.0 起）
     *
     * <p>可选值：
     * <ul>
     *   <li>{@code aviator}（默认）- Aviator 5.x，高性能、AST 缓存、函数丰富</li>
     *   <li>{@code qlexpress} - 阿里 QLExpress 3.x，语法更接近 Java，支持流程控制</li>
     * </ul>
     *
     * <p>切换引擎后，已有规则的条件表达式需保证在目标引擎语法下合法。
     * 两者的语法差异：
     * <ul>
     *   <li>Aviator：{@code seq.map("a", 1, "b", 2)}、{@code string.contains(a, b)}</li>
     *   <li>QLExpress：{@code {"a":1, "b":2}}、{@code a.contains(b)}（更像 Java）</li>
     * </ul>
     */
    private String evaluator = "aviator";

    /** 是否启用执行轨迹记录（1.4.0） */
    private boolean traceEnabled = true;

    /** 异步 Trace 队列容量 */
    private int traceQueueCapacity = 5000;

    /** 异步 Trace 批量写入大小 */
    private int traceBatchSize = 100;

    /** 异步 Trace 刷新间隔（毫秒） */
    private long traceFlushIntervalMs = 2000;

    /** 单规则执行超时（毫秒，0 表示不限制，1.4.0） */
    private long ruleTimeoutMs = 0;

    /** 规则熔断错误率阈值（0~1.0，达到阈值时熔断该规则，1.4.0） */
    private double circuitBreakerErrorRate = 0.5;

    /** 规则熔断最小评估次数（达到该次数后才计算错误率，1.4.0） */
    private int circuitBreakerMinEvaluations = 100;

    /**
     * 是否启用规则灰度路由（1.4.0）
     *
     * <p>启用后，对带 canaryRatio > 0 且配置了候选表达式的规则，
     * 按比例将流量分到候选版本，结果会被标记 canary=true。
     */
    private boolean canaryEnabled = true;

    /**
     * 是否启用规则冲突检测（1.4.0）
     *
     * <p>启用后，规则保存前会检测与现有规则的潜在冲突
     * （条件重复、严重度矛盾、命名冲突）。
     */
    private boolean conflictDetectionEnabled = true;

    /**
     * ERROR 级别冲突是否阻塞保存（1.4.0）
     *
     * <p>true：检测到 CONTRADICTORY_SEVERITY 等确定性冲突时抛异常阻塞保存；
     * false：仅记录日志，不阻塞保存。
     */
    private boolean conflictDetectionBlockOnError = true;

    /**
     * AI 增强配置（P2-15）
     */
    private Ai ai = new Ai();

    /**
     * 分布式执行配置（P2-16）
     */
    private Distributed distributed = new Distributed();

    /**
     * 多数据源配置（P1-5）
     *
     * <p>支持从 Nacos / Apollo / ZooKeeper / Redis / File 等配置中心加载规则。
     * 默认 DB（数据库），配置后可切换到配置中心数据源。
     */
    private RuleSourceConfig ruleSource = new RuleSourceConfig();

    /**
     * 文件规则源配置（P2-3 DSL YAML/JSON 规则文件加载）
     *
     * <p>启用后从 classpath 或文件系统加载 YAML/JSON 规则文件，注册为
     * {@link com.njydsz.pmis.literule.spi.FileRuleSource} Bean。
     * 适用于 GitOps 场景：规则以 YAML 文件形式存储在 Git 仓库中，
     * 应用启动时自动加载，文件变更可通过 WatchService 触发热刷新。
     *
     * <p>配置示例：
     * <pre>
     * pmis:
     *   literule:
     *     file-source:
     *       enabled: false
     *       location: classpath:rules/
     *       watch: true
     * </pre>
     *
     * @since 1.7.0
     */
    private FileSourceConfig fileSource = new FileSourceConfig();

    /**
     * 多级缓存配置（P1-1）
     *
     * <p>启用后自动装饰 {@link com.njydsz.pmis.literule.spi.RuleConfigProvider} 为
     * {@link com.njydsz.pmis.literule.cache.CachingRuleConfigProvider}，
     * 实现 Caffeine（L1 本地）+ Redis（L2 分布式）两级缓存，减少 DB 压力。
     *
     * <p>对标银行风控/Drools 优化实践：
     * <ul>
     *   <li>L1 命中直接返回，避免序列化开销</li>
     *   <li>L2 命中回填 L1，跨实例共享缓存</li>
     *   <li>写操作通过 Redis 版本号失效全部节点 L1</li>
     * </ul>
     *
     * @since 1.6.0
     */
    private CacheConfig cache = new CacheConfig();

    /**
     * 声明式注解扫描包路径（P2-10）
     *
     * <p>指定扫描 {@code @LiteRule} / {@code @RuleDefinitionMeta} 注解的基包，逗号分隔。
     * 配置后，这些包下的规则类将在 Spring 启动时被自动注册到引擎。
     * 未配置时仅扫描 {@code @LiteRule} 标注的已注册 Spring Bean（无需指定包），
     * 而 {@code @RuleDefinitionMeta} 类扫描需显式配置本项以提高扫描性能。
     *
     * @since 1.5.2
     */
    private String annotationScanBasePackages = "";

    /**
     * 当前运行环境（P1-5 多环境隔离）
     *
     * <p>可选值：
     * <ul>
     *   <li>{@code default}（默认）- 全环境生效，向后兼容</li>
     *   <li>{@code dev} - 开发环境</li>
     *   <li>{@code staging} - 预发环境</li>
     *   <li>{@code prod} - 生产环境</li>
     * </ul>
     *
     * <p>配置后，引擎评估时仅放行 environment 为 {@code "default"} 或与本配置匹配的规则。
     * 用于 dev/staging/prod 环境的规则隔离，避免开发环境的测试规则在生产环境触发。
     *
     * @since 1.6.0
     */
    private String environment = "default";

    /**
     * AI 增强配置
     *
     * <p>支持自然语言转规则表达式、规则推荐、健康度评分。
     * LLM 客户端通过 OpenAI 兼容协议接入，可在不修改代码的情况下
     * 切换 OpenAI / DeepSeek / 通义千问 / Ollama 等不同提供方。
     */
    @Data
    public static class Ai {

        /** 是否启用 AI 增强 */
        private boolean enabled = false;

        /** LLM 客户端类型：OPENAI_COMPATIBLE / MOCK（默认 MOCK，便于开发） */
        private String llmClient = "MOCK";

        /** LLM API 地址（OpenAI 兼容协议 chat/completions 端点） */
        private String llmApiUrl = "https://api.openai.com/v1/chat/completions";

        /** LLM API Key */
        private String llmApiKey = "";

        /** LLM 模型名称 */
        private String llmModel = "gpt-4o-mini";

        /** LLM 调用超时（毫秒） */
        private long llmTimeoutMs = 15000;

        /** LLM 调用温度（0~1.0，越低越稳定） */
        private double llmTemperature = 0.2;

        /** 健康度评分：命中率权重（0~1.0） */
        private double healthHitRateWeight = 0.30;

        /** 健康度评分：错误率权重（0~1.0） */
        private double healthErrorRateWeight = 0.30;

        /** 健康度评分：复杂度权重（0~1.0） */
        private double healthComplexityWeight = 0.20;

        /** 健康度评分：覆盖率权重（0~1.0） */
        private double healthCoverageWeight = 0.20;

        /** 健康度评分：复杂度上限（表达式 token 数，超过该值视为复杂） */
        private int healthComplexityThreshold = 80;

        /** 推荐结果最大返回条数 */
        private int recommendTopN = 10;
    }

    /**
     * 分布式执行配置
     *
     * <p>启用后规则引擎按一致性 hash 将规则分片到集群节点，
     * 每个节点只执行属于自己的规则，避免重复计算。
     *
     * @since 1.5.0
     */
    @Data
    public static class Distributed {

        /** 是否启用分布式分片执行 */
        private boolean enabled = false;

        /** 虚拟节点数（默认 150，越大越均匀） */
        private int virtualNodes = 150;

        /** 节点列表刷新间隔（毫秒） */
        private long refreshIntervalMs = 10_000L;

        /** 心跳超时时间（毫秒，超过此时间未心跳的节点视为下线） */
        private long heartbeatTimeoutMs = 30_000L;

        /** 心跳发送间隔（毫秒） */
        private long heartbeatIntervalMs = 5_000L;
    }

    /**
     * 规则数据源配置（P1-5）
     *
     * <p>支持从多种数据源加载规则定义，默认 DB（数据库）。
     *
     * <p>配置示例：
     * <pre>
     * pmis:
     *   literule:
     *     rule-source:
     *       type: nacos          # nacos / apollo / zookeeper / redis / file / db
     *       nacos:
     *         server-addr: 127.0.0.1:8848
     *         data-id: rule-definitions
     *         group: DEFAULT_GROUP
     *       apollo:
     *         namespace: rule-engine
     *       zookeeper:
     *         connect-string: 127.0.0.1:2181
     *         path: /literule/definitions
     * </pre>
     *
     * @since 1.6.0
     */
    @Data
    public static class RuleSourceConfig {

        /** 数据源类型：db（默认）/ nacos / apollo / zookeeper / redis / file */
        private String type = "db";

        /** Nacos 数据源配置 */
        private NacosConfig nacos = new NacosConfig();

        /** Apollo 数据源配置 */
        private ApolloConfig apollo = new ApolloConfig();

        /** ZooKeeper 数据源配置 */
        private ZookeeperConfig zookeeper = new ZookeeperConfig();

        /** 是否启用 Watch 监听（仅支持 Watch 的数据源有效） */
        private boolean watchEnabled = true;
    }

    @Data
    public static class NacosConfig {
        /** Nacos 服务地址 */
        private String serverAddr = "127.0.0.1:8848";
        /** 配置 Data ID */
        private String dataId = "rule-definitions";
        /** 配置 Group */
        private String group = "DEFAULT_GROUP";
    }

    @Data
    public static class ApolloConfig {
        /** Apollo Namespace */
        private String namespace = "rule-engine";
    }

    @Data
    public static class ZookeeperConfig {
        /** ZK 连接地址 */
        private String connectString = "127.0.0.1:2181";
        /** 规则定义节点路径 */
        private String path = "/literule/definitions";
    }

    /**
     * 多级缓存配置（P1-1）
     *
     * <p>控制 Caffeine（L1）+ Redis（L2）两级缓存行为。
     *
     * @since 1.6.0
     */
    @Data
    public static class CacheConfig {

        /** 是否启用多级缓存（关闭后直接透传到 delegate） */
        private boolean enabled = true;

        /** L1（Caffeine 本地）TTL，单位秒 */
        private int l1TtlSeconds = 60;

        /** L1 最大条数 */
        private int l1MaxSize = 1000;

        /** L2（Redis 分布式）TTL，单位秒 */
        private int l2TtlSeconds = 300;

        /**
         * 是否启用 L2（需 Redisson 在 classpath）
         *
         * <p>true：RedissonClient 可用时启用 L2；
         * false：强制仅用 L1，即便 RedissonClient 存在也不使用。
         */
        private boolean l2Enabled = true;
    }

    /**
     * 文件规则源配置（P2-3）
     *
     * <p>控制 {@link com.njydsz.pmis.literule.spi.FileRuleSource} 的加载行为。
     *
     * @since 1.7.0
     */
    @Data
    public static class FileSourceConfig {

        /**
         * 是否启用文件规则源
         *
         * <p>true：启动时加载 YAML/JSON 规则文件并注册 FileRuleSource Bean；
         * false（默认）：不加载，规则仍从 DB / 配置中心获取。
         */
        private boolean enabled = false;

        /**
         * 规则文件位置
         *
         * <p>支持的格式：
         * <ul>
         *   <li>{@code classpath:rules/} - classpath 目录（默认）</li>
         *   <li>{@code classpath:rules/risk.yml} - 单个 classpath 文件</li>
         *   <li>{@code file:/etc/rules/} - 文件系统目录</li>
         *   <li>{@code file:/etc/rules/risk.yml} - 单个文件系统文件</li>
         * </ul>
         * 不带前缀时默认按 classpath 处理。
         */
        private String location = "classpath:rules/";

        /**
         * 是否启用文件变更监听（WatchService）
         *
         * <p>true：文件变更后自动重载并通知监听器；
         * false：仅启动时加载一次。
         * 仅对文件系统目录有效，classpath 内资源（jar 包内）无法监听。
         */
        private boolean watch = true;
    }
}
