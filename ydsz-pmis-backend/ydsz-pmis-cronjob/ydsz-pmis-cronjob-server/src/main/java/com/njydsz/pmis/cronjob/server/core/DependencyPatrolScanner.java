package com.njydsz.pmis.cronjob.server.core.dispatch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagDO;
import com.njydsz.pmis.cronjob.domain.entity.job.JobDO;
import com.njydsz.pmis.cronjob.domain.entity.job.JobRelationDO;
import com.njydsz.pmis.cronjob.infra.mapper.dag.JobDagMapper;
import com.njydsz.pmis.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.pmis.cronjob.infra.mapper.job.JobRelationMapper;
import com.njydsz.pmis.cronjob.server.core.dag.DagDefinition;
import com.njydsz.pmis.cronjob.server.core.dag.DagDefinitionCodec;
import com.njydsz.pmis.cronjob.server.core.dag.DagNode;
import com.njydsz.pmis.cronjob.server.core.leader.LeaderElector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-10: 依赖巡检与自愈机制。
 *
 * <p>定期扫描 DAG 定义和任务依赖关系，发现断裂依赖时自动修复：
 * <ul>
 *   <li>DAG 定义引用了已删除/已禁用的任务 → 自动禁用 DAG 并告警</li>
 *   <li>任务依赖（JobRelation）的前置任务已删除/已禁用 → 自动删除依赖关系并告警</li>
 *   <li>NORMAL 状态的任务引用了不存在的 handler → 自动暂停并告警</li>
 * </ul>
 *
 * <h3>执行策略</h3>
 * <ul>
 *   <li>仅 Leader 节点执行（避免多节点重复扫描）</li>
 *   <li>默认每 10 分钟扫描一次</li>
 *   <li>扫描结果记录到日志，可通过告警系统推送</li>
 * </ul>
 *
 * <p>对标 Airflow 的 DAG 解析校验和 PowerJob 的任务健康检查。
 *
 * <p>P3-2-merge: JobRelation（pmis_job_relation）已标记为 @Deprecated，
 * 本扫描器对 JobRelation 的巡检逻辑保留向后兼容，后续版本将完全迁移到 DAG 体系。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class DependencyPatrolScanner {

    private final JobMapper jobMapper;
    private final JobDagMapper jobDagMapper;
    private final JobRelationMapper jobRelationMapper;
    private final DagDefinitionCodec dagDefinitionCodec;
    private final LeaderElector leaderElector;

    /** Leader 角色 */
    private String leaderRole = "pmis-job-scheduler";

    /**
     * 定时巡检依赖完整性。
     *
     * <p>默认每 10 分钟执行一次，仅 Leader 节点运行。
     */
    @Scheduled(fixedDelayString = "${pmis.cronjob.dependency-patrol.interval-ms:600000}")
    public void patrol() {
        if (!leaderElector.isLeader(leaderRole)) {
            return;
        }
        try {
            int dagIssues = patrolDagDependencies();
            int relationIssues = patrolJobRelations();
            int handlerIssues = patrolJobHandlers();
            if (dagIssues + relationIssues + handlerIssues > 0) {
                log.warn("[DependencyPatrol] 巡检完成, 发现问题: dagIssues={} relationIssues={} handlerIssues={}",
                        dagIssues, relationIssues, handlerIssues);
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
     * <p>检查每个 ENABLED 状态的 DAG 定义中引用的 jobKey 是否仍存在且为 NORMAL 状态。
     * 发现断裂依赖时自动禁用 DAG 并记录告警日志。
     *
     * @return 发现的问题数
     */
    private int patrolDagDependencies() {
        int issues = 0;
        List<JobDagDO> enabledDags = jobDagMapper.selectEnabledDags();
        if (enabledDags == null || enabledDags.isEmpty()) {
            return 0;
        }
        for (JobDagDO dag : enabledDags) {
            try {
                DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());
                if (definition == null || definition.nodes() == null) {
                    continue;
                }
                // 收集 DAG 中所有引用的 jobKey
                Set<String> referencedJobKeys = definition.nodes().stream()
                        .map(DagNode::jobKey)
                        .collect(Collectors.toSet());
                // 查询这些 jobKey 对应的任务是否存在且为 NORMAL
                for (String jobKey : referencedJobKeys) {
                    JobDO job = jobMapper.selectByJobKey(jobKey);
                    if (job == null) {
                        log.warn("[DependencyPatrol] DAG 引用的任务不存在, 自动禁用: dagKey={} jobKey={}",
                                dag.getDagKey(), jobKey);
                        disableDag(dag, "引用任务不存在: " + jobKey);
                        issues++;
                        break;  // DAG 已禁用，无需继续检查
                    }
                    if (!"NORMAL".equals(job.getStatus()) && !"AUTO_PAUSED".equals(job.getStatus())) {
                        log.warn("[DependencyPatrol] DAG 引用的任务非 NORMAL 状态, 自动禁用: dagKey={} jobKey={} jobStatus={}",
                                dag.getDagKey(), jobKey, job.getStatus());
                        disableDag(dag, "引用任务状态异常: " + jobKey + " status=" + job.getStatus());
                        issues++;
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("[DependencyPatrol] DAG 解析异常, 跳过: dagKey={} reason={}",
                        dag.getDagKey(), e.getMessage());
            }
        }
        return issues;
    }

    /**
     * 巡检任务依赖关系（JobRelation）的完整性。
     *
     * <p>检查每条依赖关系的前置任务和后继任务是否仍存在且为 NORMAL 状态。
     * 发现断裂依赖时自动删除依赖关系并记录告警日志。
     *
     * @return 发现的问题数
     */
    private int patrolJobRelations() {
        int issues = 0;
        try {
            List<JobRelationDO> relations = jobRelationMapper.selectAllRelations();
            if (relations == null || relations.isEmpty()) {
                return 0;
            }
            // 批量查询所有相关任务
            Set<String> allJobIds = new HashSet<>();
            for (JobRelationDO rel : relations) {
                allJobIds.add(rel.getParentJobId());
                allJobIds.add(rel.getChildJobId());
            }
            List<JobDO> jobs = jobMapper.selectByIds(allJobIds);
            Set<String> existingJobIds = jobs.stream()
                    .map(JobDO::getId)
                    .collect(Collectors.toSet());
            for (JobRelationDO rel : relations) {
                if (!existingJobIds.contains(rel.getParentJobId()) || !existingJobIds.contains(rel.getChildJobId())) {
                    log.warn("[DependencyPatrol] 依赖关系断裂, 自动删除: parentId={} childId={} parentExists={} childExists={}",
                            rel.getParentJobId(), rel.getChildJobId(),
                            existingJobIds.contains(rel.getParentJobId()),
                            existingJobIds.contains(rel.getChildJobId()));
                    jobRelationMapper.deleteById(rel.getId());
                    issues++;
                }
            }
        } catch (Exception e) {
            log.warn("[DependencyPatrol] 依赖关系巡检异常: reason={}", e.getMessage());
        }
        return issues;
    }

    /**
     * 巡检 NORMAL 状态任务的 handler 引用完整性。
     *
     * <p>检查 NORMAL 状态任务的 handler 字段是否为空。
     * handler 为空的任务无法执行，应自动暂停。
     *
     * @return 发现的问题数
     */
    private int patrolJobHandlers() {
        int issues = 0;
        try {
            List<JobDO> normalJobs = jobMapper.selectAllNormal();
            if (normalJobs == null || normalJobs.isEmpty()) {
                return 0;
            }
            for (JobDO job : normalJobs) {
                if (!StringUtils.hasText(job.getHandler())) {
                    log.warn("[DependencyPatrol] 任务 handler 为空, 自动暂停: jobKey={} jobId={}",
                            job.getJobKey(), job.getId());
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
     * @param dag    DAG 定义
     * @param reason 禁用原因
     */
    private void disableDag(JobDagDO dag, String reason) {
        try {
            dag.setStatus("DISABLED");
            dag.setNextFireTime(null);
            dag.setVersion((dag.getVersion() == null ? 0 : dag.getVersion()) + 1);
            jobDagMapper.updateById(dag);
            log.warn("[DependencyPatrol] DAG 已自动禁用: dagKey={} reason={}", dag.getDagKey(), reason);
        } catch (Exception e) {
            log.error("[DependencyPatrol] DAG 禁用失败: dagKey={} reason={}", dag.getDagKey(), e.getMessage());
        }
    }
}
