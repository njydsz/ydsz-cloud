package com.njydsz.pmis.cronjob.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 定时任务调度服务配置属性。
 *
 * <p>支持在 application.yml / Nacos 中通过 {@code pmis.cronjob.*} 前缀进行动态覆盖。
 *
 * <h3>关键配置项</h3>
 * <ul>
 *   <li>{@link #getJobLockTtl()} 分布式锁默认 TTL（任务级未配置时使用）</li>
 *   <li>{@link #getJobLockTtlMin()} 任务级 TTL 下限（防止误配置为过短导致并发执行）</li>
 *   <li>{@link #getJobLockTtlMax()} 任务级 TTL 上限（防止误配置为过长导致锁不释放）</li>
 *   <li>{@link #getLeader()} Leader 选举配置（P1 阶段新增）</li>
 *   <li>{@link #getScanner()} 任务扫描器配置（P1 阶段新增）</li>
 *   <li>{@link #getExecutor()} 执行器配置（P1 阶段新增）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "pmis.cronjob")
public class CronjobProperties {

    /** 分布式锁默认 TTL（兜底值，任务级未配置时使用） */
    private Duration jobLockTtl = Duration.ofMinutes(5);

    /** 任务级 TTL 下限：防止误配置为过短（&lt; 30s）导致长任务执行中被并发抢占 */
    private Duration jobLockTtlMin = Duration.ofSeconds(30);

    /** 任务级 TTL 上限：防止误配置为过长（&gt; 24h）导致锁不释放 */
    private Duration jobLockTtlMax = Duration.ofHours(24);

    /** 调度器线程池大小 */
    private int schedulerPoolSize = 8;

    /** 调度器优雅关闭等待时间（秒） */
    private int schedulerAwaitTerminationSeconds = 30;

    /** Leader 选举配置（P1 阶段新增） */
    private Leader leader = new Leader();

    /** 任务扫描器配置（P1 阶段新增） */
    private Scanner scanner = new Scanner();

    /** P0-2: 精准调度配置 */
    private PreciseScheduling preciseScheduling = new PreciseScheduling();

    /** 执行器配置（P1 阶段新增） */
    private Executor executor = new Executor();

    /** 租户级配额配置（P7-2 新增） */
    private Quota quota = new Quota();

    /** HTTP 任务配置（P1-5 新增） */
    private Http http = new Http();

    /** 远程派发配置（P1-4 新增） */
    private Remote remote = new Remote();

    /** 告警扫描配置（P3-2 新增：周期性告警扫描使用） */
    private Alert alert = new Alert();

    /** P1-1: 节点发现策略配置（Nacos 服务发现 / 心跳表） */
    private NodeDiscovery nodeDiscovery = new NodeDiscovery();

    /** P1-4: 失败自动转移（FailoverScanner）配置 */
    private Failover failover = new Failover();

    /** P2-2: 日志归档清理配置 */
    private LogRetention logRetention = new LogRetention();

    /** P0-1: MapReduce 分布式并行执行配置 */
    private MapReduce mapReduce = new MapReduce();

    /** P3-12: 跨集群调度配置 */
    private Clusters clusters = new Clusters();

    /** P3-11: 脚本执行沙箱配置 */
    private Sandbox sandbox = new Sandbox();

    /** P0-1: 调度器-执行器分离配置 */
    private SchedulerExecutorSeparation schedulerExecutorSeparation = new SchedulerExecutorSeparation();

    /** P1-1: 自适应批量调度配置 */
    private AdaptiveBatch adaptiveBatch = new AdaptiveBatch();

    /** P1-3: 告警智能降噪配置 */
    private AlertDedup alertDedup = new AlertDedup();

    /** P3-1: AI 驱动调度优化配置 */
    private AiScheduling aiScheduling = new AiScheduling();

    /** P3-2: 自愈系统配置 */
    private SelfHealing selfHealing = new SelfHealing();

    /**
     * 校验并规整化 TTL 值。
     *
     * <p>若传入 TTL 为 null，返回默认值；若超出 [min, max] 区间，自动收敛到边界值。
     *
     * @param ttl 任务级配置的 TTL（可为 null）
     * @return 规整化后的 TTL
     */
    public Duration normalizeTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return jobLockTtl;
        }
        if (ttl.compareTo(jobLockTtlMin) < 0) {
            return jobLockTtlMin;
        }
        if (ttl.compareTo(jobLockTtlMax) > 0) {
            return jobLockTtlMax;
        }
        return ttl;
    }

    /**
     * Leader 选举配置。
     */
    @Data
    public static class Leader {
        /**
         * 是否启用 Leader 选举模式（false=回退旧的 Leaderless 模式）。
         *
         * <p>P0-4: 默认改为 true，确保多实例环境下任务不会重复执行。
         * 单节点环境也会正常工作（自己成为 Leader）。
         */
        private boolean enabled = true;

        /** 角色（多套调度集群隔离时使用） */
        private String role = "pmis-job-scheduler";

        /** 租约时长（秒，到期后自动释放，需在到期前续期） */
        private long leaseSeconds = 30;

        /** 续期间隔（秒，默认 10s 续期一次） */
        private long renewIntervalSeconds = 10;

        /**
         * P2-9: 多 Active Leader 分区调度配置。
         *
         * <p>启用后，将调度集群分为 N 个分区，每个分区有一个独立的 Leader，
         * 各 Leader 负责扫描和派发属于自己分区的任务。
         * 单节点可以同时持有多个分区的 Leader 角色。
         *
         * <p>对标 PowerJob 的多分区调度能力和 XXL-Job 的分片广播。
         */
        private Partition partition = new Partition();
    }

    /**
     * P2-9: 多 Active Leader 分区调度配置。
     */
    @Data
    public static class Partition {
        /**
         * 是否启用分区调度（默认 false）。
         *
         * <p>启用后，JobScanner 仅扫描属于当前节点 Leader 分区的任务，
         * 多个节点可同时作为不同分区的 Leader，提升调度吞吐量。
         */
        private boolean enabled = false;

        /**
         * 分区总数（默认 4）。
         *
         * <p>建议设置为节点数的 2-4 倍，确保节点扩缩容时分区可均匀再分配。
         */
        private int totalPartitions = 4;

        /**
         * 分片分配策略: job_key（默认）/ job_group。
         *
         * <p>{@code job_key}: 按 jobKey 哈希取模分配分区（细粒度）
         * {@code job_group}: 按 jobGroup 哈希取模分配分区（粗粒度，同组任务在同一分区）
         */
        private String hashStrategy = "job_key";
    }

    /**
     * 任务扫描器配置。
     */
    @Data
    public static class Scanner {
        /** 扫描间隔（毫秒，默认 5s） */
        private long intervalMs = 5000;

        /**
         * 单批最多触发任务数（P0-2 吞吐提升：默认从 100 提升至 500）。
         *
         * <p>5s 扫描间隔 × 500 batch = 100 tasks/s 基线吞吐量。
         * 万级任务场景可通过增大此值或缩短扫描间隔进一步提升。
         */
        private int batchSize = 500;

        /** Misfire 宽容窗口（分钟，超过此窗口的任务按 misfire_policy 处理） */
        private int misfireGraceMinutes = 30;

        /**
         * P0-2: 是否启用并行派发（默认 true）。
         *
         * <p>启用后，JobScanner 扫描到待触发任务后，使用独立线程池并行执行
         * CAS 推进 + dispatch，避免大批量任务时单线程串行派发延迟。
         * CAS 推进本身是幂等的（WHERE next_fire_time = old），并行不会导致重复派发。
         */
        private boolean parallelDispatchEnabled = true;

        /**
         * P0-2: 并行派发线程池大小（默认 8）。
         *
         * <p>控制单次扫描中并行派发的并发度。过大可能压垮 DB 连接池（CAS 操作），
         * 过小则并行效果不明显。
         */
        private int parallelDispatchPoolSize = 8;
    }

    /**
     * 执行器配置。
     */
    @Data
    public static class Executor {
        /** 启动时注册到 pmis_job_node 表 */
        private boolean registerOnStartup = true;

        /** 心跳上报间隔（秒，默认 10s） */
        private long heartbeatIntervalSeconds = 10;

        /** 节点离线判定阈值（秒，超过此时间无心跳视为离线） */
        private long offlineThresholdSeconds = 30;

        /** 优雅下线时排空在执行任务 */
        private boolean drainOnShutdown = true;

        /** 排空超时时间（秒） */
        private long drainTimeoutSeconds = 60;

        /** 单节点最大并发任务数 */
        private int maxConcurrent = 16;

        /** P1-7: 执行线程池队列容量（0=无队列，SynchronousQueue；>0=有界队列） */
        private int queueCapacity = 32;

        /** P1-7: 线程名前缀 */
        private String threadNamePrefix = "job-exec-";

        /**
         * P2-5: 线程池隔离策略。
         * <ul>
         *   <li>{@code none}（默认）：所有租户共享全局线程池</li>
         *   <li>{@code tenant}：按 tenantId 隔离，每个租户独立线程池</li>
         *   <li>{@code job_group}：按 jobGroup 隔离，每个分组独立线程池</li>
         * </ul>
         */
        private String isolationStrategy = "none";

        /** P2-5: 每个租户/分组独立线程池的核心线程数 */
        private int tenantPoolSize = 10;

        /** P2-5: 每个租户/分组独立线程池的队列容量 */
        private int tenantPoolQueueCapacity = 200;
    }

    /**
     * 租户级配额配置（P7-2）。
     *
     * <p>控制单个租户可创建的任务数、并发执行数、日执行总量，防止 noisy neighbor 问题。
     * 默认禁用（{@link #isEnabled} = false），启用后：
     * <ul>
     *   <li>任务创建时检查 {@link TenantQuotaDO#getMaxJobs()}（DB 驱动，每租户独立配置）</li>
     *   <li>任务派发时检查 {@link TenantQuotaDO#getMaxConcurrent()}（Redis 实时计数器，P7-3 实现）</li>
     *   <li>任务派发时检查 {@link TenantQuotaDO#getMaxDailyExecutions()}（Redis 日计数器，P7-3 实现）</li>
     * </ul>
     *
     * <p>未配置租户配额记录时（{@code pmis_tenant_quota} 表无对应行），默认不限制（unlimited）。
     */
    @Data
    public static class Quota {
        /** 是否启用租户级配额检查（false=不检查，所有租户 unlimited） */
        private boolean enabled = false;

        /** 默认任务数上限（当租户未在 pmis_tenant_quota 表配置时使用，null=unlimited） */
        private Integer defaultMaxJobs = null;

        /** 默认并发执行上限（当租户未配置时使用，null=unlimited） */
        private Integer defaultMaxConcurrent = null;

        /** 默认日执行量上限（当租户未配置时使用，null=unlimited） */
        private Integer defaultMaxDailyExecutions = null;
    }

    /**
     * HTTP 任务配置（P1-5）。
     *
     * <p>为 {@code jobType=HTTP} 的任务提供默认 HTTP 客户端参数。
     * 任务级可在 paramsJson 中通过 {@code timeoutMs} 覆盖超时时间。
     */
    @Data
    public static class Http {
        /** 默认连接超时（秒） */
        private int connectTimeoutSeconds = 10;

        /** 默认请求超时（秒），任务级可通过 paramsJson.timeoutMs 覆盖 */
        private int requestTimeoutSeconds = 30;

        /** 默认成功状态码范围（inclusive），如 "200-299" */
        private String successStatusRange = "200-299";

        /** 是否跟随重定向 */
        private boolean followRedirects = true;
    }

    /**
     * 远程派发配置（P1-4）。
     *
     * <p>Leader 节点将分片任务通过 HTTP 派发到选定的执行器节点，
     * 实现真正的分布式分片执行（对标 XXL-Job / PowerJob 的远程派发）。
     *
     * <h3>工作流程</h3>
     * <ol>
     *   <li>Leader 计算分片分配方案（{@link com.njydsz.pmis.cronjob.server.core.sharding.ShardingStrategy}）</li>
     *   <li>本地分片：Leader 直接调用 {@code executeShard}</li>
     *   <li>远程分片：Leader 通过 HTTP POST 调用执行器节点的 {@code /cronjob/internal/execute}</li>
     *   <li>执行器节点接收请求后在本地执行，返回 logId</li>
     * </ol>
     *
     * <p>故障场景处理：
     * <ul>
     *   <li>HTTP 连接失败/超时：根据 {@link #fallbackToLocal} 决定是否降级本地执行</li>
     *   <li>执行器节点宕机：JobNodeReaper 故障转移释放分片锁并标记日志 FAILED</li>
     *   <li>重复执行风险：由 Redis 分布式锁兜底，同一分片只有一个节点能执行</li>
     * </ul>
     */
    @Data
    public static class Remote {
        /** 是否启用远程派发（false=所有分片在 Leader 本地执行，兼容旧行为） */
        private boolean enabled = true;

        /** HTTP 连接超时（秒） */
        private int connectTimeoutSeconds = 5;

        /** HTTP 请求超时（秒，包含连接+读取+执行） */
        private int requestTimeoutSeconds = 60;

        /** 远程派发失败时是否降级到 Leader 本地执行（true=保证分片不丢失） */
        private boolean fallbackToLocal = true;
    }

    /**
     * 告警扫描配置（P3-2 周期性告警扫描）。
     *
     * <p>控制 {@link com.njydsz.pmis.cronjob.server.core.alert.AlertScanner} 的扫描间隔。
     * 仅 Leader 节点启用周期性扫描，统计 FAIL_RATE / DURATION_P95 等需要聚合计算的告警类型。
     *
     * <p>对应配置前缀 {@code pmis.cronjob.alert.scan-interval-ms}（与 {@link AlertProperties} 共享前缀，
     * Spring Boot 会自动合并字段，无冲突）。
     */
    @Data
    public static class Alert {
        /** 告警扫描间隔（毫秒，默认 5 分钟） */
        private long scanIntervalMs = 300000L;
    }

    /**
     * P1-1: 节点发现策略配置。
     *
     * <p>控制执行器节点发现方式：
     * <ul>
     *   <li>{@code nacos}（默认）：基于 Nacos 服务发现，复用现有注册能力，无需维护心跳表</li>
     *   <li>{@code db}：基于 pmis_job_node 心跳表（向后兼容，需配合 JobNodeHeartbeat + JobNodeReaper）</li>
     * </ul>
     */
    @Data
    public static class NodeDiscovery {
        /** 节点发现策略: nacos(Nacos服务发现, 默认) / db(心跳表) */
        private String type = "nacos";
    }

    /**
     * P1-4: 失败自动转移配置（FailoverScanner）。
     *
     * <p>当执行器节点宕机后，Leader 节点定时扫描该节点上 RUNNING 状态的任务日志，
     * 标记为 FAILED 后以 triggerType=FAILOVER 重新派发任务。
     *
     * <h3>工作流程</h3>
     * <ol>
     *   <li>获取在线节点列表（Nacos 或 DB 心跳表）</li>
     *   <li>查询所有 RUNNING 日志的 exec_node_id，找出不在在线列表中的下线节点</li>
     *   <li>对每个下线节点：
     *     <ul>
     *       <li>调用 selectRunningByNode 获取 RUNNING 日志</li>
     *       <li>调用 markFailedByNodeOffline 标记为 FAILED</li>
     *       <li>对每条失败日志，若任务仍为 NORMAL 状态，重新派发（triggerType=FAILOVER）</li>
     *     </ul>
     *   </li>
     * </ol>
     */
    @Data
    public static class Failover {
        /** 是否启用故障转移扫描 */
        private boolean enabled = true;
        /** 扫描间隔（秒） */
        private int scanIntervalSeconds = 30;
        /** 单批最多扫描节点数 */
        private int scanNodeLimit = 10;
        /** 单节点最多转移任务数 */
        private int failoverTaskLimit = 50;
    }

    /**
     * P2-2: 日志归档清理配置。
     *
     * <p>控制 {@link com.njydsz.pmis.cronjob.server.core.cleaner.LogCleaner} 的清理行为：
     * <ul>
     *   <li>{@link #retentionDays} 日志保留天数（超过此天数的日志将被清理，默认 30 天）</li>
     *   <li>{@link #batchSize} 单批删除条数（避免大事务锁表，默认 1000 条/批）</li>
     * </ul>
     *
     * <p>清理范围：pmis_job_log / pmis_job_log_content /
     * pmis_job_alert_log / pmis_job_task，每天凌晨 3 点由 Leader 节点执行。
     */
    @Data
    public static class LogRetention {
        /** 日志保留天数（超过此天数的日志将被硬删除，默认 30 天） */
        private int retentionDays = 30;

        /** 单批删除条数（避免大事务锁表，默认 1000 条/批） */
        private int batchSize = 1000;

        /** 定时清理 cron 表达式（默认每天凌晨 3 点：0 0 3 * * ?） */
        private String cron = "0 0 3 * * ?";
    }

    /**
     * P0-1: MapReduce 分布式并行执行配置。
     *
     * <p>控制 MapReduce 子任务的分布式并行执行行为：
     * <ul>
     *   <li>{@link #isEnabled}: 是否启用分布式并行执行（false=单节点顺序执行，向后兼容）</li>
     *   <li>{@link #getMaxParallelSubTasks}: 最大并行子任务数（控制并行度，防止资源耗尽）</li>
     *   <li>{@link #getSubTaskTimeoutSeconds}: 单个子任务远程执行超时时间</li>
     * </ul>
     */
    @Data
    public static class MapReduce {
        /** 是否启用分布式并行执行（false=单节点顺序执行，向后兼容） */
        private boolean enabled = true;

        /** 最大并行子任务数（默认 8，控制并行度） */
        private int maxParallelSubTasks = 8;

        /** 单个子任务远程执行超时时间（秒，默认 120s） */
        private int subTaskTimeoutSeconds = 120;

        /** 远程子任务派发失败时是否降级本地执行 */
        private boolean fallbackToLocal = true;
    }

    /**
     * P0-2: 精准调度配置。
     *
     * <p>通过时间轮预加载机制提升 CRON 任务调度精度：
     * <ul>
     *   <li>{@link #isEnabled}: 是否启用精准调度（false=仅依赖 JobScanner 轮询，向后兼容）</li>
     *   <li>{@link #getPreLoadWindowSeconds}: 预加载窗口（秒），提前加载窗口内到期的任务</li>
     *   <li>{@link #getFastScanIntervalMs}: 快速扫描间隔（毫秒，默认 1s）</li>
     *   <li>{@link #getTimeWheelSlots}: 时间轮槽数（默认 60，对应 60 秒）</li>
     * </ul>
     *
     * <p>启用后，CRON 任务的触发精度从 ±5s（扫描间隔）提升到 ±0.1s（时间轮精度）。
     */
    @Data
    public static class PreciseScheduling {
        /** 是否启用精准调度（false=仅依赖 JobScanner 轮询，向后兼容） */
        private boolean enabled = false;

        /** 预加载窗口（秒），提前加载窗口内到期的任务到时间轮 */
        private int preLoadWindowSeconds = 10;

        /** 快速扫描间隔（毫秒，默认 1s） */
        private long fastScanIntervalMs = 1000;

        /** 时间轮槽数（默认 60，对应 60 秒一圈） */
        private int timeWheelSlots = 60;

        /** 精准调度线程池大小 */
        private int poolSize = 4;
    }

    /**
     * P3-12: 跨集群调度配置。
     *
     * <p>支持将任务派发到其他集群的执行器节点，实现多集群统一调度。
     * 任务的 {@code cluster} 字段指定目标集群（null=本地集群）。
     *
     * <p>配置示例（application.yml）:
     * <pre>{@code
     * pmis:
     *   cronjob:
     *     clusters:
     *       endpoints:
     *         cluster-bj: http://10.0.1.10:8080
     *         cluster-sh: http://10.0.2.10:8080
     * }</pre>
     */
    @Data
    public static class Clusters {
        /** 集群端点配置: clusterName -> baseUrl */
        private Map<String, String> endpoints = new HashMap<>();
    }

    /**
     * P3-11: 脚本执行沙箱配置。
     *
     * <p>控制 {@code SandboxScriptExecutor} 的安全隔离行为。
     * 启用后，SHELL/GLUE 类型任务的脚本将在受限环境中执行。
     */
    @Data
    public static class Sandbox {
        /** 是否启用沙箱模式（false=使用 ScriptJobHandler 原始执行逻辑） */
        private boolean enabled = false;

        /** 默认超时时间（秒） */
        private int timeoutSeconds = 300;

        /** 最大输出大小（字节，默认 1MB） */
        private int maxOutputSize = 1048576;

        /** 沙箱工作目录 */
        private String workDir = "./data/sandbox";

        // ==================== P2-11: Docker 沙箱增强 ====================

        /**
         * P2-11: 是否启用 Docker 容器沙箱（比进程沙箱更强的隔离）。
         *
         * <p>启用后，SHELL/Python 脚本在 Docker 容器中执行，提供文件系统隔离、
         * 网络隔离、资源限制和权限降级。
         * 需要宿主机安装 Docker 且应用有 docker 命令执行权限。
         */
        private boolean dockerEnabled = false;

        /** P2-11: 默认 Docker 镜像（Python 脚本） */
        private String dockerImage = "python:3.11-slim";

        /** P2-11: Shell 脚本 Docker 镜像 */
        private String dockerShellImage = "bash:5.2";

        /** P2-11: 容器内存限制（如 256m / 512m / 1g） */
        private String dockerMemory = "256m";

        /** P2-11: 容器 CPU 限制（核数，如 0.5 / 1 / 2） */
        private String dockerCpus = "1";

        /** P2-11: 容器最大进程数限制（防止 fork 炸弹） */
        private int dockerPidsLimit = 100;

        /** P2-11: 网络模式: none（禁网）/ bridge（默认桥接）/ host */
        private String dockerNetwork = "none";

        /** P2-11: 容器内运行用户（如 nobody / 1000:1000），空则使用镜像默认用户 */
        private String dockerUser = "nobody";

        /** P2-11: 容器内工作目录 */
        private String dockerWorkDir = "/tmp/sandbox";

        /** P2-11: tmpfs 挂载大小（如 10m / 50m），空则不挂载 tmpfs */
        private String dockerTmpfsSize = "10m";

        /** P2-11: 是否只读文件系统（--read-only） */
        private boolean dockerReadOnly = true;
    }

    /**
     * P0-1: 调度器-执行器分离配置。
     *
     * <p>启用后，Leader 节点仅负责调度扫描和任务派发，不再在本地执行任务。
     * 非分片任务也会通过 RemoteTaskClient 派发到选定的 Worker 节点执行。
     *
     * <h3>对标</h3>
     * <ul>
     *   <li>XXL-Job: 调度中心与执行器完全分离</li>
     *   <li>PowerJob: Server 与 Worker 分离</li>
     * </ul>
     *
     * <p>启用条件：
     * <ul>
     *   <li>remote.enabled = true（远程派发必须可用）</li>
     *   <li>至少 2 个在线节点（否则 Leader 仍需本地执行）</li>
     * </ul>
     */
    @Data
    public static class SchedulerExecutorSeparation {
        /**
         * 是否启用调度器-执行器分离（P1-5: 默认 true，对标 XXL-Job/PowerJob 的调度器-执行器分离架构）。
         *
         * <p>启用后，Leader 节点通过 WorkerNodeSelector 选定 Worker 节点远程派发任务，
         * 无可用 Worker 时自动降级为 Leader 本地执行（保证向后兼容）。
         * 运行条件：remote.enabled=true 且 WorkerNodeSelector Bean 已注册。
         */
        private boolean enabled = true;

        /** Worker 节点选择策略: round_robin(轮询) / least_load(最小负载) */
        private String workerSelectionStrategy = "round_robin";

        /** 单节点最大并行任务数（用于 least_load 策略的负载评估） */
        private int maxConcurrentPerWorker = 16;
    }

    /**
     * P1-1: 自适应批量调度配置。
     *
     * <p>根据系统实时负载指标（CPU、内存、线程池活跃度）动态调整 JobScanner 的 batchSize，
     * 避免高负载时大批量派发压垮系统，低负载时提升吞吐量。
     *
     * <h3>工作原理</h3>
     * <ol>
     *   <li>定时采集 JVM 和操作系统指标（CPU 使用率、堆内存使用率、线程池活跃线程数）</li>
     *   <li>根据负载评分计算最优 batchSize（低负载时放大，高负载时缩小）</li>
     *   <li>通过 AtomicReference 安全发布新值，JobScanner 下次扫描时自动生效</li>
     * </ol>
     *
     * <p>对标 PowerJob 的自适应调度和 SchedulerX 的流量控制能力。
     */
    @Data
    public static class AdaptiveBatch {
        /** 是否启用自适应批量调度（false=使用固定 batchSize，向后兼容） */
        private boolean enabled = false;

        /** 最小批量大小（高负载时不低于此值，防止饥饿） */
        private int minBatchSize = 50;

        /** 最大批量大小（低负载时不超过此值，防止 DB 连接耗尽） */
        private int maxBatchSize = 1000;

        /** CPU 使用率阈值（百分比），超过此值开始缩减批量 */
        private double cpuThreshold = 70.0;

        /** 内存使用率阈值（百分比），超过此值开始缩减批量 */
        private double memThreshold = 80.0;

        /** 线程池活跃度阈值（百分比，activeThreads/maxThreads），超过此值开始缩减批量 */
        private double poolActiveThreshold = 80.0;

        /** 负载评估间隔（秒，默认 10s） */
        private int evalIntervalSeconds = 10;
    }

    /**
     * P1-3: 告警智能降噪与聚合配置。
     *
     * <p>对同一任务/同组任务的告警进行时间窗口内聚合，避免告警风暴。
     * 支持基于告警频次的自动升降级：短时间内多次告警升级通知渠道，
     * 长时间无告警后自动恢复降级。
     *
     * <p>对标 SchedulerX 的告警降噪和 PowerJob 的告警聚合能力。
     */
    @Data
    public static class AlertDedup {
        /** 是否启用告警降噪（false=使用原有冷却窗口逻辑，向后兼容） */
        private boolean enabled = false;

        /** 聚合窗口（秒，窗口内同规则的告警合并为一条） */
        private int aggregateWindowSeconds = 60;

        /** 窗口内最大聚合告警数（超过此数触发升级通知） */
        private int maxAggregateCount = 5;

        /** 升级通知通道（如 "sms,phone"，在原有 channels 基础上追加） */
        private String escalateChannels = "sms";

        /** 降级冷却时间（秒，无告警后恢复原始通道） */
        private long downgradeCooldownSeconds = 3600;
    }

    /**
     * P3-1: AI 驱动调度优化配置。
     *
     * <p>基于历史执行数据预测任务执行时间和失败概率，辅助调度决策：
     * <ul>
     *   <li>预测执行时间 → 优化任务排队顺序</li>
     *   <li>预测失败概率 → 提前触发预警</li>
     *   <li>资源利用率预测 → 弹性扩缩容建议</li>
     * </ul>
     *
     * <p>采用指数加权移动平均（EWMA）作为基础预测模型，轻量无外部依赖。
     */
    @Data
    public static class AiScheduling {
        /** 是否启用 AI 调度优化（false=不启用预测，向后兼容） */
        private boolean enabled = false;

        /** EWMA 衰减因子（0-1，越大越偏向近期数据，默认 0.3） */
        private double ewmaAlpha = 0.3;

        /** 历史数据最小样本数（不足此数时不预测，使用默认值） */
        private int minSamples = 5;

        /** 最大历史样本数（滑动窗口大小） */
        private int maxSamples = 100;

        /** 预测失败概率阈值（超过此值触发预警，0-1） */
        private double failurePredictThreshold = 0.5;

        /** 预测评估间隔（分钟，定时从日志表统计并更新模型） */
        private int evalIntervalMinutes = 30;
    }

    /**
     * P3-2: 自愈系统配置。
     *
     * <p>定时检测异常状态的任务并自动修复：
     * <ul>
     *   <li>RUNNING 状态超时未更新 → 标记 FAILED 并重新派发</li>
     *   <li>AUTO_PAUSED 状态到达恢复时间 → 自动恢复为 NORMAL</li>
     *   <li>连续失败任务 → 触发降级通知</li>
     *   <li>孤儿任务（所属节点下线但日志仍 RUNNING） → 清理并转移</li>
     * </ul>
     *
     * <p>对标 PowerJob 的自愈能力和 SchedulerX 的自动恢复机制。
     */
    @Data
    public static class SelfHealing {
        /** 是否启用自愈系统（false=不启用，向后兼容） */
        private boolean enabled = false;

        /** 检测间隔（秒，默认 60s） */
        private int scanIntervalSeconds = 60;

        /** RUNNING 状态无更新超时阈值（秒，超过此值视为卡死） */
        private int stuckThresholdSeconds = 300;

        /** 单次扫描最大修复任务数（防止批量修复压垮系统） */
        private int maxHealPerScan = 20;

        /** 是否自动重新派发修复后的任务 */
        private boolean autoRedispatch = true;

        /** 重新派发最大重试次数（超过此数不再自动派发，标记为需人工介入） */
        private int maxRedispatchRetries = 3;
    }
}

