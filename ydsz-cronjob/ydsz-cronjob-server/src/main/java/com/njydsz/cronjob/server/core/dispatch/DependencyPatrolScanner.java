package com.njydsz.cronjob.server.core.dispatch;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.cronjob.domain.entity.dag.JobDag;
import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.infra.mapper.dag.JobDagMapper;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.server.core.dag.DagDefinition;
import com.njydsz.cronjob.server.core.dag.DagDefinitionCodec;
import com.njydsz.cronjob.server.core.dag.DagNode;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

/**
 * P2-10: 依赖巡检与自愈机制。
 *
 * <p>定期扫描 DAG 定义，发现断裂依赖时自动修复：
 *
 * <ul>
 *   <li>DAG 定义引用了已删除/已禁用的任务 → 自动禁用 DAG 并告警
 *   <li>NORMAL 状态的任务引用了不存在的 handler → 自动暂停并告警
 * </ul>
 *
 * <h3>执行策略</h3>
 *
 * <ul>
 *   <li>仅 Leader 节点执行（避免多节点重复扫描）
 *   <li>默认每 10 分钟扫描一次
 *   <li>扫描结果记录到日志，可通过告警系统推送
 * </ul>
 *
 * <p>对标 Airflow 的 DAG 解析校验和 PowerJob 的任务健康检查。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class DependencyPatrolScanner {

  private final JobMapper jobMapper;
  private final JobDagMapper jobDagMapper;
  private final DagDefinitionCodec dagDefinitionCodec;
  private final LeaderElector leaderElector;

  /** Leader 角色 */
  private String leaderRole = "ydsz-job-scheduler";

  /**
   * 定时巡检依赖完整性。
   *
   * <p>默认每 10 分钟执行一次，仅 Leader 节点运行。
   */
  @DistributedScheduled(lockKey = "cronjob:dependency-patrol")
  @Scheduled(fixedDelayString = "${ydsz.cronjob.dependency-patrol.interval-ms:600000}")
  public void patrol() {
    if (!leaderElector.isLeader(leaderRole)) {
      return;
    }
    try {
      int dagIssues = patrolDagDependencies();
      int handlerIssues = patrolJobHandlers();
      if (dagIssues + handlerIssues > 0) {
        log.warn(
            "[DependencyPatrol] 巡检完成, 发现问题: dagIssues={} handlerIssues={}",
            dagIssues,
            handlerIssues);
      } else {
        log.debug("[DependencyPatrol] 巡检完成, 无异常");
      }
    } catch (Exception e) {
      log.error("[DependencyPatrol] 巡检异常: reason={}", e.getMessage(), e);
    }
  }

  /**
   * 巡检 DAG 定义中的节点引用完整性。
   *
   * <p>检查每个 ENABLED 状态的 DAG 定义中引用的 jobKey 是否仍存在且为 NORMAL 状态。 发现断裂依赖时自动禁用 DAG 并记录告警日志。
   *
   * @return 发现的问题数
   */
  private int patrolDagDependencies() {
    int issues = 0;
    List<JobDag> enabledDags = jobDagMapper.selectEnabledDags();
    if (enabledDags == null || enabledDags.isEmpty()) {
      return 0;
    }
    for (JobDag dag : enabledDags) {
      try {
        DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());
        if (definition == null || definition.nodes() == null) {
          continue;
        }
        // 收集 DAG 中所有引用的 jobKey
        Set<String> referencedJobKeys =
            definition.nodes().stream().map(DagNode::jobKey).collect(Collectors.toSet());
        // 查询这些 jobKey 对应的任务是否存在且为 NORMAL
        for (String jobKey : referencedJobKeys) {
          Job job = jobMapper.selectByJobKey(jobKey);
          if (job == null) {
            log.warn(
                "[DependencyPatrol] DAG 引用的任务不存在, 自动禁用: dagKey={} jobKey={}",
                dag.getDagKey(),
                jobKey);
            disableDag(dag, "引用任务不存在: " + jobKey);
            issues++;
            break; // DAG 已禁用，无需继续检查
          }
          if (!"NORMAL".equals(job.getStatus()) && !"AUTO_PAUSED".equals(job.getStatus())) {
            log.warn(
                "[DependencyPatrol] DAG 引用的任务非 NORMAL 状态, 自动禁用: dagKey={} jobKey={} jobStatus={}",
                dag.getDagKey(),
                jobKey,
                job.getStatus());
            disableDag(dag, "引用任务状态异常: " + jobKey + " status=" + job.getStatus());
            issues++;
            break;
          }
        }
      } catch (Exception e) {
        log.warn(
            "[DependencyPatrol] DAG 解析异常, 跳过: dagKey={} reason={}",
            dag.getDagKey(),
            e.getMessage());
      }
    }
    return issues;
  }

  /**
   * 巡检 NORMAL 状态任务的 handler 引用完整性。
   *
   * <p>检查 NORMAL 状态任务的 handler 字段是否为空。 handler 为空的任务无法执行，应自动暂停。
   *
   * @return 发现的问题数
   */
  private int patrolJobHandlers() {
    int issues = 0;
    try {
      List<Job> normalJobs = jobMapper.selectAllNormal();
      if (normalJobs == null || normalJobs.isEmpty()) {
        return 0;
      }
      for (Job job : normalJobs) {
        if (!StringUtils.hasText(job.getHandler())) {
          log.warn(
              "[DependencyPatrol] 任务 handler 为空, 自动暂停: jobKey={} jobId={}",
              job.getJobKey(),
              job.getId());
          jobMapper.markAutoPaused(job.getId());
          issues++;
        }
      }
    } catch (Exception e) {
      log.warn("[DependencyPatrol] handler 巡检异常: reason={}", e.getMessage());
    }
    return issues;
  }

  /**
   * 禁用 DAG 定义并记录原因。
   *
   * @param dag DAG 定义
   * @param reason 禁用原因
   */
  private void disableDag(JobDag dag, String reason) {
    try {
      dag.setStatus("DISABLED");
      dag.setNextFireTime(null);
      dag.setVersion((dag.getVersion() == null ? 0 : dag.getVersion()) + 1);
      jobDagMapper.updateById(dag);
      log.warn("[DependencyPatrol] DAG 已自动禁用: dagKey={} reason={}", dag.getDagKey(), reason);
    } catch (Exception e) {
      log.error(
          "[DependencyPatrol] DAG 禁用失败: dagKey={} reason={}", dag.getDagKey(), e.getMessage());
    }
  }
}
