package com.njydsz.pmis.cronjob.metrics;

import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link CronjobMetrics} 单元测试（P6-2 Metrics 指标）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>Counter：任务派发/失败/超时/Misfire/告警派发计数</li>
 *   <li>Timer：任务执行耗时记录</li>
 *   <li>Gauge：扫描器状态、待触发任务数、运行中任务数</li>
 *   <li>Mapper 可选注入（null 时 Gauge 降级为 0）</li>
 *   <li>safe() 空值兜底</li>
 *   <li>Counter/Timer 缓存幂等性</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("CronjobMetrics Prometheus 指标收集器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CronjobMetricsTest {

    private SimpleMeterRegistry registry;

    @Mock
    private ObjectProvider<JobLogMapper> jobLogMapperProvider;
    @Mock
    private JobLogMapper jobLogMapper;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    // ===========================================
    // 构造与初始化
    // ===========================================

    @Test
    @DisplayName("构造时 JobLogMapper 可用，Gauge 正常注册")
    void construct_mapperAvailable_gaugesRegistered() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(jobLogMapper);
        when(jobLogMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(3L);

        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        Gauge runningGauge = registry.find("pmis_cronjob_job_running").gauge();
        assertNotNull(runningGauge, "pmis_cronjob_job_running Gauge 应已注册");
        assertEquals(3.0, runningGauge.value(), 0.001);

        Gauge dueJobsGauge = registry.find("pmis_cronjob_scanner_due_jobs").gauge();
        assertNotNull(dueJobsGauge, "pmis_cronjob_scanner_due_jobs Gauge 应已注册");
        assertEquals(0.0, dueJobsGauge.value(), 0.001);

        Gauge scanningGauge = registry.find("pmis_cronjob_scanner_scanning").gauge();
        assertNotNull(scanningGauge, "pmis_cronjob_scanner_scanning Gauge 应已注册");
        assertEquals(0.0, scanningGauge.value(), 0.001);
    }

    @Test
    @DisplayName("构造时 JobLogMapper 为 null，job_running Gauge 降级为 0")
    void construct_mapperNull_gaugeDegradesToZero() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(null);

        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        Gauge runningGauge = registry.find("pmis_cronjob_job_running").gauge();
        assertNotNull(runningGauge, "Gauge 仍应注册");
        assertEquals(0.0, runningGauge.value(), 0.001);
    }

    @Test
    @DisplayName("Gauge job_running 查询异常时降级为 0")
    void gauge_jobRunningQueryThrows_returnsZero() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(jobLogMapper);
        when(jobLogMapper.selectCount(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        Gauge runningGauge = registry.find("pmis_cronjob_job_running").gauge();
        assertNotNull(runningGauge);
        assertEquals(0.0, runningGauge.value(), 0.001);
    }

    // ===========================================
    // Counter：任务派发
    // ===========================================

    @Test
    @DisplayName("incJobDispatched: 按 trigger_type + status 分类计数")
    void incJobDispatched_incrementsCounterByTags() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(null);
        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        metrics.incJobDispatched("CRON", "SUCCESS");
        metrics.incJobDispatched("CRON", "SUCCESS");
        metrics.incJobDispatched("CRON", "FAILED");
        metrics.incJobDispatched("MANUAL", "SUCCESS");

        Counter cronSuccess = registry.find("pmis_cronjob_job_dispatched_total")
                .tag("trigger_type", "CRON").tag("status", "SUCCESS").counter();
        assertNotNull(cronSuccess);
        assertEquals(2.0, cronSuccess.count(), 0.001);

        Counter cronFailed = registry.find("pmis_cronjob_job_dispatched_total")
                .tag("trigger_type", "CRON").tag("status", "FAILED").counter();
        assertNotNull(cronFailed);
        assertEquals(1.0, cronFailed.count(), 0.001);

        Counter manualSuccess = registry.find("pmis_cronjob_job_dispatched_total")
                .tag("trigger_type", "MANUAL").tag("status", "SUCCESS").counter();
        assertNotNull(manualSuccess);
        assertEquals(1.0, manualSuccess.count(), 0.001);
    }

    @Test
    @DisplayName("incJobDispatched: null/空参数时 tag 值兜底为 unknown")
    void incJobDispatched_nullTags_defaultToUnknown() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(null);
        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        metrics.incJobDispatched(null, null);
        metrics.incJobDispatched("", "");

        Counter counter = registry.find("pmis_cronjob_job_dispatched_total")
                .tag("trigger_type", "unknown").tag("status", "unknown").counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count(), 0.001);
    }

    @Test
    @DisplayName("incJobFailed: 按 job_key 分类计数")
    void incJobFailed_incrementsCounterByJobKey() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(null);
        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        metrics.incJobFailed("report-job");
        metrics.incJobFailed("report-job");
        metrics.incJobFailed("cleanup-job");

        Counter reportCounter = registry.find("pmis_cronjob_job_failed_total")
                .tag("job_key", "report-job").counter();
        assertNotNull(reportCounter);
        assertEquals(2.0, reportCounter.count(), 0.001);

        Counter cleanupCounter = registry.find("pmis_cronjob_job_failed_total")
                .tag("job_key", "cleanup-job").counter();
        assertNotNull(cleanupCounter);
        assertEquals(1.0, cleanupCounter.count(), 0.001);
    }

    @Test
    @DisplayName("incJobTimeout: 按 job_key 分类计数")
    void incJobTimeout_incrementsCounterByJobKey() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(null);
        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        metrics.incJobTimeout("slow-job");

        Counter counter = registry.find("pmis_cronjob_job_timeout_total")
                .tag("job_key", "slow-job").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count(), 0.001);
    }

    @Test
    @DisplayName("incMisfire: 按 policy 分类计数")
    void incMisfire_incrementsCounterByPolicy() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(null);
        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        metrics.incMisfire("SKIP");
        metrics.incMisfire("COALESCE");
        metrics.incMisfire("FIRE_NOW");
        metrics.incMisfire("FIRE_NOW");

        Counter skipCounter = registry.find("pmis_cronjob_misfire_total")
                .tag("policy", "SKIP").counter();
        assertNotNull(skipCounter);
        assertEquals(1.0, skipCounter.count(), 0.001);

        Counter fireNowCounter = registry.find("pmis_cronjob_misfire_total")
                .tag("policy", "FIRE_NOW").counter();
        assertNotNull(fireNowCounter);
        assertEquals(2.0, fireNowCounter.count(), 0.001);
    }

    // ===========================================
    // Counter：告警
    // ===========================================

    @Test
    @DisplayName("incAlertDispatched: 按 alert_type + status 分类计数")
    void incAlertDispatched_incrementsCounterByTags() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(null);
        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        metrics.incAlertDispatched("FAIL", "SUCCESS");
        metrics.incAlertDispatched("FAIL", "PARTIAL");
        metrics.incAlertDispatched("SLOW", "FAILED");

        Counter failSuccess = registry.find("pmis_cronjob_alert_dispatched_total")
                .tag("alert_type", "FAIL").tag("status", "SUCCESS").counter();
        assertNotNull(failSuccess);
        assertEquals(1.0, failSuccess.count(), 0.001);

        Counter failPartial = registry.find("pmis_cronjob_alert_dispatched_total")
                .tag("alert_type", "FAIL").tag("status", "PARTIAL").counter();
        assertNotNull(failPartial);
        assertEquals(1.0, failPartial.count(), 0.001);

        Counter slowFailed = registry.find("pmis_cronjob_alert_dispatched_total")
                .tag("alert_type", "SLOW").tag("status", "FAILED").counter();
        assertNotNull(slowFailed);
        assertEquals(1.0, slowFailed.count(), 0.001);
    }

    // ===========================================
    // Timer：任务执行耗时
    // ===========================================

    @Test
    @DisplayName("recordJobDuration: 正常记录耗时")
    void recordJobDuration_recordsTimer() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(null);
        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        metrics.recordJobDuration("report-job", "SUCCESS", 500);
        metrics.recordJobDuration("report-job", "SUCCESS", 1500);

        Timer timer = registry.find("pmis_cronjob_job_duration_ms")
                .tag("job_key", "report-job").tag("status", "SUCCESS").timer();
        assertNotNull(timer);
        assertEquals(2, timer.count());
        assertEquals(2000.0, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS), 1.0);
    }

    @Test
    @DisplayName("recordJobDuration: 负数 millis 时跳过不记录")
    void recordJobDuration_negativeMillis_skipped() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(null);
        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        metrics.recordJobDuration("report-job", "SUCCESS", -1);

        Timer timer = registry.find("pmis_cronjob_job_duration_ms")
                .tag("job_key", "report-job").tag("status", "SUCCESS").timer();
        assertNull(timer, "负数耗时应跳过，Timer 不应被注册");
    }

    // ===========================================
    // Gauge：状态更新
    // ===========================================

    @Test
    @DisplayName("setLastScanDueJobs: 更新待触发任务数 Gauge")
    void setLastScanDueJobs_updatesGauge() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(null);
        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        Gauge dueJobsGauge = registry.find("pmis_cronjob_scanner_due_jobs").gauge();
        assertNotNull(dueJobsGauge);
        assertEquals(0.0, dueJobsGauge.value(), 0.001);

        metrics.setLastScanDueJobs(15);
        assertEquals(15.0, dueJobsGauge.value(), 0.001);

        metrics.setLastScanDueJobs(0);
        assertEquals(0.0, dueJobsGauge.value(), 0.001);
    }

    @Test
    @DisplayName("setScanning: 更新扫描中标志 Gauge")
    void setScanning_updatesGauge() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(null);
        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        Gauge scanningGauge = registry.find("pmis_cronjob_scanner_scanning").gauge();
        assertNotNull(scanningGauge);
        assertEquals(0.0, scanningGauge.value(), 0.001);

        metrics.setScanning(true);
        assertEquals(1.0, scanningGauge.value(), 0.001);

        metrics.setScanning(false);
        assertEquals(0.0, scanningGauge.value(), 0.001);
    }

    // ===========================================
    // Counter/Timer 缓存幂等性
    // ===========================================

    @Test
    @DisplayName("重复调用相同 tag 的 Counter 不重复注册")
    void counterCache_idempotentRegistration() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(null);
        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        // 多次调用相同 tag
        metrics.incJobFailed("job-1");
        metrics.incJobFailed("job-1");
        metrics.incJobFailed("job-1");

        // 只应有一个 Counter 实例
        long counterCount = registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("pmis_cronjob_job_failed_total"))
                .count();
        assertEquals(1, counterCount, "相同 tag 的 Counter 只应注册一次");

        Counter counter = registry.find("pmis_cronjob_job_failed_total")
                .tag("job_key", "job-1").counter();
        assertNotNull(counter);
        assertEquals(3.0, counter.count(), 0.001);
    }

    @Test
    @DisplayName("不同 tag 的 Counter 独立注册")
    void counterCache_differentTagsRegisteredSeparately() {
        when(jobLogMapperProvider.getIfAvailable()).thenReturn(null);
        CronjobMetrics metrics = new CronjobMetrics(registry, jobLogMapperProvider);

        metrics.incJobFailed("job-A");
        metrics.incJobFailed("job-B");

        long counterCount = registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("pmis_cronjob_job_failed_total"))
                .count();
        assertEquals(2, counterCount, "不同 job_key 应注册为独立 Counter");
    }
}
