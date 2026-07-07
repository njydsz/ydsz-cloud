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

    /** 执行器配置（P1 阶段新增） */
    private Executor executor = new Executor();

    /** 租户级配额配置（P7-2 新增） */
    private Quota quota = new Quota();

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
        /** 是否启用 Leader 选举模式（false=回退旧的 Leaderless 模式） */
        private boolean enabled = false;

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
}

