package com.njydsz.literule.server.config;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * LiteRule 配置属性
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.literule")
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

  /** 是否启用执行轨迹记录（1.4.0） */
  private boolean traceEnabled = true;

  /** 异步 Trace 队列容量 */
  @Min(1)
  private int traceQueueCapacity = 5000;

  /** 异步 Trace 批量写入大小 */
  @Min(1)
  private int traceBatchSize = 100;

  /** 异步 Trace 刷新间隔（毫秒） */
  @Min(1)
  private long traceFlushIntervalMs = 2000;

  /** 单规则执行超时（毫秒，0 表示不限制，1.4.0） */
  @Min(0)
  private long ruleTimeoutMs = 0;

  /** 规则熔断错误率阈值（0~1.0，达到阈值时熔断该规则，1.4.0） */
  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double circuitBreakerErrorRate = 0.5;

  /** 规则熔断最小评估次数（达到该次数后才计算错误率，1.4.0） */
  @Min(0)
  private int circuitBreakerMinEvaluations = 100;

  /**
   * 规则熔断 OPEN 状态持续时间（毫秒，P2-14）
   *
   * <p>熔断器进入 OPEN 状态后，持续该时长后转为 HALF_OPEN，允许试探性评估。 默认 30000ms（30 秒），与 Resilience4j 默认
   * waitDurationInOpenState 对齐。
   */
  private long circuitBreakerOpenStateMs = 30_000L;

  /**
   * 是否启用规则灰度路由（1.4.0）
   *
   * <p>启用后，对带 canaryRatio > 0 且配置了候选表达式的规则， 按比例将流量分到候选版本，结果会被标记 canary=true。
   */
  private boolean canaryEnabled = true;

  /**
   * 是否启用规则冲突检测（1.4.0）
   *
   * <p>启用后，规则保存前会检测与现有规则的潜在冲突 （条件重复、严重度矛盾、命名冲突）。
   */
  private boolean conflictDetectionEnabled = true;

  /**
   * ERROR 级别冲突是否阻塞保存（1.4.0）
   *
   * <p>true：检测到 CONTRADICTORY_SEVERITY 等确定性冲突时抛异常阻塞保存； false：仅记录日志，不阻塞保存。
   */
  private boolean conflictDetectionBlockOnError = true;

  /** 分布式执行配置（P2-16） */
  private Distributed distributed = new Distributed();

  /**
   * 多数据源配置（P1-5）
   *
   * <p>支持从 Nacos / Apollo / ZooKeeper / Redis / File 等配置中心加载规则。 默认 DB（数据库），配置后可切换到配置中心数据源。
   */
  private RuleSourceConfig ruleSource = new RuleSourceConfig();

  /**
   * 文件规则源配置（P2-3 DSL YAML/JSON 规则文件加载）
   *
   * <p>启用后从 classpath 或文件系统加载 YAML/JSON 规则文件，注册为 {@link
   * com.njydsz.literule.server.spi.FileRuleSource} Bean。 适用于 GitOps 场景：规则以 YAML 文件形式存储在 Git 仓库中，
   * 应用启动时自动加载，文件变更可通过 WatchService 触发热刷新。
   *
   * <p>配置示例：
   *
   * <pre>
   * ydsz:
   *   literule:
   *     file-source:
   *       enabled: false
   *       location: classpath:rules/
   *       watch: true
   * </pre>
   *
   * @since 1.0.0
   */
  private FileSourceConfig fileSource = new FileSourceConfig();

  /**
   * 多级缓存配置（P1-1）
   *
   * <p>启用后自动装饰 {@link com.njydsz.literule.server.spi.RuleConfigProvider} 为 {@link
   * com.njydsz.literule.server.cache.CachingRuleConfigProvider}， 实现 Caffeine（L1 本地）+ Redis（L2
   * 分布式）两级缓存，减少 DB 压力。
   *
   * <p>对标银行风控/Drools 优化实践：
   *
   * <ul>
   *   <li>L1 命中直接返回，避免序列化开销
   *   <li>L2 命中回填 L1，跨实例共享缓存
   *   <li>写操作通过 Redis 版本号失效全部节点 L1
   * </ul>
   *
   * @since 1.0.0
   */
  private CacheConfig cache = new CacheConfig();

  /**
   * 声明式注解扫描包路径（P2-10）
   *
   * <p>指定扫描 {@code @LiteRule} / {@code @RuleDefinitionMeta} 注解的基包，逗号分隔。 配置后，这些包下的规则类将在 Spring
   * 启动时被自动注册到引擎。 未配置时仅扫描 {@code @LiteRule} 标注的已注册 Spring Bean（无需指定包）， 而 {@code @RuleDefinitionMeta}
   * 类扫描需显式配置本项以提高扫描性能。
   *
   * @since 1.0.0
   */
  private String annotationScanBasePackages = "";

  /**
   * 默认租户 ID（P2-T4：消除硬编码）
   *
   * <p>SDK 和自动注册规则的默认租户标识。未在规则定义中显式指定 tenantId 时， 引擎使用此值作为默认租户。
   *
   * <p>默认值 {@code "1"}（向后兼容）。多租户场景下通过 {@code ydsz.literule.default-tenant-id} 配置覆盖。
   *
   * @since 1.0.0
   */
  private String defaultTenantId = "1";

  /**
   * 多租户隔离配置（P0-F3）
   *
   * <p>控制规则引擎的多租户物理隔离校验。 对标大厂金融级规则引擎：默认逻辑隔离（行级 tenant_id）， 可通过
   * {@code physical-isolation-required=true} 强制要求物理隔离（SCHEMA/ISOLATE_DB）， 校验不通过时启动失败（fail-fast），
   * 避免高合规场景下误用逻辑隔离导致租户数据串扰。
   *
   * @since 1.0.0
   */
  private TenantConfig tenant = new TenantConfig();

  /**
   * 当前运行环境（P1-5 多环境隔离）
   *
   * <p>可选值：
   *
   * <ul>
   *   <li>{@code default}（默认）- 全环境生效，向后兼容
   *   <li>{@code dev} - 开发环境
   *   <li>{@code staging} - 预发环境
   *   <li>{@code prod} - 生产环境
   * </ul>
   *
   * <p>配置后，引擎评估时仅放行 environment 为 {@code "default"} 或与本配置匹配的规则。 用于 dev/staging/prod
   * 环境的规则隔离，避免开发环境的测试规则在生产环境触发。
   *
   * @since 1.0.0
   */
  private String environment = "default";

  /**
   * 规则+模型融合配置（P3-1）
   *
   * <p>启用后，规则引擎在评估前会调用所有已注册的 {@link com.njydsz.literule.domain.model.ModelInputProvider} 获取模型输出，注入到
   * {@link com.njydsz.literule.api.RuleContext} 的 facts 中， 使规则表达式可通过 {@code model.<field>} 引用（如
   * {@code model.score > 0.8}）。
   *
   * <p>对标滴滴 Newton、字节风控的"规则+模型融合"能力：
   *
   * <ul>
   *   <li>规则兜底模型异常：模型不可用时降级为纯规则评估
   *   <li>模型输出触发规则：模型输出作为规则条件输入
   * </ul>
   *
   * <p>配置示例：
   *
   * <pre>
   * ydsz:
   *   literule:
   *     model:
   *       enabled: true
   *       timeout-ms: 100
   *       fallback-on-error: true
   *       mock-enabled: false
   * </pre>
   *
   * @since 1.0.0
   */
  private ModelConfig model = new ModelConfig();

  /**
   * 动态事实采集配置（P0-2）
   *
   * <p>启用后，规则引擎在评估前会调用所有已注册的 {@link com.njydsz.literule.server.spi.FactProvider}
   * 从外部数据源（DB、Redis、HTTP API 等）动态采集事实数据， 合并到 {@link com.njydsz.literule.api.RuleContext} 的 facts 中。
   *
   * <p>事实采集在模型注入之前执行，采集的事实可供模型 provider 使用。
   *
   * <p>配置示例：
   *
   * <pre>
   * ydsz:
   *   literule:
   *     fact:
   *       enabled: true
   *       timeout-ms: 200
   *       fallback-on-error: true
   * </pre>
   *
   * @since 1.0.0
   */
  private FactConfig fact = new FactConfig();

  /**
   * 注入线程池配置（P1-3）
   *
   * <p>控制事实采集和模型注入并行执行的线程池大小。
   * 默认值根据 CPU 核数动态计算，确保在不同规格机器上都能获得合理的并发度。
   *
   * @since 1.0.0
   */
  private InjectionConfig injection = new InjectionConfig();

  /**
   * 高性能优化配置（P2-3）
   *
   * <p>控制评估结果缓存与规则分组并行评估。
   *
   * @since 1.0.0
   */
  private PerformanceConfig performance = new PerformanceConfig();

  /**
   * 规则生命周期管理配置（P3-1）
   *
   * <p>控制退役检测的阈值参数，用于自动识别应退役的规则。
   *
   * @since 1.0.0
   */
  private LifecycleConfig lifecycle = new LifecycleConfig();

  /**
   * 分布式执行配置
   *
   * <p>启用后规则引擎按一致性 hash 将规则分片到集群节点， 每个节点只执行属于自己的规则，避免重复计算。
   *
   * @since 1.0.0
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
   *
   * <pre>
   * ydsz:
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
   * @since 1.0.0
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

  /** Nacos 配置中心接入配置（规则定义动态下发）。 */
  @Data
  public static class NacosConfig {
    /** Nacos 服务地址 */
    private String serverAddr = "127.0.0.1:8848";

    /** 配置 Data ID */
    private String dataId = "rule-definitions";

    /** 配置 Group */
    private String group = "DEFAULT_GROUP";
  }

  /** Apollo 配置中心接入配置。 */
  @Data
  public static class ApolloConfig {
    /** Apollo Namespace */
    private String namespace = "rule-engine";
  }

  /** Zookeeper 配置中心接入配置。 */
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
   * @since 1.0.0
   */
  @Data
  public static class CacheConfig {

    /** 是否启用多级缓存（关闭后直接透传到 delegate） */
    private boolean enabled = true;

    /** L1（Caffeine 本地）TTL，单位秒 */
    @Min(1)
    private int l1TtlSeconds = 60;

    /** L1 最大条数 */
    @Min(1)
    private int l1MaxSize = 1000;

    /** L2（Redis 分布式）TTL，单位秒 */
    @Min(1)
    private int l2TtlSeconds = 300;

    /**
     * 是否启用 L2（需 Redisson 在 classpath）
     *
     * <p>true：RedissonClient 可用时启用 L2； false：强制仅用 L1，即便 RedissonClient 存在也不使用。
     */
    private boolean l2Enabled = true;
  }

  /**
   * 文件规则源配置（P2-3）
   *
   * <p>控制 {@link com.njydsz.literule.server.spi.FileRuleSource} 的加载行为。
   *
   * @since 1.0.0
   */
  @Data
  public static class FileSourceConfig {

    /**
     * 是否启用文件规则源
     *
     * <p>true：启动时加载 YAML/JSON 规则文件并注册 FileRuleSource Bean； false（默认）：不加载，规则仍从 DB / 配置中心获取。
     */
    private boolean enabled = false;

    /**
     * 规则文件位置
     *
     * <p>支持的格式：
     *
     * <ul>
     *   <li>{@code classpath:rules/} - classpath 目录（默认）
     *   <li>{@code classpath:rules/risk.yml} - 单个 classpath 文件
     *   <li>{@code file:/etc/rules/} - 文件系统目录
     *   <li>{@code file:/etc/rules/risk.yml} - 单个文件系统文件
     * </ul>
     *
     * 不带前缀时默认按 classpath 处理。
     */
    private String location = "classpath:rules/";

    /**
     * 是否启用文件变更监听（WatchService）
     *
     * <p>true：文件变更后自动重载并通知监听器； false：仅启动时加载一次。 仅对文件系统目录有效，classpath 内资源（jar 包内）无法监听。
     */
    private boolean watch = true;
  }

  /**
   * 规则+模型融合配置（P3-1）
   *
   * <p>控制 {@link com.njydsz.literule.domain.model.ModelInputRegistry} 与 {@link
   * com.njydsz.literule.domain.model.MockModelInputProvider} 的行为。 默认关闭（{@code
   * enabled=false}），需显式启用以保证向后兼容。
   *
   * @since 1.0.0
   */
  @Data
  public static class ModelConfig {

    /**
     * 是否启用规则+模型融合
     *
     * <p>true：规则引擎评估前调用已注册的 ModelInputProvider 注入模型输出； false（默认）：不调用，规则表达式引用 {@code model.xxx}
     * 将因变量不存在而返回 false。 向后兼容：默认关闭，不影响现有规则评估。
     */
    private boolean enabled = false;

    /**
     * 单个模型调用超时（毫秒）
     *
     * <p>每个 {@link com.njydsz.literule.domain.model.ModelInputProvider} 调用最多等待该时长， 超时则取消并返回空
     * Map（或抛异常，取决于 {@link #fallbackOnError}）。 默认 100ms，对标在线风控引擎性能要求。
     */
    private long timeoutMs = 100;

    /**
     * 模型异常时是否降级
     *
     * <p>true（默认）：模型调用失败时记录 WARN 日志，不注入模型变量， 规则表达式引用 {@code model.xxx} 将返回 false（变量不存在），规则评估继续；
     * false：模型调用失败时抛出 {@link com.njydsz.literule.domain.model.ModelInvocationException}，
     * 中断规则评估流程。适用于"模型必须可用"的强一致场景。
     */
    private boolean fallbackOnError = true;

    /**
     * 是否启用 Mock 模型提供者
     *
     * <p>true：自动注册 {@link com.njydsz.literule.domain.model.MockModelInputProvider}，
     * 返回配置的模拟模型输出，便于开发/测试； false（默认）：不注册 Mock，需业务方提供真实 {@link
     * com.njydsz.literule.domain.model.ModelInputProvider} 实现。
     */
    private boolean mockEnabled = false;

    /**
     * Mock 模型输出（仅当 {@link #mockEnabled}=true 时生效）
     *
     * <p>key 为模型字段名（无需 "model." 前缀），value 为数值/字符串/布尔。 未配置时使用 MockModelInputProvider
     * 默认值（modelScore=0.75, predictProbability=0.05）。
     */
    private Map<String, Object> mockOutputs = new LinkedHashMap<>();
  }

  /**
   * 动态事实采集配置（P0-2）
   *
   * <p>控制 {@link com.njydsz.literule.server.spi.FactProviderRegistry} 的行为。 默认关闭（{@code
   * enabled=false}），需显式启用以保证向后兼容。
   *
   * @since 1.0.0
   */
  @Data
  public static class FactConfig {

    /**
     * 是否启用动态事实采集
     *
     * <p>true：规则引擎评估前调用已注册的 FactProvider 从外部数据源采集事实； false（默认）：不调用，规则仅使用调用方传入的 facts。
     * 向后兼容：默认关闭，不影响现有规则评估。
     */
    private boolean enabled = false;

    /**
     * 单个 provider 调用超时（毫秒）
     *
     * <p>每个 {@link com.njydsz.literule.server.spi.FactProvider} 调用最多等待该时长， 超时则取消并返回空 Map（或抛异常，取决于
     * {@link #fallbackOnError}）。 默认 200ms，适用于大多数 DB/Redis 查询场景。
     */
    private long timeoutMs = 200;

    /**
     * provider 异常时是否降级
     *
     * <p>true（默认）：provider 调用失败时记录 WARN 日志，跳过该 provider， 规则评估继续使用已采集的事实； false：provider 调用失败时抛出
     * {@link com.njydsz.literule.server.spi.FactCollectionException}， 中断规则评估流程。适用于"事实必须可用"的强一致场景。
     */
    private boolean fallbackOnError = true;
  }

  /**
   * 高性能优化配置（P2-3）
   *
   * <p>控制评估结果缓存与规则分组并行评估，提升大规则量场景下的评估吞吐。
   *
   * @since 1.0.0
   */
  @Data
  public static class PerformanceConfig {

    /**
     * 是否启用评估结果缓存
     *
     * <p>true：相同上下文（scenario+tenant+environment+facts）在 TTL 内复用缓存结果； false（默认）：每次评估都重新计算。
     * 适用于批量回放、风控试运行等重复评估率高的场景。
     */
    private boolean cacheEnabled = false;

    /** 缓存 TTL（秒），默认 300（5 分钟） */
    private int cacheTtlSeconds = 300;

    /** 缓存最大条目数，默认 10000 */
    private int cacheMaxSize = 10_000;

    /**
     * 是否启用规则分组并行评估
     *
     * <p>true：将候选规则按互斥组分组，组间并行评估； false（默认）：串行评估。 适用于规则数 > 100 且评估耗时敏感的场景。
     */
    private boolean parallelEnabled = false;

    /** 并行评估线程池大小，默认 CPU 核数 */
    @Min(0)
    private int parallelPoolSize = Math.max(2, Runtime.getRuntime().availableProcessors());

    /**
     * 并行评估触发阈值
     *
     * <p>候选规则数 ≥ 此值时自动切换为并行评估。 默认 50，适用于规则数较大的场景。
     */
    @Min(1)
    private int parallelThreshold = 50;

    /**
     * 慢规则告警阈值（毫秒，P2-4）
     *
     * <p>单规则评估耗时 ≥ 此值时记录慢规则告警：
     *
     * <ul>
     *   <li>输出 WARN 日志 {@code [LiteRule-SlowRule] rule=,elapsed=,threshold=}
     *   <li>当 Micrometer 可用时，递增 Prometheus 计数器 {@code literule_slow_rule_total{rule_code,}}
     * </ul>
     *
     * 0（默认）表示关闭慢规则检测。 推荐生产环境设置为 100~500ms，对标在线风控引擎性能要求。
     *
     * @since 1.0.0
     */
    private long slowRuleThresholdMs = 0L;
  }

  /**
   * 规则生命周期管理配置（P3-1）
   *
   * <p>控制退役检测的阈值参数。当规则满足以下任一条件时， {@link com.njydsz.literule.server.core.RuleLifecycleService}
   * 将生成退役建议：
   *
   * <ul>
   *   <li>休眠规则：评估次数 ≥ {@link #dormantMinEvaluations} 且触发次数 = 0
   *   <li>高错误率：错误率 ≥ {@link #highErrorRateThreshold}
   *   <li>长期停用：已停用超过 {@link #staleDisabledDays} 天
   *   <li>低影响：触发率 &lt; {@link #lowImpactTriggerRate} 且评估次数 ≥ {@link #minSampleSize}
   * </ul>
   *
   * @since 1.0.0
   */
  @Data
  public static class LifecycleConfig {

    /**
     * 是否启用退役检测
     *
     * <p>true（默认）：自动装配 {@link com.njydsz.literule.server.core.RuleLifecycleService}；
     * false：不装配，退役检测功能不可用。
     */
    private boolean enabled = true;

    /**
     * 休眠规则最小评估次数
     *
     * <p>当规则评估次数达到此值且触发次数为 0 时，判定为休眠规则。 默认 1000 次。
     */
    @Min(0)
    private long dormantMinEvaluations = 1000;

    /**
     * 高错误率阈值（0~1.0）
     *
     * <p>当规则错误率 ≥ 此值时，判定为高错误率规则。 默认 0.30（30%）。
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double highErrorRateThreshold = 0.30;

    /**
     * 长期停用天数
     *
     * <p>规则处于 DISABLED 状态超过此天数时，判定为长期停用规则。 默认 90 天。
     */
    private int staleDisabledDays = 90;

    /**
     * 低影响触发率阈值（0~1.0）
     *
     * <p>当规则触发率 &lt; 此值且评估次数 ≥ minSampleSize 时，判定为低影响规则。 默认 0.001（0.1%）。
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double lowImpactTriggerRate = 0.001;

    /**
     * 最小样本量
     *
     * <p>评估次数低于此值的规则不参与退役判定（数据不足）。 长期停用检测不受此限制。 默认 500 次。
     */
    @Min(0)
    private long minSampleSize = 500;
  }

  /**
   * 多租户隔离配置（P0-F3）
   *
   * <p>配置示例：
   *
   * <pre>
   * ydsz:
   *   literule:
   *     tenant:
   *       physical-isolation-required: true   # 强制要求物理隔离
   * </pre>
   *
   * @since 1.0.0
   */
  @Data
  public static class TenantConfig {

    /**
     * 是否强制要求多租户物理隔离
     *
     * <p>true：启动时校验 {@code ydsz.common.tenant.mode} 必须为 SCHEMA 或 ISOLATE_DB， 否则启动失败（fail-fast）。
     * 适用于金融/合规等高隔离要求场景。 false（默认）：不校验，兼容逻辑隔离部署。
     */
    private boolean physicalIsolationRequired = false;
  }

  /**
   * 注入线程池配置（P1-3）
   *
   * <p>控制事实采集和模型注入并行执行的线程池大小（{@code injectionExecutor}）。
   *
   * @since 1.0.0
   */
  @Data
  public static class InjectionConfig {

    /**
     * 注入线程池大小
     *
     * <p>用于事实采集和模型注入的并行执行线程数量。
     * 默认值为 {@code max(4, CPU 核数 * 2)}，适用于大多数 FactProvider 和 ModelProvider 组合场景。
     * 如果注册了大量 provider（>5），建议适当增加池大小以避免注入成为瓶颈。
     */
    @Min(1)
    private int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);

    /**
     * 注入线程保持活跃时间（秒）
     *
     * <p>超过核心线程数的空闲线程在终止前等待新任务的最长时间。
     * 默认 60 秒。
     */
    @Min(0)
    private long keepAliveSeconds = 60L;
  }
}
