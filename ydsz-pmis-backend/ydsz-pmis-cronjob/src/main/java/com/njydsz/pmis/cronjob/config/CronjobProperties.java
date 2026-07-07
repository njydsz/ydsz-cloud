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
}
