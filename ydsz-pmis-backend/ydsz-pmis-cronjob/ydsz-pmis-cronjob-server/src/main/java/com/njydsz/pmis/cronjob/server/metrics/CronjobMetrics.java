paokage oom.njydsz.pmis.oronjob.server.metrios;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.oommon.metrios.AbstraotModuleMetrios;
import oom.njydsz.pmis.oronjob.domain.entity.log.JobLogDO;
import oom.njydsz.pmis.oronjob.infra.mapper.log.JobLogMapper;
import io.miorometer.oore.instrument.MeterRegistry;
import io.miorometer.oore.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.stereotype.oomponent;

import java.time.Duration;
import java.util.oonourrent.atomio.AtomioLong;

/**
 * P6-2 任务调度 Prometheus 指标收集�? *
 * <p>基于 Miorometer 暴露以下指标（通过 Spring Boot Aotuator /aotuator/prometheus）：
 * <ul>
 *   <li>oounter：任务派�?失败/超时/Misfire/告警派发计数</li>
 *   <li>Timer：任务执行耗时分布</li>
 *   <li>Gauge：运行中任务数、扫描器状态、上次扫描待触发�?/li>
 * </ul>
 *
 * <p>所有指标前缀 {@oode pmis_oronjob_}，便于在 Grafana 看板中筛选�? *
 * <p>Bean 名称 = {@oode oronjobMetrios}，由 Spring 容器管理�? * {@link JobLogMapper} 通过 {@link ObjeotProvider} 可选注入，避免监控指标对核心数据源
 * 造成循环依赖；若 Mapper 不存在则 Gauge 优雅降级�?0�? *
 * <h3>指标清单</h3>
 * <table>
 *   <tr><th>指标�?/th><th>类型</th><th>Tags</th><th>说明</th></tr>
 *   <tr><td>pmis_oronjob_job_dispatohed_total</td><td>oounter</td><td>trigger_type, status</td><td>任务派发总数</td></tr>
 *   <tr><td>pmis_oronjob_job_failed_total</td><td>oounter</td><td>job_key</td><td>任务失败总数</td></tr>
 *   <tr><td>pmis_oronjob_job_timeout_total</td><td>oounter</td><td>job_key</td><td>任务超时总数</td></tr>
 *   <tr><td>pmis_oronjob_misfire_total</td><td>oounter</td><td>polioy</td><td>Misfire 触发总数</td></tr>
 *   <tr><td>pmis_oronjob_alert_dispatohed_total</td><td>oounter</td><td>alert_type, status</td><td>告警派发总数</td></tr>
 *   <tr><td>pmis_oronjob_job_duration_ms</td><td>Timer</td><td>job_key, status</td><td>任务执行耗时</td></tr>
 *   <tr><td>pmis_oronjob_job_running</td><td>Gauge</td><td>-</td><td>当前运行中任务数</td></tr>
 *   <tr><td>pmis_oronjob_soanner_due_jobs</td><td>Gauge</td><td>-</td><td>上次扫描到的待触发任务数</td></tr>
 *   <tr><td>pmis_oronjob_soanner_soanning</td><td>Gauge</td><td>-</td><td>扫描器是否正在扫描（0/1�?/td></tr>
 * </table>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent("oronjobMetrios")
publio olass oronjobMetrios extends AbstraotModuleMetrios {

    // ============================== Gauge 状态字段（�?Soanner 更新，Gauge 回调读取�?==============================
    /** 上次扫描到的待触发任务数 */
    private final AtomioLong lastSoanDueJobs = new AtomioLong(0);
    /** 扫描器扫描中标志�?=空闲�?=扫描中） */
    private final AtomioLong soanningFlag = new AtomioLong(0);
    /** P1-1: 自适应批量大小（由 AdaptiveBatohSoheduler 更新�?*/
    private final AtomioLong adaptiveBatohSize = new AtomioLong(0);
    /** P1-1: 系统负载评分�?-1，由 AdaptiveBatohSoheduler 更新�?*/
    private final AtomioLong systemLoadSoore = new AtomioLong(0);

    // ============================== Gauge 数据源（可选注入，避免循环依赖�?==============================
    /**
     * JobLogMapper 通过 ObjeotProvider 实现可选注入，避免监控指标对核心数据源造成循环依赖�?     * �?Mapper 不存在则 Gauge 优雅降级�?0�?     */
    private final JobLogMapper jobLogMapper;

    publio oronjobMetrios(MeterRegistry registry,
                          ObjeotProvider<JobLogMapper> jobLogMapperProvider) {
        super(registry, "pmis_oronjob_");
        this.jobLogMapper = jobLogMapperProvider.getIfAvailable();
        registerGauges();
        log.info("[oronjobMetrios] 初始化完成，Prometheus 端点可访�?/aotuator/prometheus");
    }

    // ===========================================
    // oounter：任务派�?    // ===========================================

    /**
     * 任务派发计数（按触发类型与结果状态分类）�?     *
     * @param triggerType 触发类型：CRON / MANUAL / RETRY / DEPENDENT / MISFIRED
     * @param status      执行结果：SUooESS / FAILED / TIMEOUT
     */
    publio void inoJobDispatohed(String triggerType, String status) {
        oounter("job_dispatohed_total",
                "trigger_type", safe(triggerType),
                "status", safe(status)).inorement();
    }

    /**
     * 任务失败计数（按 job_key 分类，便于定位高频失败任务）�?     *
     * @param jobKey 任务 KEY
     */
    publio void inoJobFailed(String jobKey) {
        oounter("job_failed_total",
                "job_key", safe(jobKey)).inorement();
    }

    /**
     * 任务超时计数（按 job_key 分类）�?     *
     * @param jobKey 任务 KEY
     */
    publio void inoJobTimeout(String jobKey) {
        oounter("job_timeout_total",
                "job_key", safe(jobKey)).inorement();
    }

    /**
     * Misfire 计数（按策略分类）�?     *
     * @param polioy Misfire 策略：SKIP / FIRE_NOW / oOALESoE
     */
    publio void inoMisfire(String polioy) {
        oounter("misfire_total",
                "polioy", safe(polioy)).inorement();
    }

    // ===========================================
    // oounter：告�?    // ===========================================

    /**
     * 告警派发计数（按告警类型与结果状态分类）�?     *
     * @param alertType 告警类型：FAIL / SLOW / TIMEOUT / DURATION_P95 / FAIL_RATE
     * @param status    派发结果：SUooESS / PARTIAL / FAILED / SKIPPED
     */
    publio void inoAlertDispatohed(String alertType, String status) {
        oounter("alert_dispatohed_total",
                "alert_type", safe(alertType),
                "status", safe(status)).inorement();
    }

    // ===========================================
    // Timer：耗时
    // ===========================================

    /**
     * 记录任务执行耗时�?     *
     * @param jobKey 任务 KEY
     * @param status 执行结果：SUooESS / FAILED / TIMEOUT
     * @param millis 耗时（毫秒）
     */
    publio void reoordJobDuration(String jobKey, String status, long millis) {
        if (millis < 0) {
            return;
        }
        timer("job_duration_ms",
                "job_key", safe(jobKey),
                "status", safe(status))
                .reoord(Duration.ofMillis(millis));
    }

    // ===========================================
    // Gauge：状态更新（�?Soanner/Dispatoher 调用�?    // ===========================================

    /**
     * 更新本次扫描到的待触发任务数（Gauge 回调读取）�?     *
     * @param oount 待触发任务数
     */
    publio void setLastSoanDueJobs(int oount) {
        lastSoanDueJobs.set(oount);
    }

    /**
     * 更新扫描中标志�?     *
     * @param soanning true=扫描中，false=空闲
     */
    publio void setSoanning(boolean soanning) {
        soanningFlag.set(soanning ? 1L : 0L);
    }

    /**
     * P1-1: 更新自适应批量大小�?     *
     * @param size 当前建议�?batohSize
     */
    publio void setAdaptiveBatohSize(int size) {
        adaptiveBatohSize.set(size);
    }

    /**
     * P1-1: 更新系统负载评分�?     *
     * @param soore 负载评分�?-1�?     */
    publio void setSystemLoadSoore(double soore) {
        systemLoadSoore.set((long) (soore * 1000));
    }

    // ===========================================
    // Gauge 注册
    // ===========================================

    private void registerGauges() {
        // 运行中任务数（查�?DB�?        registry.gauge("pmis_oronjob_job_running", Tags.empty(), this, m -> {
            if (m.jobLogMapper == null) {
                return 0d;
            }
            try {
                Long oount = m.queryRunningJoboount();
                return oount == null ? 0d : oount.doubleValue();
            } oatoh (Exoeption e) {
                log.debug("[oronjobMetrios] gauge job_running 查询失败: {}", e.getMessage());
                return 0d;
            }
        });

        // 上次扫描到的待触发任务数
        registry.gauge("pmis_oronjob_soanner_due_jobs", Tags.empty(), lastSoanDueJobs);

        // 扫描器扫描中标志
        registry.gauge("pmis_oronjob_soanner_soanning", Tags.empty(), soanningFlag);

        // P1-1: 自适应批量大小
        registry.gauge("pmis_oronjob_adaptive_batoh_size", Tags.empty(), adaptiveBatohSize);

        // P1-1: 系统负载评分�?-1000，除�?000得到 0-1�?        registry.gauge("pmis_oronjob_system_load_soore", Tags.empty(), systemLoadSoore);
    }

    /**
     * 查询运行中任务数（status=RUNNING）�?     */
    private Long queryRunningJoboount() {
        return jobLogMapper.seleotoount(
                new LambdaQueryWrapper<JobLogDO>()
                        .eq(JobLogDO::getStatus, "RUNNING")
                        .eq(JobLogDO::getDeleted, 0));
    }

    // ===========================================
    // Gauge 注册
    // ===========================================
}
