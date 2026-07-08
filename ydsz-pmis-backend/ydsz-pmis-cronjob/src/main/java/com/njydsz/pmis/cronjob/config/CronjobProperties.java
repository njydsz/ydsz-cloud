package com.njydsz.pmis.cronjob.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

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
    }

    /**
     * 任务扫描器配置。
     */
    @Data
    public static class Scanner {
        /** 扫描间隔（毫秒，默认 5s） */
        private long intervalMs = 5000;

        /** 单批最多触发任务数 */
        private int batchSize = 100;

        /** Misfire 宽容窗口（分钟，超过此窗口的任务按 misfire_policy 处理） */
        private int misfireGraceMinutes = 30;
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
     *   <li>Leader 计算分片分配方案（{@link com.njydsz.pmis.cronjob.core.sharding.ShardingStrategy}）</li>
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
     * <p>控制 {@link com.njydsz.pmis.cronjob.core.alert.AlertScanner} 的扫描间隔。
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
     * <p>控制 {@link com.njydsz.pmis.cronjob.core.cleaner.LogCleaner} 的清理行为：
     * <ul>
     *   <li>{@link #retentionDays} 日志保留天数（超过此天数的日志将被清理，默认 30 天）</li>
     *   <li>{@link #batchSize} 单批删除条数（避免大事务锁表，默认 1000 条/批）</li>
     * </ul>
     *
     * <p>清理范围：pmis_job_log / pmis_job_log_content / pmis_job_slow_log /
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
}

