package com.njydsz.cronjob.server.core.dag;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.service.dag.JobDagService;

/**
 * P1-F7: DAG Cron 定时触发扫描器。
 *
 * <p>补齐 DAG 工作流的定时自动跑批能力：数据模型（trigger_type/cron_expression/next_fire_time）、
 * 索引（idx_dag_next_fire）与触发服务（{@link JobDagService#triggerDag}）此前均已就绪，
 * 唯独缺少"到点自动触发"的调度入口，本类补上最后一环。
 *
 * <h3>调度语义（与 {@code JobScanner} 保持一致）</h3>
 *
 * <ol>
 *   <li><b>Leader 独占</b>：仅 Leader 节点执行扫描，避免多节点重复触发
 *   <li><b>任期号 fencing</b>：扫描开始时捕获 Leader epoch，逐 DAG 触发前比对，
 *       防止 Redis 主从切换窗口内双 Leader 双写
 *   <li><b>CAS 推进</b>：触发前先 {@code UPDATE ... WHERE next_fire_time = old} 原子推进，
 *       推进失败说明已被其他路（如手动触发后的重算）抢占，本次放弃
 *   <li><b>先推进后触发</b>：推进成功后调用 {@code triggerDag}，即使触发失败也不会重复触发
 *       （极端场景可能漏一次，与任务 Misfire 语义一致，后续可扩展 DAG misfire 补偿）
 * </ol>
 *
 * <p>触发来源标记 {@code triggerBy=SCHEDULER}，与手动（MANUAL）/OpenAPI（API）区分。
 *
 * <p>扫描周期可通过 {@code ydsz.cronjob.dag-scan.interval-ms} 调整（默认 5000ms）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DagCronScheduler {

  /** 定时触发来源标识（写入 DAG 实例 trigger_by 字段） */
  public static final String TRIGGER_BY_SCHEDULER = "SCHEDULER";

  /** 单次扫描最大触发数（防止大批量 DAG 同时到期时单轮耗时过长） */
  private static final int MAX_TRIGGER_PER_SCAN = 50;

  /** 防止上一轮扫描未完成时重入 */
  private final AtomicBoolean scanning = new AtomicBoolean(false);

  private final LeaderElector leaderElector;
  private final CronjobProperties cronjobProperties;
  private final JobDagRepository jobDagRepository;
  private final JobDagService jobDagService;

  /**
   * 定时扫描到期的 CRON 触发 DAG 并触发执行。
   *
   * <p>使用 {@code fixedDelayString} 而非 fixedRate：上一轮耗时过长时自动跳过下一轮，
   * 避免任务堆积与重复触发。
   */
  @Scheduled(fixedDelayString = "${ydsz.cronjob.dag-scan.interval-ms:5000}")
  public void scan() {
    if (!cronjobProperties.getLeader().isEnabled()) {
      return;
    }
    String leaderRole = cronjobProperties.getLeader().getRole();
    if (!leaderElector.isLeader(leaderRole)) {
      return;
    }
    if (!scanning.compareAndSet(false, true)) {
      log.debug("[DagCronScheduler] 上次扫描尚未完成, 跳过本次执行");
      return;
    }
    try {
      doScan(leaderRole, leaderElector.getEpoch(leaderRole));
    } catch (Exception e) {
      log.error("[DagCronScheduler] 扫描异常: reason={}", e.getMessage(), e);
    } finally {
      scanning.set(false);
    }
  }

  /**
   * 执行一次 DAG 到期扫描。
   *
   * @param leaderRole Leader 角色名
   * @param scanEpoch 扫描开始时捕获的 Leader 任期号（fencing）
   */
  private void doScan(String leaderRole, long scanEpoch) {
    List<JobDagVO> cronDags = jobDagService.listCronEnabledDags();
    if (cronDags.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    int triggered = 0;
    for (JobDagVO dag : cronDags) {
      if (triggered >= MAX_TRIGGER_PER_SCAN) {
        log.warn("[DagCronScheduler] 单次扫描触发数达上限, 剩余 DAG 下轮处理: maxPerScan={}", MAX_TRIGGER_PER_SCAN);
        break;
      }
      if (dag.getNextFireTime() == null || dag.getNextFireTime().isAfter(now)) {
        continue;
      }
      // P1-F4: Leader 任期号 fencing —— Leader 已被接管则立即中止本轮
      if (scanEpoch >= 0 && leaderElector.getEpoch(leaderRole) != scanEpoch) {
        log.warn(
            "[DagCronScheduler] Leader 任期号已变更, 中止扫描(fencing): scanEpoch={} currentEpoch={}",
            scanEpoch,
            leaderElector.getEpoch(leaderRole));
        return;
      }
      triggerDagIfDue(dag, now);
      triggered++;
    }
  }

  /**
   * 单个 DAG 的"CAS 推进 + 触发"流程。
   *
   * <p>先推进 next_fire_time（CAS，失败说明已被其他路抢占），成功后再触发；
   * 触发失败（如并发实例数达上限）仅记录日志，不阻塞其他 DAG。
   *
   * @param dag 到期的 DAG 定义
   * @param now 当前时间
   */
  private void triggerDagIfDue(JobDagVO dag, LocalDateTime now) {
    LocalDateTime newNext = nextFireTime(dag.getCronExpression());
    int advanced = jobDagRepository.advanceNextFireTime(dag.getId(), dag.getNextFireTime(), newNext);
    if (advanced <= 0) {
      log.debug(
          "[DagCronScheduler] DAG next_fire_time 已被其他节点推进, 跳过: dagKey={}", dag.getDagKey());
      return;
    }
    try {
      String instanceId = jobDagService.triggerDag(dag.getDagKey(), TRIGGER_BY_SCHEDULER);
      log.info(
          "[DagCronScheduler] 定时触发 DAG 成功: dagKey={} instanceId={} nextFireTime={}",
          dag.getDagKey(),
          instanceId,
          newNext);
    } catch (Exception e) {
      // 触发失败（并发实例上限/状态变更等）：next_fire_time 已推进，本周期不再重试，下周期按新时间
      log.warn(
          "[DagCronScheduler] 定时触发 DAG 失败: dagKey={} reason={}",
          dag.getDagKey(),
          e.getMessage());
    }
  }

  /**
   * 计算 Cron 表达式的下次触发时间（与 JobDagServiceImpl 同源实现，保证语义一致）。
   *
   * @param cron Cron 表达式（可为 null）
   * @return 下次触发时间；表达式非法或计算失败时返回 null
   */
  private LocalDateTime nextFireTime(String cron) {
    if (cron == null || cron.isBlank()) {
      return null;
    }
    try {
      return CronExpression.parse(cron).next(LocalDateTime.now());
    } catch (Exception e) {
      log.warn("[DagCronScheduler] 计算 nextFireTime 失败: cron={} err={}", cron, e.getMessage());
      return null;
    }
  }
}
