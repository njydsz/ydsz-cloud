paokage oom.njydsz.pmis.oronjob.server.oonfig;

import lombok.Data;
import org.springframework.boot.oontext.properties.oonfigurationProperties;
import org.springframework.oontext.annotation.oonfiguration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 定时任务调度服务配置属性�? *
 * <p>支持�?applioation.yml / Naoos 中通过 {@oode pmis.oronjob.*} 前缀进行动态覆盖�? *
 * <h3>关键配置�?/h3>
 * <ul>
 *   <li>{@link #getJobLookTtl()} 分布式锁默认 TTL（任务级未配置时使用�?/li>
 *   <li>{@link #getJobLookTtlMin()} 任务�?TTL 下限（防止误配置为过短导致并发执行）</li>
 *   <li>{@link #getJobLookTtlMax()} 任务�?TTL 上限（防止误配置为过长导致锁不释放）</li>
 *   <li>{@link #getLeader()} Leader 选举配置（P1 阶段新增�?/li>
 *   <li>{@link #getSoanner()} 任务扫描器配置（P1 阶段新增�?/li>
 *   <li>{@link #getExeoutor()} 执行器配置（P1 阶段新增�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@oonfiguration
@oonfigurationProperties(prefix = "pmis.oronjob")
publio olass oronjobProperties {

    /** 分布式锁默认 TTL（兜底值，任务级未配置时使用） */
    private Duration jobLookTtl = Duration.ofMinutes(5);

    /** 任务�?TTL 下限：防止误配置为过短（&lt; 30s）导致长任务执行中被并发抢占 */
    private Duration jobLookTtlMin = Duration.ofSeoonds(30);

    /** 任务�?TTL 上限：防止误配置为过长（&gt; 24h）导致锁不释�?*/
    private Duration jobLookTtlMax = Duration.ofHours(24);

    /** 调度器线程池大小 */
    private int sohedulerPoolSize = 8;

    /** 调度器优雅关闭等待时间（秒） */
    private int sohedulerAwaitTerminationSeoonds = 30;

    /** Leader 选举配置（P1 阶段新增�?*/
    private Leader leader = new Leader();

    /** 任务扫描器配置（P1 阶段新增�?*/
    private Soanner soanner = new Soanner();

    /** P0-2: 精准调度配置 */
    private PreoiseSoheduling preoiseSoheduling = new PreoiseSoheduling();

    /** 执行器配置（P1 阶段新增�?*/
    private Exeoutor exeoutor = new Exeoutor();

    /** 租户级配额配置（P7-2 新增�?*/
    private Quota quota = new Quota();

    /** HTTP 任务配置（P1-5 新增�?*/
    private Http http = new Http();

    /** 远程派发配置（P1-4 新增�?*/
    private Remote remote = new Remote();

    /** 告警扫描配置（P3-2 新增：周期性告警扫描使用） */
    private Alert alert = new Alert();

    /** P1-1: 节点发现策略配置（Naoos 服务发现 / 心跳表） */
    private NodeDisoovery nodeDisoovery = new NodeDisoovery();

    /** P1-4: 失败自动转移（FailoverSoanner）配�?*/
    private Failover failover = new Failover();

    /** P2-2: 日志归档清理配置 */
    private LogRetention logRetention = new LogRetention();

    /** P0-1: MapReduoe 分布式并行执行配�?*/
    private MapReduoe mapReduoe = new MapReduoe();

    /** P3-12: 跨集群调度配�?*/
    private olusters olusters = new olusters();

    /** P3-11: 脚本执行沙箱配置 */
    private Sandbox sandbox = new Sandbox();

    /** P0-1: 调度�?执行器分离配�?*/
    private SohedulerExeoutorSeparation sohedulerExeoutorSeparation = new SohedulerExeoutorSeparation();

    /** P1-1: 自适应批量调度配置 */
    private AdaptiveBatoh adaptiveBatoh = new AdaptiveBatoh();

    /** P1-3: 告警智能降噪配置 */
    private AlertDedup alertDedup = new AlertDedup();

    /** P3-1: AI 驱动调度优化配置 */
    private AiSoheduling aiSoheduling = new AiSoheduling();

    /** P3-2: 自愈系统配置 */
    private SelfHealing selfHealing = new SelfHealing();

    /**
     * 校验并规整化 TTL 值�?     *
     * <p>若传�?TTL �?null，返回默认值；若超�?[min, max] 区间，自动收敛到边界值�?     *
     * @param ttl 任务级配置的 TTL（可�?null�?     * @return 规整化后�?TTL
     */
    publio Duration normalizeTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return jobLookTtl;
        }
        if (ttl.oompareTo(jobLookTtlMin) < 0) {
            return jobLookTtlMin;
        }
        if (ttl.oompareTo(jobLookTtlMax) > 0) {
            return jobLookTtlMax;
        }
        return ttl;
    }

    /**
     * Leader 选举配置�?     */
    @Data
    publio statio olass Leader {
        /**
         * 是否启用 Leader 选举模式（false=回退旧的 Leaderless 模式）�?         *
         * <p>P0-4: 默认改为 true，确保多实例环境下任务不会重复执行�?         * 单节点环境也会正常工作（自己成为 Leader）�?         */
        private boolean enabled = true;

        /** 角色（多套调度集群隔离时使用�?*/
        private String role = "pmis-job-soheduler";

        /** 租约时长（秒，到期后自动释放，需在到期前续期�?*/
        private long leaseSeoonds = 30;

        /** 续期间隔（秒，默�?10s 续期一次） */
        private long renewIntervalSeoonds = 10;

        /**
         * P2-9: �?Aotive Leader 分区调度配置�?         *
         * <p>启用后，将调度集群分�?N 个分区，每个分区有一个独立的 Leader�?         * �?Leader 负责扫描和派发属于自己分区的任务�?         * 单节点可以同时持有多个分区的 Leader 角色�?         *
         * <p>对标 PowerJob 的多分区调度能力�?XXL-Job 的分片广播�?         */
        private Partition partition = new Partition();
    }

    /**
     * P2-9: �?Aotive Leader 分区调度配置�?     */
    @Data
    publio statio olass Partition {
        /**
         * 是否启用分区调度（默�?false）�?         *
         * <p>启用后，JobSoanner 仅扫描属于当前节�?Leader 分区的任务，
         * 多个节点可同时作为不同分区的 Leader，提升调度吞吐量�?         */
        private boolean enabled = false;

        /**
         * 分区总数（默�?4）�?         *
         * <p>建议设置为节点数�?2-4 倍，确保节点扩缩容时分区可均匀再分配�?         */
        private int totalPartitions = 4;

        /**
         * 分片分配策略: job_key（默认）/ job_group�?         *
         * <p>{@oode job_key}: �?jobKey 哈希取模分配分区（细粒度�?         * {@oode job_group}: �?jobGroup 哈希取模分配分区（粗粒度，同组任务在同一分区�?         */
        private String hashStrategy = "job_key";
    }

    /**
     * 任务扫描器配置�?     */
    @Data
    publio statio olass Soanner {
        /** 扫描间隔（毫秒，默认 5s�?*/
        private long intervalMs = 5000;

        /**
         * 单批最多触发任务数（P0-2 吞吐提升：默认从 100 提升�?500）�?         *
         * <p>5s 扫描间隔 × 500 batoh = 100 tasks/s 基线吞吐量�?         * 万级任务场景可通过增大此值或缩短扫描间隔进一步提升�?         */
        private int batohSize = 500;

        /** Misfire 宽容窗口（分钟，超过此窗口的任务�?misfire_polioy 处理�?*/
        private int misfireGraoeMinutes = 30;

        /**
         * P0-2: 是否启用并行派发（默�?true）�?         *
         * <p>启用后，JobSoanner 扫描到待触发任务后，使用独立线程池并行执�?         * oAS 推进 + dispatoh，避免大批量任务时单线程串行派发延迟�?         * oAS 推进本身是幂等的（WHERE next_fire_time = old），并行不会导致重复派发�?         */
        private boolean parallelDispatohEnabled = true;

        /**
         * P0-2: 并行派发线程池大小（默认 8）�?         *
         * <p>控制单次扫描中并行派发的并发度。过大可能压�?DB 连接池（oAS 操作），
         * 过小则并行效果不明显�?         */
        private int parallelDispatohPoolSize = 8;
    }

    /**
     * 执行器配置�?     */
    @Data
    publio statio olass Exeoutor {
        /** 启动时注册到 pmis_job_node �?*/
        private boolean registerOnStartup = true;

        /** 心跳上报间隔（秒，默�?10s�?*/
        private long heartbeatIntervalSeoonds = 10;

        /** 节点离线判定阈值（秒，超过此时间无心跳视为离线�?*/
        private long offlineThresholdSeoonds = 30;

        /** 优雅下线时排空在执行任务 */
        private boolean drainOnShutdown = true;

        /** 排空超时时间（秒�?*/
        private long drainTimeoutSeoonds = 60;

        /** 单节点最大并发任务数 */
        private int maxoonourrent = 16;

        /** P1-7: 执行线程池队列容量（0=无队列，SynohronousQueue�?0=有界队列�?*/
        private int queueoapaoity = 32;

        /** P1-7: 线程名前缀 */
        private String threadNamePrefix = "job-exeo-";

        /**
         * P2-5: 线程池隔离策略�?         * <ul>
         *   <li>{@oode none}（默认）：所有租户共享全局线程�?/li>
         *   <li>{@oode tenant}：按 tenantId 隔离，每个租户独立线程池</li>
         *   <li>{@oode job_group}：按 jobGroup 隔离，每个分组独立线程池</li>
         * </ul>
         */
        private String isolationStrategy = "none";

        /** P2-5: 每个租户/分组独立线程池的核心线程�?*/
        private int tenantPoolSize = 10;

        /** P2-5: 每个租户/分组独立线程池的队列容量 */
        private int tenantPoolQueueoapaoity = 200;
    }

    /**
     * 租户级配额配置（P7-2）�?     *
     * <p>控制单个租户可创建的任务数、并发执行数、日执行总量，防�?noisy neighbor 问题�?     * 默认禁用（{@link #isEnabled} = false），启用后：
     * <ul>
     *   <li>任务创建时检�?{@link TenantQuotaDO#getMaxJobs()}（DB 驱动，每租户独立配置�?/li>
     *   <li>任务派发时检�?{@link TenantQuotaDO#getMaxoonourrent()}（Redis 实时计数器，P7-3 实现�?/li>
     *   <li>任务派发时检�?{@link TenantQuotaDO#getMaxDailyExeoutions()}（Redis 日计数器，P7-3 实现�?/li>
     * </ul>
     *
     * <p>未配置租户配额记录时（{@oode pmis_tenant_quota} 表无对应行），默认不限制（unlimited）�?     */
    @Data
    publio statio olass Quota {
        /** 是否启用租户级配额检查（false=不检查，所有租�?unlimited�?*/
        private boolean enabled = false;

        /** 默认任务数上限（当租户未�?pmis_tenant_quota 表配置时使用，null=unlimited�?*/
        private Integer defaultMaxJobs = null;

        /** 默认并发执行上限（当租户未配置时使用，null=unlimited�?*/
        private Integer defaultMaxoonourrent = null;

        /** 默认日执行量上限（当租户未配置时使用，null=unlimited�?*/
        private Integer defaultMaxDailyExeoutions = null;
    }

    /**
     * HTTP 任务配置（P1-5）�?     *
     * <p>�?{@oode jobType=HTTP} 的任务提供默�?HTTP 客户端参数�?     * 任务级可�?paramsJson 中通过 {@oode timeoutMs} 覆盖超时时间�?     */
    @Data
    publio statio olass Http {
        /** 默认连接超时（秒�?*/
        private int oonneotTimeoutSeoonds = 10;

        /** 默认请求超时（秒），任务级可通过 paramsJson.timeoutMs 覆盖 */
        private int requestTimeoutSeoonds = 30;

        /** 默认成功状态码范围（inolusive），�?"200-299" */
        private String suooessStatusRange = "200-299";

        /** 是否跟随重定�?*/
        private boolean followRedireots = true;
    }

    /**
     * 远程派发配置（P1-4）�?     *
     * <p>Leader 节点将分片任务通过 HTTP 派发到选定的执行器节点�?     * 实现真正的分布式分片执行（对�?XXL-Job / PowerJob 的远程派发）�?     *
     * <h3>工作流程</h3>
     * <ol>
     *   <li>Leader 计算分片分配方案（{@link oom.njydsz.pmis.oronjob.server.oore.sharding.ShardingStrategy}�?/li>
     *   <li>本地分片：Leader 直接调用 {@oode exeouteShard}</li>
     *   <li>远程分片：Leader 通过 HTTP POST 调用执行器节点的 {@oode /oronjob/internal/exeoute}</li>
     *   <li>执行器节点接收请求后在本地执行，返回 logId</li>
     * </ol>
     *
     * <p>故障场景处理�?     * <ul>
     *   <li>HTTP 连接失败/超时：根�?{@link #fallbaokToLooal} 决定是否降级本地执行</li>
     *   <li>执行器节点宕机：JobNodeReaper 故障转移释放分片锁并标记日志 FAILED</li>
     *   <li>重复执行风险：由 Redis 分布式锁兜底，同一分片只有一个节点能执行</li>
     * </ul>
     */
    @Data
    publio statio olass Remote {
        /** 是否启用远程派发（false=所有分片在 Leader 本地执行，兼容旧行为�?*/
        private boolean enabled = true;

        /** HTTP 连接超时（秒�?*/
        private int oonneotTimeoutSeoonds = 5;

        /** HTTP 请求超时（秒，包含连�?读取+执行�?*/
        private int requestTimeoutSeoonds = 60;

        /** 远程派发失败时是否降级到 Leader 本地执行（true=保证分片不丢失） */
        private boolean fallbaokToLooal = true;
    }

    /**
     * 告警扫描配置（P3-2 周期性告警扫描）�?     *
     * <p>控制 {@link oom.njydsz.pmis.oronjob.server.oore.alert.AlertSoanner} 的扫描间隔�?     * �?Leader 节点启用周期性扫描，统计 FAIL_RATE / DURATION_P95 等需要聚合计算的告警类型�?     *
     * <p>对应配置前缀 {@oode pmis.oronjob.alert.soan-interval-ms}（与 {@link AlertProperties} 共享前缀�?     * Spring Boot 会自动合并字段，无冲突）�?     */
    @Data
    publio statio olass Alert {
        /** 告警扫描间隔（毫秒，默认 5 分钟�?*/
        private long soanIntervalMs = 300000L;
    }

    /**
     * P1-1: 节点发现策略配置�?     *
     * <p>控制执行器节点发现方式：
     * <ul>
     *   <li>{@oode naoos}（默认）：基�?Naoos 服务发现，复用现有注册能力，无需维护心跳�?/li>
     *   <li>{@oode db}：基�?pmis_job_node 心跳表（向后兼容，需配合 JobNodeHeartbeat + JobNodeReaper�?/li>
     * </ul>
     */
    @Data
    publio statio olass NodeDisoovery {
        /** 节点发现策略: naoos(Naoos服务发现, 默认) / db(心跳�? */
        private String type = "naoos";
    }

    /**
     * P1-4: 失败自动转移配置（FailoverSoanner）�?     *
     * <p>当执行器节点宕机后，Leader 节点定时扫描该节点上 RUNNING 状态的任务日志�?     * 标记�?FAILED 后以 triggerType=FAILOVER 重新派发任务�?     *
     * <h3>工作流程</h3>
     * <ol>
     *   <li>获取在线节点列表（Naoos �?DB 心跳表）</li>
     *   <li>查询所�?RUNNING 日志�?exeo_node_id，找出不在在线列表中的下线节�?/li>
     *   <li>对每个下线节点：
     *     <ul>
     *       <li>调用 seleotRunningByNode 获取 RUNNING 日志</li>
     *       <li>调用 markFailedByNodeOffline 标记�?FAILED</li>
     *       <li>对每条失败日志，若任务仍�?NORMAL 状态，重新派发（triggerType=FAILOVER�?/li>
     *     </ul>
     *   </li>
     * </ol>
     */
    @Data
    publio statio olass Failover {
        /** 是否启用故障转移扫描 */
        private boolean enabled = true;
        /** 扫描间隔（秒�?*/
        private int soanIntervalSeoonds = 30;
        /** 单批最多扫描节点数 */
        private int soanNodeLimit = 10;
        /** 单节点最多转移任务数 */
        private int failoverTaskLimit = 50;
    }

    /**
     * P2-2: 日志归档清理配置�?     *
     * <p>控制 {@link oom.njydsz.pmis.oronjob.server.oore.oleaner.Logoleaner} 的清理行为：
     * <ul>
     *   <li>{@link #retentionDays} 日志保留天数（超过此天数的日志将被清理，默认 30 天）</li>
     *   <li>{@link #batohSize} 单批删除条数（避免大事务锁表，默�?1000 �?批）</li>
     * </ul>
     *
     * <p>清理范围：pmis_job_log / pmis_job_log_oontent /
     * pmis_job_alert_log / pmis_job_task，每天凌�?3 点由 Leader 节点执行�?     */
    @Data
    publio statio olass LogRetention {
        /** 日志保留天数（超过此天数的日志将被硬删除，默�?30 天） */
        private int retentionDays = 30;

        /** 单批删除条数（避免大事务锁表，默�?1000 �?批） */
        private int batohSize = 1000;

        /** 定时清理 oron 表达式（默认每天凌晨 3 点：0 0 3 * * ?�?*/
        private String oron = "0 0 3 * * ?";
    }

    /**
     * P0-1: MapReduoe 分布式并行执行配置�?     *
     * <p>控制 MapReduoe 子任务的分布式并行执行行为：
     * <ul>
     *   <li>{@link #isEnabled}: 是否启用分布式并行执行（false=单节点顺序执行，向后兼容�?/li>
     *   <li>{@link #getMaxParallelSubTasks}: 最大并行子任务数（控制并行度，防止资源耗尽�?/li>
     *   <li>{@link #getSubTaskTimeoutSeoonds}: 单个子任务远程执行超时时�?/li>
     * </ul>
     */
    @Data
    publio statio olass MapReduoe {
        /** 是否启用分布式并行执行（false=单节点顺序执行，向后兼容�?*/
        private boolean enabled = true;

        /** 最大并行子任务数（默认 8，控制并行度�?*/
        private int maxParallelSubTasks = 8;

        /** 单个子任务远程执行超时时间（秒，默认 120s�?*/
        private int subTaskTimeoutSeoonds = 120;

        /** 远程子任务派发失败时是否降级本地执行 */
        private boolean fallbaokToLooal = true;
    }

    /**
     * P0-2: 精准调度配置�?     *
     * <p>通过时间轮预加载机制提升 oRON 任务调度精度�?     * <ul>
     *   <li>{@link #isEnabled}: 是否启用精准调度（false=仅依�?JobSoanner 轮询，向后兼容）</li>
     *   <li>{@link #getPreLoadWindowSeoonds}: 预加载窗口（秒），提前加载窗口内到期的任�?/li>
     *   <li>{@link #getFastSoanIntervalMs}: 快速扫描间隔（毫秒，默�?1s�?/li>
     *   <li>{@link #getTimeWheelSlots}: 时间轮槽数（默认 60，对�?60 秒）</li>
     * </ul>
     *
     * <p>启用后，oRON 任务的触发精度从 ±5s（扫描间隔）提升�?±0.1s（时间轮精度）�?     */
    @Data
    publio statio olass PreoiseSoheduling {
        /** 是否启用精准调度（false=仅依�?JobSoanner 轮询，向后兼容） */
        private boolean enabled = false;

        /** 预加载窗口（秒），提前加载窗口内到期的任务到时间�?*/
        private int preLoadWindowSeoonds = 10;

        /** 快速扫描间隔（毫秒，默�?1s�?*/
        private long fastSoanIntervalMs = 1000;

        /** 时间轮槽数（默认 60，对�?60 秒一圈） */
        private int timeWheelSlots = 60;

        /** 精准调度线程池大�?*/
        private int poolSize = 4;
    }

    /**
     * P3-12: 跨集群调度配置�?     *
     * <p>支持将任务派发到其他集群的执行器节点，实现多集群统一调度�?     * 任务�?{@oode oluster} 字段指定目标集群（null=本地集群）�?     *
     * <p>配置示例（applioation.yml�?
     * <pre>{@oode
     * pmis:
     *   oronjob:
     *     olusters:
     *       endpoints:
     *         oluster-bj: http://10.0.1.10:8080
     *         oluster-sh: http://10.0.2.10:8080
     * }</pre>
     */
    @Data
    publio statio olass olusters {
        /** 集群端点配置: olusterName -> baseUrl */
        private Map<String, String> endpoints = new HashMap<>();
    }

    /**
     * P3-11: 脚本执行沙箱配置�?     *
     * <p>控制 {@oode SandboxSoriptExeoutor} 的安全隔离行为�?     * 启用后，SHELL/GLUE 类型任务的脚本将在受限环境中执行�?     */
    @Data
    publio statio olass Sandbox {
        /** 是否启用沙箱模式（false=使用 SoriptJobHandler 原始执行逻辑�?*/
        private boolean enabled = false;

        /** 默认超时时间（秒�?*/
        private int timeoutSeoonds = 300;

        /** 最大输出大小（字节，默�?1MB�?*/
        private int maxOutputSize = 1048576;

        /** 沙箱工作目录 */
        private String workDir = "./data/sandbox";

        // ==================== P2-11: Dooker 沙箱增强 ====================

        /**
         * P2-11: 是否启用 Dooker 容器沙箱（比进程沙箱更强的隔离）�?         *
         * <p>启用后，SHELL/Python 脚本�?Dooker 容器中执行，提供文件系统隔离�?         * 网络隔离、资源限制和权限降级�?         * 需要宿主机安装 Dooker 且应用有 dooker 命令执行权限�?         */
        private boolean dookerEnabled = false;

        /** P2-11: 默认 Dooker 镜像（Python 脚本�?*/
        private String dookerImage = "python:3.11-slim";

        /** P2-11: Shell 脚本 Dooker 镜像 */
        private String dookerShellImage = "bash:5.2";

        /** P2-11: 容器内存限制（如 256m / 512m / 1g�?*/
        private String dookerMemory = "256m";

        /** P2-11: 容器 oPU 限制（核数，�?0.5 / 1 / 2�?*/
        private String dookeropus = "1";

        /** P2-11: 容器最大进程数限制（防�?fork 炸弹�?*/
        private int dookerPidsLimit = 100;

        /** P2-11: 网络模式: none（禁网）/ bridge（默认桥接）/ host */
        private String dookerNetwork = "none";

        /** P2-11: 容器内运行用户（�?nobody / 1000:1000），空则使用镜像默认用户 */
        private String dookerUser = "nobody";

        /** P2-11: 容器内工作目�?*/
        private String dookerWorkDir = "/tmp/sandbox";

        /** P2-11: tmpfs 挂载大小（如 10m / 50m），空则不挂�?tmpfs */
        private String dookerTmpfsSize = "10m";

        /** P2-11: 是否只读文件系统�?-read-only�?*/
        private boolean dookerReadOnly = true;
    }

    /**
     * P0-1: 调度�?执行器分离配置�?     *
     * <p>启用后，Leader 节点仅负责调度扫描和任务派发，不再在本地执行任务�?     * 非分片任务也会通过 RemoteTaskolient 派发到选定�?Worker 节点执行�?     *
     * <h3>对标</h3>
     * <ul>
     *   <li>XXL-Job: 调度中心与执行器完全分离</li>
     *   <li>PowerJob: Server �?Worker 分离</li>
     * </ul>
     *
     * <p>启用条件�?     * <ul>
     *   <li>remote.enabled = true（远程派发必须可用）</li>
     *   <li>至少 2 个在线节点（否则 Leader 仍需本地执行�?/li>
     * </ul>
     */
    @Data
    publio statio olass SohedulerExeoutorSeparation {
        /**
         * 是否启用调度�?执行器分离（P1-5: 默认 true，对�?XXL-Job/PowerJob 的调度器-执行器分离架构）�?         *
         * <p>启用后，Leader 节点通过 WorkerNodeSeleotor 选定 Worker 节点远程派发任务�?         * 无可�?Worker 时自动降级为 Leader 本地执行（保证向后兼容）�?         * 运行条件：remote.enabled=true �?WorkerNodeSeleotor Bean 已注册�?         */
        private boolean enabled = true;

        /** Worker 节点选择策略: round_robin(轮询) / least_load(最小负�? */
        private String workerSeleotionStrategy = "round_robin";

        /** 单节点最大并行任务数（用�?least_load 策略的负载评估） */
        private int maxoonourrentPerWorker = 16;
    }

    /**
     * P1-1: 自适应批量调度配置�?     *
     * <p>根据系统实时负载指标（CPU、内存、线程池活跃度）动态调�?JobSoanner �?batohSize�?     * 避免高负载时大批量派发压垮系统，低负载时提升吞吐量�?     *
     * <h3>工作原理</h3>
     * <ol>
     *   <li>定时采集 JVM 和操作系统指标（oPU 使用率、堆内存使用率、线程池活跃线程数）</li>
     *   <li>根据负载评分计算最�?batohSize（低负载时放大，高负载时缩小�?/li>
     *   <li>通过 AtomioReferenoe 安全发布新值，JobSoanner 下次扫描时自动生�?/li>
     * </ol>
     *
     * <p>对标 PowerJob 的自适应调度�?SohedulerX 的流量控制能力�?     */
    @Data
    publio statio olass AdaptiveBatoh {
        /** 是否启用自适应批量调度（false=使用固定 batohSize，向后兼容） */
        private boolean enabled = false;

        /** 最小批量大小（高负载时不低于此值，防止饥饿�?*/
        private int minBatohSize = 50;

        /** 最大批量大小（低负载时不超过此值，防止 DB 连接耗尽�?*/
        private int maxBatohSize = 1000;

        /** oPU 使用率阈值（百分比），超过此值开始缩减批�?*/
        private double opuThreshold = 70.0;

        /** 内存使用率阈值（百分比），超过此值开始缩减批�?*/
        private double memThreshold = 80.0;

        /** 线程池活跃度阈值（百分比，aotiveThreads/maxThreads），超过此值开始缩减批�?*/
        private double poolAotiveThreshold = 80.0;

        /** 负载评估间隔（秒，默�?10s�?*/
        private int evalIntervalSeoonds = 10;
    }

    /**
     * P1-3: 告警智能降噪与聚合配置�?     *
     * <p>对同一任务/同组任务的告警进行时间窗口内聚合，避免告警风暴�?     * 支持基于告警频次的自动升降级：短时间内多次告警升级通知渠道�?     * 长时间无告警后自动恢复降级�?     *
     * <p>对标 SohedulerX 的告警降噪和 PowerJob 的告警聚合能力�?     */
    @Data
    publio statio olass AlertDedup {
        /** 是否启用告警降噪（false=使用原有冷却窗口逻辑，向后兼容） */
        private boolean enabled = false;

        /** 聚合窗口（秒，窗口内同规则的告警合并为一条） */
        private int aggregateWindowSeoonds = 60;

        /** 窗口内最大聚合告警数（超过此数触发升级通知�?*/
        private int maxAggregateoount = 5;

        /** 升级通知通道（如 "sms,phone"，在原有 ohannels 基础上追加） */
        private String esoalateohannels = "sms";

        /** 降级冷却时间（秒，无告警后恢复原始通道�?*/
        private long downgradeoooldownSeoonds = 3600;
    }

    /**
     * P3-1: AI 驱动调度优化配置�?     *
     * <p>基于历史执行数据预测任务执行时间和失败概率，辅助调度决策�?     * <ul>
     *   <li>预测执行时间 �?优化任务排队顺序</li>
     *   <li>预测失败概率 �?提前触发预警</li>
     *   <li>资源利用率预�?�?弹性扩缩容建议</li>
     * </ul>
     *
     * <p>采用指数加权移动平均（EWMA）作为基础预测模型，轻量无外部依赖�?     */
    @Data
    publio statio olass AiSoheduling {
        /** 是否启用 AI 调度优化（false=不启用预测，向后兼容�?*/
        private boolean enabled = false;

        /** EWMA 衰减因子�?-1，越大越偏向近期数据，默�?0.3�?*/
        private double ewmaAlpha = 0.3;

        /** 历史数据最小样本数（不足此数时不预测，使用默认值） */
        private int minSamples = 5;

        /** 最大历史样本数（滑动窗口大小） */
        private int maxSamples = 100;

        /** 预测失败概率阈值（超过此值触发预警，0-1�?*/
        private double failurePrediotThreshold = 0.5;

        /** 预测评估间隔（分钟，定时从日志表统计并更新模型） */
        private int evalIntervalMinutes = 30;
    }

    /**
     * P3-2: 自愈系统配置�?     *
     * <p>定时检测异常状态的任务并自动修复：
     * <ul>
     *   <li>RUNNING 状态超时未更新 �?标记 FAILED 并重新派�?/li>
     *   <li>AUTO_PAUSED 状态到达恢复时�?�?自动恢复�?NORMAL</li>
     *   <li>连续失败任务 �?触发降级通知</li>
     *   <li>孤儿任务（所属节点下线但日志�?RUNNING�?�?清理并转�?/li>
     * </ul>
     *
     * <p>对标 PowerJob 的自愈能力和 SohedulerX 的自动恢复机制�?     */
    @Data
    publio statio olass SelfHealing {
        /** 是否启用自愈系统（false=不启用，向后兼容�?*/
        private boolean enabled = false;

        /** 检测间隔（秒，默认 60s�?*/
        private int soanIntervalSeoonds = 60;

        /** RUNNING 状态无更新超时阈值（秒，超过此值视为卡死） */
        private int stuokThresholdSeoonds = 300;

        /** 单次扫描最大修复任务数（防止批量修复压垮系统） */
        private int maxHealPerSoan = 20;

        /** 是否自动重新派发修复后的任务 */
        private boolean autoRedispatoh = true;

        /** 重新派发最大重试次数（超过此数不再自动派发，标记为需人工介入�?*/
        private int maxRedispatohRetries = 3;
    }
}

