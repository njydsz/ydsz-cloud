package com.njydsz.cronjob.server.core.dispatch;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.core.scheduler.SecondLevelScheduler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

/**
 * 熔断自动恢复扫描器（P1-5）。
 *
 * <p>定时扫描 AUTO_PAUSED 状态的任务，当 {@code auto_resume_after_minutes} 到期时 自动恢复为 NORMAL 状态并重置连续失败计数。
 *
 * <h3>工作流程</h3>
 *
 * <ol>
 *   <li>仅 Leader 节点执行扫描（避免多节点重复恢复）
 *   <li>查询所有 AUTO_PAUSED 状态且已到自动恢复时间的任务
 *   <li>对每个任务执行 CAS 恢复（AUTO_PAUSED → NORMAL）
 *   <li>重置 consecutive_fail_count = 0
 *   <li>重新计算 next_fire_time 并注册到 SecondLevelScheduler（如适用）
 * </ol>
 *
 * <h3>配置</h3>
 *
 * <ul>
 *   <li>任务级：{@code ydsz_job.auto_resume_after_minutes}（null=不自动恢复）
 *   <li>扫描间隔：固定 60s（每分钟扫描一次）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
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

  /**
   * 缓存 Leader 选举角色名，供后续每次扫描判定本节点是否为 Leader。
   *
   * <p>角色名在容器初始化时一次性读入并驻留字段，避免每分钟扫描都穿透配置对象； 代价是<b>不支持运行期动态改角色</b>，修改配置需重启才生效。
   *
   * <p>本方法只做赋值与日志，不触碰数据库，因此不会拖慢容器启动。
   */
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
  @DistributedScheduled(lockKey = "cronjob:auto-resume")
  @Scheduled(fixedDelayString = "${ydsz.cronjob.auto-resume.interval-ms:60000}")
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
    List<Job> candidates = jobMapper.selectAutoResumeCandidates(now);
    if (candidates.isEmpty()) {
      return;
    }
    log.info("[AutoResumeScanner] 发现 {} 个可恢复的 AUTO_PAUSED 任务", candidates.size());

    int resumed = 0;
    for (Job job : candidates) {
      try {
        int affected = jobMapper.resumeAutoPaused(job.getId());
        if (affected > 0) {
          resumed++;
          // 重新计算 next_fire_time
          recomputeNextFireTime(job);
          // 如果是 FIXED_RATE/FIXED_DELAY，注册到 SecondLevelScheduler
          registerToSchedulerIfNeeded(job);
          log.info(
              "[AutoResumeScanner] 任务已恢复: key={} autoResumeAfter={}min",
              job.getJobKey(),
              job.getAutoResumeAfterMinutes());
        }
      } catch (Exception e) {
        log.error(
            "[AutoResumeScanner] 恢复任务异常: key={} reason={}", job.getJobKey(), e.getMessage(), e);
      }
    }
    if (resumed > 0) {
      log.info("[AutoResumeScanner] 恢复完成: total={} resumed={}", candidates.size(), resumed);
    }
  }

  /** 恢复后重新计算 next_fire_time。 */
  private void recomputeNextFireTime(Job job) {
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
      log.warn(
          "[AutoResumeScanner] 计算 nextFireTime 失败: key={} cron={} err={}",
          job.getJobKey(),
          job.getCronExpression(),
          e.getMessage());
    }
  }

  /** 如果任务是 FIXED_RATE/FIXED_DELAY 类型，注册到 SecondLevelScheduler。 */
  private void registerToSchedulerIfNeeded(Job job) {
    SecondLevelScheduler scheduler = secondLevelSchedulerProvider.getIfAvailable();
    if (scheduler == null) {
      return;
    }
    scheduler.register(job);
  }

  /**
   * 容器销毁钩子，仅打印关闭日志。
   *
   * <p>本扫描器不持有自建线程池——调度由 Spring {@code @Scheduled} 线程池驱动， 由容器统一停机，因此无需在此释放资源。保留钩子是为了在日志中标记组件下线时刻，
   * 便于排查"停机瞬间是否还有任务被恢复"。
   *
   * <p><b>注意</b>：正在执行中的 {@code scan()} 不会被本方法打断， 依赖 Spring 调度器的优雅停机等待。
   */
  @PreDestroy
  public void shutdown() {
    log.info("[AutoResumeScanner] 关闭");
  }
}
