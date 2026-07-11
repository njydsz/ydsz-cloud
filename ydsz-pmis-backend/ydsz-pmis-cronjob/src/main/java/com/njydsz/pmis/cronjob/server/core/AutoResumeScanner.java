package com.njydsz.pmis.cronjob.server.core.dispatch;

import com.njydsz.pmis.cronjob.web.config.CronjobProperties;
import com.njydsz.pmis.cronjob.server.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.server.core.scheduler.SecondLevelScheduler;
import com.njydsz.pmis.cronjob.domain.entity.job.JobDO;
import com.njydsz.pmis.cronjob.infra.mapper.job.JobMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 熔断自动恢复扫描器（P1-5）。
 *
 * <p>定时扫描 AUTO_PAUSED 状态的任务，当 {@code auto_resume_after_minutes} 到期时
 * 自动恢复为 NORMAL 状态并重置连续失败计数。
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>仅 Leader 节点执行扫描（避免多节点重复恢复）</li>
 *   <li>查询所有 AUTO_PAUSED 状态且已到自动恢复时间的任务</li>
 *   <li>对每个任务执行 CAS 恢复（AUTO_PAUSED → NORMAL）</li>
 *   <li>重置 consecutive_fail_count = 0</li>
 *   <li>重新计算 next_fire_time 并注册到 SecondLevelScheduler（如适用）</li>
 * </ol>
 *
 * <h3>配置</h3>
 * <ul>
 *   <li>任务级：{@code pmis_job.auto_resume_after_minutes}（null=不自动恢复）</li>
 *   <li>扫描间隔：固定 60s（每分钟扫描一次）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class AutoResumeScanner {

    private final JobMapper jobMapper;
    private final LeaderElector leaderElector;
    private final CronjobProperties cronjobProperties;
    private final ObjectProvider<SecondLevelScheduler> secondLevelSchedulerProvider;

    private String leaderRole;

    @PostConstruct
    public void init() {
        this.leaderRole = cronjobProperties.getLeader().getRole();
        log.info("[AutoResumeScanner] 初始化完成, role={}", leaderRole);
    }

    /**
     * 定时扫描 AUTO_PAUSED 任务并尝试自动恢复。
     *
     * <p>每 60 秒执行一次，仅 Leader 节点执行。
     */
    @Scheduled(fixedDelayString = "${pmis.cronjob.auto-resume.interval-ms:60000}")
    public void scan() {
        if (!cronjobProperties.getLeader().isEnabled()) {
            return;
        }
        if (!leaderElector.isLeader(leaderRole)) {
            return;
        }
        try {
            doScan();
        } catch (Exception e) {
            log.error("[AutoResumeScanner] 扫描异常: reason={}", e.getMessage(), e);
        }
    }

    private void doScan() {
        LocalDateTime now = LocalDateTime.now();
        List<JobDO> candidates = jobMapper.selectAutoResumeCandidates(now);
        if (candidates.isEmpty()) {
            return;
        }
        log.info("[AutoResumeScanner] 发现 {} 个可恢复的 AUTO_PAUSED 任务", candidates.size());

        int resumed = 0;
        for (JobDO job : candidates) {
            try {
                int affected = jobMapper.resumeAutoPaused(job.getId());
                if (affected > 0) {
                    resumed++;
                    // 重新计算 next_fire_time
                    recomputeNextFireTime(job);
                    // 如果是 FIXED_RATE/FIXED_DELAY，注册到 SecondLevelScheduler
                    registerToSchedulerIfNeeded(job);
                    log.info("[AutoResumeScanner] 任务已恢复: key={} autoResumeAfter={}min",
                            job.getJobKey(), job.getAutoResumeAfterMinutes());
                }
            } catch (Exception e) {
                log.error("[AutoResumeScanner] 恢复任务异常: key={} reason={}",
                        job.getJobKey(), e.getMessage(), e);
            }
        }
        if (resumed > 0) {
            log.info("[AutoResumeScanner] 恢复完成: total={} resumed={}", candidates.size(), resumed);
        }
    }

    /**
     * 恢复后重新计算 next_fire_time。
     */
    private void recomputeNextFireTime(JobDO job) {
        if (job.getCronExpression() == null || job.getCronExpression().isBlank()) {
            return;
        }
        try {
            CronExpression expr = CronExpression.parse(job.getCronExpression());
            LocalDateTime nextFire = expr.next(LocalDateTime.now());
            if (nextFire != null) {
                jobMapper.updateStats(job.getId(), null, nextFire, null, null, null, null);
            }
        } catch (Exception e) {
            log.warn("[AutoResumeScanner] 计算 nextFireTime 失败: key={} cron={} err={}",
                    job.getJobKey(), job.getCronExpression(), e.getMessage());
        }
    }

    /**
     * 如果任务是 FIXED_RATE/FIXED_DELAY 类型，注册到 SecondLevelScheduler。
     */
    private void registerToSchedulerIfNeeded(JobDO job) {
        SecondLevelScheduler scheduler = secondLevelSchedulerProvider.getIfAvailable();
        if (scheduler == null) {
            return;
        }
        scheduler.register(job);
    }

    @PreDestroy
    public void shutdown() {
        log.info("[AutoResumeScanner] 关闭");
    }
}
