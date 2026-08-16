package com.njydsz.cronjob.server.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;
import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;

/**
 * P6-2 任务调度 Prometheus 指标收集器
 *
 * <p>基于 Micrometer 暴露以下指标（通过 Spring Boot Actuator /actuator/prometheus）：
 * <ul>
 *   <li>Counter：任务派发/失败/超时/Misfire/告警派发计数</li>
 *   <li>Timer：任务执行耗时分布</li>
 *   <li>Gauge：运行中任务数、扫描器状态、上次扫描待触发数</li>
 * </ul>
 *
 * <p>所有指标前缀 {@code ydsz_cronjob_}，便于在 Grafana 看板中筛选。
 *
 * <p>Bean 名称 = {@code cronjobMetrics}，由 Spring 容器管理。
 * {@link JobLogMapper} 通过 {@link ObjectProvider} 可选注入，避免监控指标对核心数据源
 * 造成循环依赖；若 Mapper 不存在则 Gauge 优雅降级为 0。
 *
 * <h3>指标清单</h3>
 * <table>
 *   <tr><th>指标名</th><th>类型</th><th>Tags</th><th>说明</th></tr>
 *   <tr><td>ydsz_cronjob_job_dispatched_total</td><td>Counter</td><td>trigger_type, status</td><td>任务派发总数</td></tr>
 *   <tr><td>ydsz_cronjob_job_failed_total</td><td>Counter</td><td>job_key</td><td>任务失败总数</td></tr>
 *   <tr><td>ydsz_cronjob_job_timeout_total</td><td>Counter</td><td>job_key</td><td>任务超时总数</td></tr>
 *   <tr><td>ydsz_cronjob_misfire_total</td><td>Counter</td><td>policy</td><td>Misfire 触发总数</td></tr>
 *   <tr><td>ydsz_cronjob_alert_dispatched_total</td><td>Counter</td><td>alert_type, status</td><td>告警派发总数</td></tr>
 *   <tr><td>ydsz_cronjob_job_duration_ms</td><td>Timer</td><td>job_key, status</td><td>任务执行耗时</td></tr>
 *   <tr><td>ydsz_cronjob_job_running</td><td>Gauge</td><td>-</td><td>当前运行中任务数</td></tr>
 *   <tr><td>ydsz_cronjob_scanner_due_jobs</td><td>Gauge</td><td>-</td><td>上次扫描到的待触发任务数</td></tr>
 *   <tr><td>ydsz_cronjob_scanner_scanning</td><td>Gauge</td><td>-</td><td>扫描器是否正在扫描（0/1）</td></tr>
 * </table>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component("cronjobMetrics")
public class CronjobMetrics extends SentryMetricsAdapter {

    /** 上次查询运行中任务数的缓存值（30s TTL，避免高频 Gauge 回调压垮 DB） */
    private volatile Long cachedRunningCount = null;
    private volatile long cachedRunningCountExpireAt = 0L;
    private static final long RUNNING_COUNT_CACHE_TTL_MS = 30_000L;

    // ============================== Gauge 状态字段（由 Scanner 更新，Gauge 回调读取） ==============================
    /** 上次扫描到的待触发任务数 */
    private final AtomicLong lastScanDueJobs = new AtomicLong(0);
    /** 扫描器扫描中标志（0=空闲，1=扫描中） */
    private final AtomicLong scanningFlag = new AtomicLong(0);
    /** P1-1: 自适应批量大小（由 AdaptiveBatchScheduler 更新） */
    private final AtomicLong adaptiveBatchSize = new AtomicLong(0);
    /** P1-1: 系统负载评分（0-1，由 AdaptiveBatchScheduler 更新） */
    private final AtomicLong systemLoadScore = new AtomicLong(0);

    // ============================== Gauge 数据源（可选注入，避免循环依赖） ==============================
    /**
     * JobLogMapper 通过 ObjectProvider 实现可选注入，避免监控指标对核心数据源造成循环依赖。
     * 若 Mapper 不存在则 Gauge 优雅降级为 0。
     */
    private final JobLogMapper jobLogMapper;

    public CronjobMetrics(MeterRegistry registry,
                          ObjectProvider<JobLogMapper> jobLogMapperProvider) {
        super(registry, "ydsz_cronjob_");
        this.jobLogMapper = jobLogMapperProvider.getIfAvailable();
        registerGauges();
        log.info("[CronjobMetrics] 初始化完成，Prometheus 端点可访问 /actuator/prometheus");
    }

    // ===========================================
    // Counter：任务派发
    // ===========================================

    /**
     * 任务派发计数（按触发类型与结果状态分类）。
     *
     * @param triggerType 触发类型：CRON / MANUAL / RETRY / DEPENDENT / MISFIRED
     * @param status      执行结果：SUCCESS / FAILED / TIMEOUT
     */
    public void incJobDispatched(String triggerType, String status) {
        counter("job_dispatched_total",
                "trigger_type", safe(triggerType),
                "status", safe(status)).increment();
    }

    /**
     * 任务失败计数（按 job_key 分类，便于定位高频失败任务）。
     *
     * @param jobKey 任务 KEY
     */
    public void incJobFailed(String jobKey) {
        counter("job_failed_total",
                "job_key", safe(jobKey)).increment();
    }

    /**
     * 任务超时计数（按 job_key 分类）。
     *
     * @param jobKey 任务 KEY
     */
    public void incJobTimeout(String jobKey) {
        counter("job_timeout_total",
                "job_key", safe(jobKey)).increment();
    }

    /**
     * Misfire 计数（按策略分类）。
     *
     * @param policy Misfire 策略：SKIP / FIRE_NOW / COALESCE
     */
    public void incMisfire(String policy) {
        counter("misfire_total",
                "policy", safe(policy)).increment();
    }

    // ===========================================
    // Counter：告警
    // ===========================================

    /**
     * 告警派发计数（按告警类型与结果状态分类）。
     *
     * @param alertType 告警类型：FAIL / SLOW / TIMEOUT / DURATION_P95 / FAIL_RATE
     * @param status    派发结果：SUCCESS / PARTIAL / FAILED / SKIPPED
     */
    public void incAlertDispatched(String alertType, String status) {
        counter("alert_dispatched_total",
                "alert_type", safe(alertType),
                "status", safe(status)).increment();
    }

    // ===========================================
    // P1-10: 补充指标 — DAG / 分片 / 重试
    // ===========================================

    /**
     * P1-10: DAG 执行计数（按状态分类）。
     *
     * @param dagKey   DAG KEY
     * @param status   执行结果：SUCCESS / FAILED / TIMEOUT / PARTIAL_SUCCESS
     */
    public void incDagExecution(String dagKey, String status) {
        counter("dag_execution_total",
                "dag_key", safe(dagKey),
                "status", safe(status)).increment();
    }

    /**
     * P1-10: DAG 执行耗时记录。
     *
     * @param dagKey DAG KEY
     * @param status 执行结果
     * @param millis 耗时（毫秒）
     */
    public void recordDagDuration(String dagKey, String status, long millis) {
        if (millis < 0) {
            return;
        }
        timer("dag_duration_ms",
                "dag_key", safe(dagKey),
                "status", safe(status))
                .record(Duration.ofMillis(millis));
    }

    /**
     * P1-10: 分片任务派发计数。
     *
     * @param jobKey     任务 KEY
     * @param shardIndex 分片索引
     * @param status     执行结果
     */
    public void incShardDispatched(String jobKey, int shardIndex, String status) {
        counter("shard_dispatched_total",
                "job_key", safe(jobKey),
                "shard", String.valueOf(shardIndex),
                "status", safe(status)).increment();
    }

    /**
     * P1-10: 任务重试计数（按触发类型分类）。
     *
     * @param jobKey       任务 KEY
     * @param triggerType  触发类型：RETRY / FAILOVER / SELF_HEALING
     */
    public void incJobRetry(String jobKey, String triggerType) {
        counter("job_retry_total",
                "job_key", safe(jobKey),
                "trigger_type", safe(triggerType)).increment();
    }

    /**
     * P1-10: 任务队列等待耗时记录（从入队到派发的耗时）。
     *
     * @param jobKey 任务 KEY
     * @param millis 等待耗时（毫秒）
     */
    public void recordQueueWaitDuration(String jobKey, long millis) {
        if (millis < 0) {
            return;
        }
        timer("job_queue_wait_ms",
                "job_key", safe(jobKey))
                .record(Duration.ofMillis(millis));
    }

    // ===========================================
    // Timer：耗时
    // ===========================================

    /**
     * 记录任务执行耗时。
     *
     * @param jobKey 任务 KEY
     * @param status 执行结果：SUCCESS / FAILED / TIMEOUT
     * @param millis 耗时（毫秒）
     */
    public void recordJobDuration(String jobKey, String status, long millis) {
        if (millis < 0) {
            return;
        }
        timer("job_duration_ms",
                "job_key", safe(jobKey),
                "status", safe(status))
                .record(Duration.ofMillis(millis));
    }

    // ===========================================
    // Gauge：状态更新（由 Scanner/Dispatcher 调用）
    // ===========================================

    /**
     * 更新本次扫描到的待触发任务数（Gauge 回调读取）。
     *
     * @param count 待触发任务数
     */
    public void setLastScanDueJobs(int count) {
        lastScanDueJobs.set(count);
    }

    /**
     * 更新扫描中标志。
     *
     * @param scanning true=扫描中，false=空闲
     */
    public void setScanning(boolean scanning) {
        scanningFlag.set(scanning ? 1L : 0L);
    }

    /**
     * P1-1: 更新自适应批量大小。
     *
     * @param size 当前建议的 batchSize
     */
    public void setAdaptiveBatchSize(int size) {
        adaptiveBatchSize.set(size);
    }

    /**
     * P1-1: 更新系统负载评分。
     *
     * @param score 负载评分（0-1）
     */
    public void setSystemLoadScore(double score) {
        systemLoadScore.set((long) (score * 1000));
    }

    // ===========================================
    // Gauge 注册
    // ===========================================

    private void registerGauges() {
        // 运行中任务数（查询 DB）
        registry.gauge("ydsz_cronjob_job_running", Tags.empty(), this, m -> {
            if (m.jobLogMapper == null) {
                return 0d;
            }
            try {
                Long count = m.queryRunningJobCount();
                return count == null ? 0d : count.doubleValue();
            } catch (Exception e) {
                log.debug("[CronjobMetrics] gauge job_running 查询失败: {}", e.getMessage());
                return 0d;
            }
        });

        // 上次扫描到的待触发任务数
        registry.gauge("ydsz_cronjob_scanner_due_jobs", Tags.empty(), lastScanDueJobs);

        // 扫描器扫描中标志
        registry.gauge("ydsz_cronjob_scanner_scanning", Tags.empty(), scanningFlag);

        // P1-1: 自适应批量大小
        registry.gauge("ydsz_cronjob_adaptive_batch_size", Tags.empty(), adaptiveBatchSize);

        // P1-1: 系统负载评分（0-1000，除以1000得到 0-1）
        registry.gauge("ydsz_cronjob_system_load_score", Tags.empty(), systemLoadScore);
    }

    /**
     * 查询运行中任务数（status=RUNNING），30s 缓存避免高频 Gauge 回调压垮 DB。
     */
    private Long queryRunningJobCount() {
        long now = System.currentTimeMillis();
        if (cachedRunningCount != null && now < cachedRunningCountExpireAt) {
            return cachedRunningCount;
        }
        Long count = jobLogMapper.selectCount(
                new LambdaQueryWrapper<JobLog>()
                        .eq(JobLog::getStatus, "RUNNING")
                        .eq(JobLog::getDeleted, 0));
        cachedRunningCount = count;
        cachedRunningCountExpireAt = now + RUNNING_COUNT_CACHE_TTL_MS;
        return count;
    }
}
