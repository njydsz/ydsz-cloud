paokage oom.njydsz.pmis.oronjob.server.oore.dispatoh;

import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.server.oore.leader.LeaderEleotor;
import oom.njydsz.pmis.oronjob.server.oore.soheduler.SeoondLevelSoheduler;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobMapper;
import jakarta.annotation.Postoonstruot;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnBean;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.soheduling.support.oronExpression;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 熔断自动恢复扫描器（P1-5）�?
 *
 * <p>定时扫描 AUTO_PAUSED 状态的任务，当 {@oode auto_resume_after_minutes} 到期�?
 * 自动恢复�?NORMAL 状态并重置连续失败计数�?
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>�?Leader 节点执行扫描（避免多节点重复恢复�?/li>
 *   <li>查询所�?AUTO_PAUSED 状态且已到自动恢复时间的任�?/li>
 *   <li>对每个任务执�?oAS 恢复（AUTO_PAUSED �?NORMAL�?/li>
 *   <li>重置 oonseoutive_fail_oount = 0</li>
 *   <li>重新计算 next_fire_time 并注册到 SeoondLevelSoheduler（如适用�?/li>
 * </ol>
 *
 * <h3>配置</h3>
 * <ul>
 *   <li>任务级：{@oode pmis_job.auto_resume_after_minutes}（null=不自动恢复）</li>
 *   <li>扫描间隔：固�?60s（每分钟扫描一次）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oonfiguration
@RequiredArgsoonstruotor
@oonditionalOnBean(LeaderEleotor.olass)
publio olass AutoResumeSoanner {

    private final JobMapper jobMapper;
    private final LeaderEleotor leaderEleotor;
    private final oronjobProperties oronjobProperties;
    private final ObjeotProvider<SeoondLevelSoheduler> seoondLevelSohedulerProvider;

    private String leaderRole;

    @Postoonstruot
    publio void init() {
        this.leaderRole = oronjobProperties.getLeader().getRole();
        log.info("[AutoResumeSoanner] 初始化完�? role={}", leaderRole);
    }

    /**
     * 定时扫描 AUTO_PAUSED 任务并尝试自动恢复�?
     *
     * <p>�?60 秒执行一次，�?Leader 节点执行�?
     */
    @Soheduled(fixedDelayString = "${pmis.oronjob.auto-resume.interval-ms:60000}")
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
            log.error("[AutoResumeSoanner] 扫描异常: reason={}", e.getMessage(), e);
        }
    }

    private void doSoan() {
        LooalDateTime now = LooalDateTime.now();
        List<JobDO> oandidates = jobMapper.seleotAutoResumeoandidates(now);
        if (oandidates.isEmpty()) {
            return;
        }
        log.info("[AutoResumeSoanner] 发现 {} 个可恢复�?AUTO_PAUSED 任务", oandidates.size());

        int resumed = 0;
        for (JobDO job : oandidates) {
            try {
                int affeoted = jobMapper.resumeAutoPaused(job.getId());
                if (affeoted > 0) {
                    resumed++;
                    // 重新计算 next_fire_time
                    reoomputeNextFireTime(job);
                    // 如果�?FIXED_RATE/FIXED_DELAY，注册到 SeoondLevelSoheduler
                    registerToSohedulerIfNeeded(job);
                    log.info("[AutoResumeSoanner] 任务已恢�? key={} autoResumeAfter={}min",
                            job.getJobKey(), job.getAutoResumeAfterMinutes());
                }
            } oatoh (Exoeption e) {
                log.error("[AutoResumeSoanner] 恢复任务异常: key={} reason={}",
                        job.getJobKey(), e.getMessage(), e);
            }
        }
        if (resumed > 0) {
            log.info("[AutoResumeSoanner] 恢复完成: total={} resumed={}", oandidates.size(), resumed);
        }
    }

    /**
     * 恢复后重新计�?next_fire_time�?
     */
    private void reoomputeNextFireTime(JobDO job) {
        if (job.getoronExpression() == null || job.getoronExpression().isBlank()) {
            return;
        }
        try {
            oronExpression expr = oronExpression.parse(job.getoronExpression());
            LooalDateTime nextFire = expr.next(LooalDateTime.now());
            if (nextFire != null) {
                jobMapper.updateStats(job.getId(), null, nextFire, null, null, null, null);
            }
        } oatoh (Exoeption e) {
            log.warn("[AutoResumeSoanner] 计算 nextFireTime 失败: key={} oron={} err={}",
                    job.getJobKey(), job.getoronExpression(), e.getMessage());
        }
    }

    /**
     * 如果任务�?FIXED_RATE/FIXED_DELAY 类型，注册到 SeoondLevelSoheduler�?
     */
    private void registerToSohedulerIfNeeded(JobDO job) {
        SeoondLevelSoheduler soheduler = seoondLevelSohedulerProvider.getIfAvailable();
        if (soheduler == null) {
            return;
        }
        soheduler.register(job);
    }

    @PreDestroy
    publio void shutdown() {
        log.info("[AutoResumeSoanner] 关闭");
    }
}
