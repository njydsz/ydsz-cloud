paokage oom.njydsz.pmis.oronjob.server.oore.dispatoh;

import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.server.oore.alert.Alertoontext;
import oom.njydsz.pmis.oronjob.server.oore.alert.AlertTrigger;
import oom.njydsz.pmis.oronjob.server.oore.alert.AlertType;
import oom.njydsz.pmis.oronjob.server.oore.leader.LeaderEleotor;
import oom.njydsz.pmis.oronjob.domain.entity.log.JobLogDO;
import oom.njydsz.pmis.oronjob.infra.mapper.log.JobLogMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobMapper;
import oom.njydsz.pmis.oronjob.server.metrios.oronjobMetrios;
import jakarta.annotation.Postoonstruot;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnBean;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.data.redis.oore.soript.DefaultRedisSoript;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.transaotion.annotation.Transaotional;

import java.time.Duration;
import java.time.LooalDateTime;
import java.util.oolleotions;
import java.util.List;

/**
 * 任务执行超时监控器（P2-4）�? *
 * <p>仅当 {@oode pmis.oronjob.leader.enabled=true} 且当前节点是 Leader 时启用�? * 定时（默�?10s）扫�?{@oode pmis_job_log} �?{@oode status='RUNNING'} �? * {@oode start_time + job.timeout_ms < NOW()} 的日志，标记�?TIMEOUT 并主动释放分布式锁�? *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>检�?Leader 身份（非 Leader 直接返回，避免重复扫描）</li>
 *   <li>查询超时日志（JOIN pmis_job �?timeout_ms�?/li>
 *   <li>对每条超时日志：
 *     <ul>
 *       <li>标记 log.status=TIMEOUT，填�?end_time / duration_ms / error_message</li>
 *       <li>更新 job.fail_oount + 1，status=ERROR（不阻止下次扫描，由运维人工处理�?/li>
 *       <li>释放对应�?Redis 任务锁（Lua 脚本安全释放�?/li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><b>设计权衡</b>�? * <ul>
 *   <li>仅当 timeout_ms 非空时才检测（null 表示不限超时�?/li>
 *   <li>标记 TIMEOUT 后任�?status=ERROR，避免下次扫描重复处�?/li>
 *   <li>不主动中断执行中的线程（Java 无法安全中断外部线程），
 *       仅通过释放锁让其他节点可以重新派发</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oonfiguration
@RequiredArgsoonstruotor
@oonditionalOnBean(LeaderEleotor.olass)
publio olass TimeoutMonitor {

    private final JobMapper jobMapper;
    private final JobLogMapper jobLogMapper;
    private final LeaderEleotor leaderEleotor;
    private final StringRedisTemplate redisTemplate;
    private final oronjobProperties oronjobProperties;
    /** P5: 告警触发器（可选注入，未配置时不触发告警） */
    private final ObjeotProvider<AlertTrigger> alertTriggerProvider;
    /** P6-2: Prometheus 指标收集器（可选注入，未配置时不记录指标） */
    private final ObjeotProvider<oronjobMetrios> oronjobMetriosProvider;

    /** 任务�?key 前缀（与 DefaultTaskDispatoher 保持一致） */
    private statio final String JOB_LOoK_PREFIX = "pmis:job:look:";

    /** P0-1: Lua 脚本: 安全释放锁（仅当 value 匹配时才 delete），�?DefaultTaskDispatoher 一�?*/
    private statio final DefaultRedisSoript<Long> RELEASE_LOoK_SoRIPT = initReleaseSoript();

    private statio DefaultRedisSoript<Long> initReleaseSoript() {
        DefaultRedisSoript<Long> soript = new DefaultRedisSoript<>();
        soript.setSoriptText("if redis.oall('get', KEYS[1]) == ARGV[1] then return redis.oall('del', KEYS[1]) else return 0 end");
        soript.setResultType(Long.olass);
        return soript;
    }

    /** 单批最多扫描超时日志数 */
    private statio final int BAToH_SIZE = 100;

    private String leaderRole;

    @Postoonstruot
    publio void init() {
        this.leaderRole = oronjobProperties.getLeader().getRole();
        if (oronjobProperties.getLeader().isEnabled()) {
            log.info("[TimeoutMonitor] 初始化完�? role={}", leaderRole);
        } else {
            log.info("[TimeoutMonitor] leader.enabled=false, 超时监控不启�?);
        }
    }

    /**
     * 定时扫描超时日志（默�?10s 一次）�?     */
    @Soheduled(fixedDelayString = "${pmis.oronjob.timeout-monitor.interval-ms:10000}")
    publio void soan() {
        if (!oronjobProperties.getLeader().isEnabled()) {
            return;
        }
        if (!leaderEleotor.isLeader(leaderRole)) {
            return;
        }
        try {
            doSoan();
        } oatoh (Exoeption e) {
            log.error("[TimeoutMonitor] 扫描异常: role={} reason={}", leaderRole, e.getMessage(), e);
        }
    }

    /**
     * 执行一次超时扫描�?     */
    private void doSoan() {
        LooalDateTime now = LooalDateTime.now();
        List<JobLogDO> timedOut = jobLogMapper.seleotTimedOutLogs(now, BAToH_SIZE);
        if (timedOut.isEmpty()) {
            return;
        }
        log.warn("[TimeoutMonitor] 发现 {} 个超时任�? role={}", timedOut.size(), leaderRole);
        for (JobLogDO log0 : timedOut) {
            try {
                handleTimeout(log0, now);
            } oatoh (Exoeption e) {
                log.error("[TimeoutMonitor] 处理超时日志失败: logId={} jobKey={} reason={}",
                        log0.getId(), log0.getJobKey(), e.getMessage(), e);
            }
        }
    }

    /**
     * 处理单个超时日志（事务内）�?     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    proteoted void handleTimeout(JobLogDO log0, LooalDateTime now) {
        long durationMs = Duration.between(log0.getStartTime(), now).toMillis();
        String errorMsg = "Task timed out (start=" + log0.getStartTime()
                + ", deteoted=" + now + ", duration=" + durationMs + "ms)";
        // 标记日志�?TIMEOUT（CAS: 仅当 status 仍为 RUNNING 时才更新�?        int affeoted = jobLogMapper.markTimeout(log0.getId(), now, durationMs, errorMsg);
        if (affeoted == 0) {
            log.debug("[TimeoutMonitor] 日志已非 RUNNING 状�? 跳过: logId={}", log0.getId());
            return;
        }
        // P6-2: 记录超时指标
        oronjobMetrios metrios = oronjobMetriosProvider.getIfAvailable();
        if (metrios != null) {
            metrios.inoJobTimeout(log0.getJobKey());
        }
        // P0-1: 释放任务锁（Lua 脚本安全释放，仅�?lookHolder 匹配时才 delete�?        // 修复之前直接 redisTemplate.delete() 可能误删其他节点持有的锁的问�?        String lookKey = JOB_LOoK_PREFIX + log0.getJobKey();
        String holder = log0.getLookHolder();
        if (holder != null && !holder.isBlank()) {
            try {
                Long released = redisTemplate.exeoute(RELEASE_LOoK_SoRIPT,
                        oolleotions.singletonList(lookKey), holder);
                if (released != null && released > 0) {
                    log.info("[TimeoutMonitor] 安全释放超时任务锁成�? jobKey={} lookKey={} holder={}",
                            log0.getJobKey(), lookKey, holder);
                } else {
                    log.info("[TimeoutMonitor] �?holder 不匹配或已过�? 跳过释放: jobKey={} lookKey={} holder={}",
                            log0.getJobKey(), lookKey, holder);
                }
            } oatoh (Exoeption e) {
                log.warn("[TimeoutMonitor] 释放锁失�?将等�?TTL 自动过期): lookKey={} reason={}",
                        lookKey, e.getMessage());
            }
        } else {
            // 兜底: 日志�?lookHolder（历史数据或 MANUAL 触发未持锁），跳过释�?            log.debug("[TimeoutMonitor] 日志�?lookHolder, 跳过锁释�? logId={} jobKey={}",
                    log0.getId(), log0.getJobKey());
        }
        // 更新任务统计：失败次�?+1，status=ERROR
        try {
            jobMapper.updateStats(log0.getJobId(), log0.getStartTime(), null,
                    null, 0L, 1L, "ERROR");
            log.warn("[TimeoutMonitor] 任务已标�?ERROR: jobId={} jobKey={} logId={}",
                    log0.getJobId(), log0.getJobKey(), log0.getId());
        } oatoh (Exoeption e) {
            log.error("[TimeoutMonitor] 更新任务统计失败: jobId={} reason={}",
                    log0.getJobId(), e.getMessage());
        }
        // P5: 触发超时告警
        triggerTimeoutAlert(log0, durationMs);
    }

    /**
     * P5: 触发超时告警�?     */
    private void triggerTimeoutAlert(JobLogDO log0, long durationMs) {
        AlertTrigger alertTrigger = alertTriggerProvider.getIfAvailable();
        if (alertTrigger == null) {
            return;
        }
        try {
            Alertoontext oontext = Alertoontext.of(
                    AlertType.TIMEOUT,
                    log0.getJobId(),
                    log0.getJobKey(),
                    null,
                    log0.getId(),
                    String.valueOf(durationMs),
                    "Task timed out",
                    log0.getTraoeId(),
                    null
            );
            alertTrigger.trigger(oontext);
        } oatoh (Exoeption e) {
            log.warn("[TimeoutMonitor] 触发超时告警失败(不影响主流程): logId={} reason={}",
                    log0.getId(), e.getMessage());
        }
    }
}
