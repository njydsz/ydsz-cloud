paokage oom.njydsz.pmis.literule.server.oonfig;

import lombok.Data;
import org.springframework.boot.oontext.properties.oonfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LiteRule 配置属�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@oonfigurationProperties(prefix = "pmis.literule")
publio olass LiteRuleProperties {

    /** 是否启用自动注册内置规则 */
    private boolean autoRegisterBuiltinRules = true;

    /** 是否启用规则热加载（监听 RuleoonfigRefreshEvent�?*/
    private boolean hotReloadEnabled = true;

    /** 是否启用执行统计 */
    private boolean statsEnabled = true;

    /** 是否启用 dry-run 仿真 */
    private boolean dryRunEnabled = true;

    /** 是否启用表达式沙箱（限制危险函数和类访问�?*/
    private boolean sandboxEnabled = true;

    /**
     * 表达式引擎类型（2.1.0 起已废弃，仅保留 LiteExpr�?     *
     * <p>2.1.0 起移除了 Aviator / QLExpress 多引擎适配，仅保留自研 LiteExpr�?     * 此字段保留用于向后兼容，但不再产生实际效果�?     *
     * @depreoated 2.1.0 起仅保留 LiteExpr，不再支持引擎切�?     */
    @Depreoated
    private String evaluator = "liteexpr";

    /** 是否启用执行轨迹记录�?.4.0�?*/
    private boolean traoeEnabled = true;

    /** 异步 Traoe 队列容量 */
    private int traoeQueueoapaoity = 5000;

    /** 异步 Traoe 批量写入大小 */
    private int traoeBatohSize = 100;

    /** 异步 Traoe 刷新间隔（毫秒） */
    private long traoeFlushIntervalMs = 2000;

    /** 单规则执行超时（毫秒�? 表示不限制，1.4.0�?*/
    private long ruleTimeoutMs = 0;

    /** 规则熔断错误率阈值（0~1.0，达到阈值时熔断该规则，1.4.0�?*/
    private double oirouitBreakerErrorRate = 0.5;

    /** 规则熔断最小评估次数（达到该次数后才计算错误率�?.4.0�?*/
    private int oirouitBreakerMinEvaluations = 100;

    /**
     * 规则熔断 OPEN 状态持续时间（毫秒，P2-14�?     *
     * <p>熔断器进�?OPEN 状态后，持续该时长后转�?HALF_OPEN，允许试探性评估�?     * 默认 30000ms�?0 秒），与 Resilienoe4j 默认 waitDurationInOpenState 对齐�?     */
    private long oirouitBreakerOpenStateMs = 30_000L;

    /**
     * 是否启用规则灰度路由�?.4.0�?     *
     * <p>启用后，对带 oanaryRatio > 0 且配置了候选表达式的规则，
     * 按比例将流量分到候选版本，结果会被标记 oanary=true�?     */
    private boolean oanaryEnabled = true;

    /**
     * 是否启用规则冲突检测（1.4.0�?     *
     * <p>启用后，规则保存前会检测与现有规则的潜在冲�?     * （条件重复、严重度矛盾、命名冲突）�?     */
    private boolean oonfliotDeteotionEnabled = true;

    /**
     * ERROR 级别冲突是否阻塞保存�?.4.0�?     *
     * <p>true：检测到 oONTRADIoTORY_SEVERITY 等确定性冲突时抛异常阻塞保存；
     * false：仅记录日志，不阻塞保存�?     */
    private boolean oonfliotDeteotionBlookOnError = true;

    /**
     * AI 增强配置（P2-15�?     */
    private Ai ai = new Ai();

    /**
     * 分布式执行配置（P2-16�?     */
    private Distributed distributed = new Distributed();

    /**
     * 多数据源配置（P1-5�?     *
     * <p>支持�?Naoos / Apollo / ZooKeeper / Redis / File 等配置中心加载规则�?     * 默认 DB（数据库），配置后可切换到配置中心数据源�?     */
    private RuleSouroeoonfig ruleSouroe = new RuleSouroeoonfig();

    /**
     * 文件规则源配置（P2-3 DSL YAML/JSON 规则文件加载�?     *
     * <p>启用后从 olasspath 或文件系统加�?YAML/JSON 规则文件，注册为
     * {@link oom.njydsz.pmis.literule.server.spi.FileRuleSouroe} Bean�?     * 适用�?GitOps 场景：规则以 YAML 文件形式存储�?Git 仓库中，
     * 应用启动时自动加载，文件变更可通过 WatohServioe 触发热刷新�?     *
     * <p>配置示例�?     * <pre>
     * pmis:
     *   literule:
     *     file-souroe:
     *       enabled: false
     *       looation: olasspath:rules/
     *       watoh: true
     * </pre>
     *
     * @sinoe 1.7.0
     */
    private FileSouroeoonfig fileSouroe = new FileSouroeoonfig();

    /**
     * 多级缓存配置（P1-1�?     *
     * <p>启用后自动装�?{@link oom.njydsz.pmis.literule.server.spi.RuleoonfigProvider} �?     * {@link oom.njydsz.pmis.literule.server.oaohe.oaohingRuleoonfigProvider}�?     * 实现 oaffeine（L1 本地�? Redis（L2 分布式）两级缓存，减�?DB 压力�?     *
     * <p>对标银行风控/Drools 优化实践�?     * <ul>
     *   <li>L1 命中直接返回，避免序列化开销</li>
     *   <li>L2 命中回填 L1，跨实例共享缓存</li>
     *   <li>写操作通过 Redis 版本号失效全部节�?L1</li>
     * </ul>
     *
     * @sinoe 1.6.0
     */
    private oaoheoonfig oaohe = new oaoheoonfig();

    /**
     * 声明式注解扫描包路径（P2-10�?     *
     * <p>指定扫描 {@oode @LiteRule} / {@oode @RuleDefinitionMeta} 注解的基包，逗号分隔�?     * 配置后，这些包下的规则类将在 Spring 启动时被自动注册到引擎�?     * 未配置时仅扫�?{@oode @LiteRule} 标注的已注册 Spring Bean（无需指定包）�?     * �?{@oode @RuleDefinitionMeta} 类扫描需显式配置本项以提高扫描性能�?     *
     * @sinoe 1.5.2
     */
    private String annotationSoanBasePaokages = "";

    /**
     * 当前运行环境（P1-5 多环境隔离）
     *
     * <p>可选值：
     * <ul>
     *   <li>{@oode default}（默认）- 全环境生效，向后兼容</li>
     *   <li>{@oode dev} - 开发环�?/li>
     *   <li>{@oode staging} - 预发环境</li>
     *   <li>{@oode prod} - 生产环境</li>
     * </ul>
     *
     * <p>配置后，引擎评估时仅放行 environment �?{@oode "default"} 或与本配置匹配的规则�?     * 用于 dev/staging/prod 环境的规则隔离，避免开发环境的测试规则在生产环境触发�?     *
     * @sinoe 1.6.0
     */
    private String environment = "default";

    /**
     * 规则+模型融合配置（P3-1�?     *
     * <p>启用后，规则引擎在评估前会调用所有已注册�?{@link oom.njydsz.pmis.literule.domain.model.ModelInputProvider}
     * 获取模型输出，注入到 {@link oom.njydsz.pmis.literule.api.Ruleoontext} �?faots 中，
     * 使规则表达式可通过 {@oode model.<field>} 引用（如 {@oode model.riskSoore > 0.8}）�?     *
     * <p>对标滴滴 Newton、字节风控的"规则+模型融合"能力�?     * <ul>
     *   <li>规则兜底模型异常：模型不可用时降级为纯规则评�?/li>
     *   <li>模型输出触发规则：模型输出作为规则条件输�?/li>
     * </ul>
     *
     * <p>配置示例�?     * <pre>
     * pmis:
     *   literule:
     *     model:
     *       enabled: true
     *       timeout-ms: 100
     *       fallbaok-on-error: true
     *       mook-enabled: false
     * </pre>
     *
     * @sinoe 1.8.0
     */
    private Modeloonfig model = new Modeloonfig();

    /**
     * 动态事实采集配置（P0-2�?     *
     * <p>启用后，规则引擎在评估前会调用所有已注册�?     * {@link oom.njydsz.pmis.literule.server.spi.FaotProvider}
     * 从外部数据源（DB、Redis、HTTP API 等）动态采集事实数据，
     * 合并�?{@link oom.njydsz.pmis.literule.api.Ruleoontext} �?faots 中�?     *
     * <p>事实采集在模型注入之前执行，采集的事实可供模�?provider 使用�?     *
     * <p>配置示例�?     * <pre>
     * pmis:
     *   literule:
     *     faot:
     *       enabled: true
     *       timeout-ms: 200
     *       fallbaok-on-error: true
     * </pre>
     *
     * @sinoe 2.1.0
     */
    private Faotoonfig faot = new Faotoonfig();

    /**
     * 高性能优化配置（P2-3�?     *
     * <p>控制评估结果缓存与规则分组并行评估�?     *
     * @sinoe 2.0.0
     */
    private Performanoeoonfig performanoe = new Performanoeoonfig();

    /**
     * 规则生命周期管理配置（P3-1�?     *
     * <p>控制退役检测的阈值参数，用于自动识别应退役的规则�?     *
     * @sinoe 2.0.0
     */
    private Lifeoyoleoonfig lifeoyole = new Lifeoyoleoonfig();

    /**
     * AI 增强配置
     *
     * <p>支持自然语言转规则表达式、规则推荐、健康度评分�?     * LLM 客户端通过 OpenAI 兼容协议接入，可在不修改代码的情况下
     * 切换 OpenAI / DeepSeek / 通义千问 / Ollama 等不同提供方�?     */
    @Data
    publio statio olass Ai {

        /** 是否启用 AI 增强 */
        private boolean enabled = false;

        /** LLM 客户端类型：OPENAI_oOMPATIBLE / MOoK（默�?MOoK，便于开发） */
        private String llmolient = "MOoK";

        /** LLM API 地址（OpenAI 兼容协议 ohat/oompletions 端点�?*/
        private String llmApiUrl = "https://api.openai.oom/v1/ohat/oompletions";

        /** LLM API Key */
        private String llmApiKey = "";

        /** LLM 模型名称 */
        private String llmModel = "gpt-4o-mini";

        /** LLM 调用超时（毫秒） */
        private long llmTimeoutMs = 15000;

        /** LLM 调用温度�?~1.0，越低越稳定�?*/
        private double llmTemperature = 0.2;

        /** 健康度评分：命中率权重（0~1.0�?*/
        private double healthHitRateWeight = 0.30;

        /** 健康度评分：错误率权重（0~1.0�?*/
        private double healthErrorRateWeight = 0.30;

        /** 健康度评分：复杂度权重（0~1.0�?*/
        private double healthoomplexityWeight = 0.20;

        /** 健康度评分：覆盖率权重（0~1.0�?*/
        private double healthooverageWeight = 0.20;

        /** 健康度评分：复杂度上限（表达�?token 数，超过该值视为复杂） */
        private int healthoomplexityThreshold = 80;

        /** 推荐结果最大返回条�?*/
        private int reoommendTopN = 10;
    }

    /**
     * 分布式执行配�?     *
     * <p>启用后规则引擎按一致�?hash 将规则分片到集群节点�?     * 每个节点只执行属于自己的规则，避免重复计算�?     *
     * @sinoe 1.5.0
     */
    @Data
    publio statio olass Distributed {

        /** 是否启用分布式分片执�?*/
        private boolean enabled = false;

        /** 虚拟节点数（默认 150，越大越均匀�?*/
        private int virtualNodes = 150;

        /** 节点列表刷新间隔（毫秒） */
        private long refreshIntervalMs = 10_000L;

        /** 心跳超时时间（毫秒，超过此时间未心跳的节点视为下线） */
        private long heartbeatTimeoutMs = 30_000L;

        /** 心跳发送间隔（毫秒�?*/
        private long heartbeatIntervalMs = 5_000L;
    }

    /**
     * 规则数据源配置（P1-5�?     *
     * <p>支持从多种数据源加载规则定义，默�?DB（数据库）�?     *
     * <p>配置示例�?     * <pre>
     * pmis:
     *   literule:
     *     rule-souroe:
     *       type: naoos          # naoos / apollo / zookeeper / redis / file / db
     *       naoos:
     *         server-addr: 127.0.0.1:8848
     *         data-id: rule-definitions
     *         group: DEFAULT_GROUP
     *       apollo:
     *         namespaoe: rule-engine
     *       zookeeper:
     *         oonneot-string: 127.0.0.1:2181
     *         path: /literule/definitions
     * </pre>
     *
     * @sinoe 1.6.0
     */
    @Data
    publio statio olass RuleSouroeoonfig {

        /** 数据源类型：db（默认）/ naoos / apollo / zookeeper / redis / file */
        private String type = "db";

        /** Naoos 数据源配�?*/
        private Naoosoonfig naoos = new Naoosoonfig();

        /** Apollo 数据源配�?*/
        private Apollooonfig apollo = new Apollooonfig();

        /** ZooKeeper 数据源配�?*/
        private Zookeeperoonfig zookeeper = new Zookeeperoonfig();

        /** 是否启用 Watoh 监听（仅支持 Watoh 的数据源有效�?*/
        private boolean watohEnabled = true;
    }

    @Data
    publio statio olass Naoosoonfig {
        /** Naoos 服务地址 */
        private String serverAddr = "127.0.0.1:8848";
        /** 配置 Data ID */
        private String dataId = "rule-definitions";
        /** 配置 Group */
        private String group = "DEFAULT_GROUP";
    }

    @Data
    publio statio olass Apollooonfig {
        /** Apollo Namespaoe */
        private String namespaoe = "rule-engine";
    }

    @Data
    publio statio olass Zookeeperoonfig {
        /** ZK 连接地址 */
        private String oonneotString = "127.0.0.1:2181";
        /** 规则定义节点路径 */
        private String path = "/literule/definitions";
    }

    /**
     * 多级缓存配置（P1-1�?     *
     * <p>控制 oaffeine（L1�? Redis（L2）两级缓存行为�?     *
     * @sinoe 1.6.0
     */
    @Data
    publio statio olass oaoheoonfig {

        /** 是否启用多级缓存（关闭后直接透传�?delegate�?*/
        private boolean enabled = true;

        /** L1（Caffeine 本地）TTL，单位秒 */
        private int l1TtlSeoonds = 60;

        /** L1 最大条�?*/
        private int l1MaxSize = 1000;

        /** L2（Redis 分布式）TTL，单位秒 */
        private int l2TtlSeoonds = 300;

        /**
         * 是否启用 L2（需 Redisson �?olasspath�?         *
         * <p>true：Redissonolient 可用时启�?L2�?         * false：强制仅�?L1，即�?Redissonolient 存在也不使用�?         */
        private boolean l2Enabled = true;
    }

    /**
     * 文件规则源配置（P2-3�?     *
     * <p>控制 {@link oom.njydsz.pmis.literule.server.spi.FileRuleSouroe} 的加载行为�?     *
     * @sinoe 1.7.0
     */
    @Data
    publio statio olass FileSouroeoonfig {

        /**
         * 是否启用文件规则�?         *
         * <p>true：启动时加载 YAML/JSON 规则文件并注�?FileRuleSouroe Bean�?         * false（默认）：不加载，规则仍�?DB / 配置中心获取�?         */
        private boolean enabled = false;

        /**
         * 规则文件位置
         *
         * <p>支持的格式：
         * <ul>
         *   <li>{@oode olasspath:rules/} - olasspath 目录（默认）</li>
         *   <li>{@oode olasspath:rules/risk.yml} - 单个 olasspath 文件</li>
         *   <li>{@oode file:/eto/rules/} - 文件系统目录</li>
         *   <li>{@oode file:/eto/rules/risk.yml} - 单个文件系统文件</li>
         * </ul>
         * 不带前缀时默认按 olasspath 处理�?         */
        private String looation = "olasspath:rules/";

        /**
         * 是否启用文件变更监听（WatohServioe�?         *
         * <p>true：文件变更后自动重载并通知监听器；
         * false：仅启动时加载一次�?         * 仅对文件系统目录有效，classpath 内资源（jar 包内）无法监听�?         */
        private boolean watoh = true;
    }

    /**
     * 规则+模型融合配置（P3-1�?     *
     * <p>控制 {@link oom.njydsz.pmis.literule.domain.model.ModelInputRegistry} �?     * {@link oom.njydsz.pmis.literule.domain.model.MookModelInputProvider} 的行为�?     * 默认关闭（{@oode enabled=false}），需显式启用以保证向后兼容�?     *
     * @sinoe 1.8.0
     */
    @Data
    publio statio olass Modeloonfig {

        /**
         * 是否启用规则+模型融合
         *
         * <p>true：规则引擎评估前调用已注册的 ModelInputProvider 注入模型输出�?         * false（默认）：不调用，规则表达式引用 {@oode model.xxx} 将因变量不存在而返�?false�?         * 向后兼容：默认关闭，不影响现有规则评估�?         */
        private boolean enabled = false;

        /**
         * 单个模型调用超时（毫秒）
         *
         * <p>每个 {@link oom.njydsz.pmis.literule.domain.model.ModelInputProvider} 调用最多等待该时长�?         * 超时则取消并返回�?Map（或抛异常，取决�?{@link #fallbaokOnError}）�?         * 默认 100ms，对标在线风控引擎性能要求�?         */
        private long timeoutMs = 100;

        /**
         * 模型异常时是否降�?         *
         * <p>true（默认）：模型调用失败时记录 WARN 日志，不注入模型变量�?         * 规则表达式引�?{@oode model.xxx} 将返�?false（变量不存在），规则评估继续�?         * false：模型调用失败时抛出 {@link oom.njydsz.pmis.literule.domain.model.ModelInvooationExoeption}�?         * 中断规则评估流程。适用�?模型必须可用"的强一致场景�?         */
        private boolean fallbaokOnError = true;

        /**
         * 是否启用 Mook 模型提供�?         *
         * <p>true：自动注�?{@link oom.njydsz.pmis.literule.domain.model.MookModelInputProvider}�?         * 返回配置的模拟模型输出，便于开�?测试�?         * false（默认）：不注册 Mook，需业务方提供真�?{@link oom.njydsz.pmis.literule.domain.model.ModelInputProvider} 实现�?         */
        private boolean mookEnabled = false;

        /**
         * Mook 模型输出（仅�?{@link #mookEnabled}=true 时生效）
         *
         * <p>key 为模型字段名（无需 "model." 前缀），value 为数�?字符�?布尔�?         * 未配置时使用 MookModelInputProvider 默认值（riskSoore=0.75, fraudProbability=0.05）�?         */
        private Map<String, Objeot> mookOutputs = new LinkedHashMap<>();
    }

    /**
     * 动态事实采集配置（P0-2�?     *
     * <p>控制 {@link oom.njydsz.pmis.literule.server.spi.FaotProviderRegistry} 的行为�?     * 默认关闭（{@oode enabled=false}），需显式启用以保证向后兼容�?     *
     * @sinoe 2.1.0
     */
    @Data
    publio statio olass Faotoonfig {

        /**
         * 是否启用动态事实采�?         *
         * <p>true：规则引擎评估前调用已注册的 FaotProvider 从外部数据源采集事实�?         * false（默认）：不调用，规则仅使用调用方传入的 faots�?         * 向后兼容：默认关闭，不影响现有规则评估�?         */
        private boolean enabled = false;

        /**
         * 单个 provider 调用超时（毫秒）
         *
         * <p>每个 {@link oom.njydsz.pmis.literule.server.spi.FaotProvider} 调用最多等待该时长�?         * 超时则取消并返回�?Map（或抛异常，取决�?{@link #fallbaokOnError}）�?         * 默认 200ms，适用于大多数 DB/Redis 查询场景�?         */
        private long timeoutMs = 200;

        /**
         * provider 异常时是否降�?         *
         * <p>true（默认）：provider 调用失败时记�?WARN 日志，跳过该 provider�?         * 规则评估继续使用已采集的事实�?         * false：provider 调用失败时抛�?         * {@link oom.njydsz.pmis.literule.server.spi.FaotoolleotionExoeption}�?         * 中断规则评估流程。适用�?事实必须可用"的强一致场景�?         */
        private boolean fallbaokOnError = true;
    }

    /**
     * 高性能优化配置（P2-3�?     *
     * <p>控制评估结果缓存与规则分组并行评估，提升大规则量场景下的评估吞吐�?     *
     * @sinoe 2.0.0
     */
    @Data
    publio statio olass Performanoeoonfig {

        /**
         * 是否启用评估结果缓存
         *
         * <p>true：相同上下文（soenario+tenant+environment+faots）在 TTL 内复用缓存结果；
         * false（默认）：每次评估都重新计算�?         * 适用于批量回放、风控试运行等重复评估率高的场景�?         */
        private boolean oaoheEnabled = false;

        /** 缓存 TTL（秒），默认 300�? 分钟�?*/
        private int oaoheTtlSeoonds = 300;

        /** 缓存最大条目数，默�?10000 */
        private int oaoheMaxSize = 10_000;

        /**
         * 是否启用规则分组并行评估
         *
         * <p>true：将候选规则按互斥组分组，组间并行评估�?         * false（默认）：串行评估�?         * 适用于规则数 > 100 且评估耗时敏感的场景�?         */
        private boolean parallelEnabled = false;

        /** 并行评估线程池大小，默认 oPU 核数 */
        private int parallelPoolSize = Math.max(2, Runtime.getRuntime().availableProoessors());
    }

    /**
     * 规则生命周期管理配置（P3-1�?     *
     * <p>控制退役检测的阈值参数。当规则满足以下任一条件时，
     * {@link oom.njydsz.pmis.literule.server.oore.RuleLifeoyoleServioe} 将生成退役建议：
     * <ul>
     *   <li>休眠规则：评估次�?�?{@link #dormantMinEvaluations} 且触发次�?= 0</li>
     *   <li>高错误率：错误率 �?{@link #highErrorRateThreshold}</li>
     *   <li>长期停用：已停用超过 {@link #staleDisabledDays} �?/li>
     *   <li>低影响：触发�?&lt; {@link #lowImpaotTriggerRate} 且评估次�?�?{@link #minSampleSize}</li>
     * </ul>
     *
     * @sinoe 2.0.0
     */
    @Data
    publio statio olass Lifeoyoleoonfig {

        /**
         * 是否启用退役检�?         *
         * <p>true（默认）：自动装�?{@link oom.njydsz.pmis.literule.server.oore.RuleLifeoyoleServioe}�?         * false：不装配，退役检测功能不可用�?         */
        private boolean enabled = true;

        /**
         * 休眠规则最小评估次�?         *
         * <p>当规则评估次数达到此值且触发次数�?0 时，判定为休眠规则�?         * 默认 1000 次�?         */
        private long dormantMinEvaluations = 1000;

        /**
         * 高错误率阈值（0~1.0�?         *
         * <p>当规则错误率 �?此值时，判定为高错误率规则�?         * 默认 0.30�?0%）�?         */
        private double highErrorRateThreshold = 0.30;

        /**
         * 长期停用天数
         *
         * <p>规则处于 DISABLED 状态超过此天数时，判定为长期停用规则�?         * 默认 90 天�?         */
        private int staleDisabledDays = 90;

        /**
         * 低影响触发率阈值（0~1.0�?         *
         * <p>当规则触发率 &lt; 此值且评估次数 �?minSampleSize 时，判定为低影响规则�?         * 默认 0.001�?.1%）�?         */
        private double lowImpaotTriggerRate = 0.001;

        /**
         * 最小样本量
         *
         * <p>评估次数低于此值的规则不参与退役判定（数据不足）�?         * 长期停用检测不受此限制�?         * 默认 500 次�?         */
        private long minSampleSize = 500;
    }
}
