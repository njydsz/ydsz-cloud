package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务扫描器（P1-7 Leader 模式专用）。
 *
 * <p>仅当 {@code pmis.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。
 * 定时（默认 5s）扫描 {@code pmis_job} 表中 {@code next_fire_time <= NOW()} 的任务，
 * 通过 {@code SELECT ... FOR UPDATE SKIP LOCKED} 抢占式行锁获取待派发任务，
 * 然后调用 {@link TaskDispatcher#dispatch(JobDO, String, String)} 派发到执行节点。
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>检查 Leader 身份（非 Leader 节点直接返回，避免重复扫描）</li>
 *   <li>开启事务，调用 {@link JobMapper#selectDueJobs(LocalDateTime, int)} 抢占式扫描</li>
 *   <li>对每个任务 CAS 推进 {@code next_fire_time}（防止 Leader 切换时重复派发）</li>
 *   <li>提交事务后调用 {@link TaskDispatcher} 派发（避免长事务阻塞）</li>
 *   <li>派发结果（成功/失败/跳过）记录到日志</li>
 * </ol>
 *
 * <p><b>避免重复派发的设计</b>：
 * <ul>
 *   <li>DB 行锁：{@code FOR UPDATE SKIP LOCKED} 保证多个 Leader 候选节点互不冲突</li>
 *   <li>CAS 推进：{@code WHERE next_fire_time = #{oldNextFireTime}} 保证 Leader 切换时不重复</li>
 *   <li>Redis 任务锁：{@link TaskDispatcher} 内部的 {@code pmis:job:lock:*} 锁兜底</li>
 * </ul>
 *
 * <p><b>故障转移</b>：Leader 节点宕机后，lease 到期自动释放，其他节点竞选为新 Leader，
 * 新 Leader 扫描时会重新发现 {@code next_fire_time <= NOW()} 的任务并派发。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class JobScanner {

    private final JobMapper jobMapper;
    private final LeaderElector leaderElector;
    private final TaskDispatcher taskDispatcher;
    private final CronjobProperties cronjobProperties;

    /** 扫描执行中标志（避免上次扫描未完成时重叠触发） */
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    /** Leader 角色（从配置读取，便于多套调度集群隔离） */
    private String leaderRole;

    @PostConstruct
    public void init() {
        this.leaderRole = cronjobProperties.getLeader().getRole();
        if (cronjobProperties.getLeader().isEnabled()) {
            log.info("[JobScanner] 初始化完成, role={} scanInterval={}ms batchSize={}",
                    leaderRole, cronjobProperties.getScanner().getIntervalMs(),
                    cronjobProperties.getScanner().getBatchSize());
        } else {
            log.info("[JobScanner] leader.enabled=false, 扫描器不启用（Leaderless 模式）");
        }
    }

    /**
     * 定时扫描待触发任务。
     *
     * <p>使用 {@code fixedDelayString} 而非 {@code fixedRateString}，
     * 避免上次扫描耗时较长时任务堆积。
     */
    @Scheduled(fixedDelayString = "${pmis.cronjob.scanner.interval-ms:5000}")
    public void scan() {
        if (!cronjobProperties.getLeader().isEnabled()) {
            return;
        }
        if (!leaderElector.isLeader(leaderRole)) {
            return;
        }
        if (!scanning.compareAndSet(false, true)) {
            log.debug("[JobScanner] 上次扫描尚未完成, 跳过本次执行");
            return;
        }
        try {
            doScan();
        } catch (Exception e) {
            log.error("[JobScanner] 扫描异常: role={} reason={}", leaderRole, e.getMessage(), e);
        } finally {
            scanning.set(false);
        }
    }

    /**
     * 执行一次扫描（事务内抢占 + CAS 推进 + 事务外派发）。
     *
     * <p>P2-2: 在派发前先判定 Misfire：
     * <ul>
     *   <li>{@link MisfirePolicy#SKIP} 跳过本次错过的触发，仅推进 next_fire_time</li>
     *   <li>{@link MisfirePolicy#FIRE_NOW} 立即执行一次（默认）</li>
     *   <li>{@link MisfirePolicy#COALESCE} 执行一次，日志 triggerType 标记 MISFIRED</li>
     * </ul>
     */
    private void doScan() {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = cronjobProperties.getScanner().getBatchSize();
        List<JobDO> dueJobs = acquireDueJobs(now, batchSize);
        if (dueJobs.isEmpty()) {
            return;
        }
        log.info("[JobScanner] 扫描到 {} 个待触发任务: role={}", dueJobs.size(), leaderRole);
        for (JobDO job : dueJobs) {
            try {
                // P2-2: Misfire 判定
                MisfirePolicy policy = MisfirePolicy.parse(job.getMisfirePolicy());
                boolean misfired = isMisfired(job, now);
                if (misfired && policy == MisfirePolicy.SKIP) {
                    // 仅推进 next_fire_time，不派发
                    LocalDateTime newNext = nextFireTime(job.getCronExpression());
                    boolean advanced = advanceNextFireTime(job, job.getNextFireTime(), newNext, now);
                    log.info("[JobScanner] Misfire SKIP 跳过派发: key={} advanced={}",
                            job.getJobKey(), advanced);
                    continue;
                }
                // 计算新的 next_fire_time 并 CAS 推进
                LocalDateTime oldNext = job.getNextFireTime();
                LocalDateTime newNext = nextFireTime(job.getCronExpression());
                boolean advanced = advanceNextFireTime(job, oldNext, newNext, now);
                if (!advanced) {
                    log.debug("[JobScanner] 任务 next_fire_time 已被其他节点推进, 跳过: key={}",
                            job.getJobKey());
                    continue;
                }
                // P2-2: 选择 triggerType
                //  - 非 Misfire: TRIGGER_CRON
                //  - Misfire + FIRE_NOW: TRIGGER_CRON（保持默认行为）
                //  - Misfire + COALESCE: TRIGGER_MISFIRED（日志可识别）
                String triggerType = DefaultTaskDispatcher.TRIGGER_CRON;
                if (misfired && policy == MisfirePolicy.COALESCE) {
                    triggerType = DefaultTaskDispatcher.TRIGGER_MISFIRED;
                    log.info("[JobScanner] Misfire COALESCE 派发（日志标记 MISFIRED）: key={}",
                            job.getJobKey());
                } else if (misfired) {
                    log.info("[JobScanner] Misfire FIRE_NOW 立即派发: key={}", job.getJobKey());
                }
                String logId = taskDispatcher.dispatch(job, null, triggerType);
                if (logId == null) {
                    log.info("[JobScanner] 任务被其他实例持有锁, 跳过: key={}", job.getJobKey());
                } else {
                    log.info("[JobScanner] 任务派发成功: key={} logId={} triggerType={}",
                            job.getJobKey(), logId, triggerType);
                }
            } catch (Exception e) {
                log.error("[JobScanner] 任务派发失败: key={} reason={}",
                        job.getJobKey(), e.getMessage(), e);
            }
        }
    }

    /**
     * 判定任务是否 Misfire。
     *
     * <p>当 {@code next_fire_time} 早于 {@code NOW() - misfireGraceMinutes} 时视为 Misfire。
     *
     * @param job 任务定义
     * @param now 当前时间
     * @return true 视为 Misfire
     */
    private boolean isMisfired(JobDO job, LocalDateTime now) {
        if (job.getNextFireTime() == null) {
            return false;
        }
        Duration grace = Duration.ofMinutes(cronjobProperties.getScanner().getMisfireGraceMinutes());
        LocalDateTime threshold = now.minus(grace);
        return job.getNextFireTime().isBefore(threshold);
    }

    /**
     * 抢占式扫描待触发任务（事务内）。
     */
    @Transactional(readOnly = true)
    protected List<JobDO> acquireDueJobs(LocalDateTime now, int batchSize) {
        return jobMapper.selectDueJobs(now, batchSize);
    }

    /**
     * CAS 推进 next_fire_time（事务内，防止重复派发）。
     */
    @Transactional(rollbackFor = Exception.class)
    protected boolean advanceNextFireTime(JobDO job, LocalDateTime oldNext,
                                          LocalDateTime newNext, LocalDateTime lastFire) {
        if (oldNext == null) {
            log.warn("[JobScanner] next_fire_time 为 null, 跳过 CAS: key={}", job.getJobKey());
            return false;
        }
        int affected = jobMapper.advanceNextFireTime(job.getId(), oldNext, newNext, lastFire);
        return affected > 0;
    }

    /**
     * 计算下次触发时间（基于 CronExpression，Asia/Shanghai 时区）。
     */
    private LocalDateTime nextFireTime(String cron) {
        try {
            Assert.hasText(cron, "cron 表达式不能为空");
            CronExpression expr = CronExpression.parse(cron);
            return expr.next(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("[JobScanner] 计算 nextFireTime 失败: cron={} err={}", cron, e.getMessage());
            return null;
        }
    }

    /**
     * 优雅下线：无需特殊处理，{@link LeaderElector#release(String)} 会释放 Leader 锁。
     */
    @PreDestroy
    public void shutdown() {
        log.info("[JobScanner] 关闭: role={}", leaderRole);
    }

    /**
     * 暴露扫描中状态（仅供测试断言使用）。
     */
    boolean isScanning() {
        return scanning.get();
    }

    /**
     * 暴露 Leader 角色（仅供测试断言使用）。
     */
    String getLeaderRole() {
        return leaderRole;
    }

    /**
     * 计算任务 Misfire 宽容窗口（仅供测试断言使用）。
     */
    Duration getMisfireGrace() {
        return Duration.ofMinutes(cronjobProperties.getScanner().getMisfireGraceMinutes());
    }
}
