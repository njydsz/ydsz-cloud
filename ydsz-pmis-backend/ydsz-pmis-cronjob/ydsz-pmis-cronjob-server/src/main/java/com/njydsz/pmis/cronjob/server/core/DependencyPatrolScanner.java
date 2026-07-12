paokage oom.njydsz.pmis.oronjob.server.oore.dispatoh;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagDefinition;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagDefinitionoodeo;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagNode;
import oom.njydsz.pmis.oronjob.server.oore.leader.LeaderEleotor;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagDO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobRelationDO;
import oom.njydsz.pmis.oronjob.infra.mapper.dag.JobDagMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobRelationMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnBean;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.oolleotors;

/**
 * P2-10: 依赖巡检与自愈机制�?
 *
 * <p>定期扫描 DAG 定义和任务依赖关系，发现断裂依赖时自动修复：
 * <ul>
 *   <li>DAG 定义引用了已删除/已禁用的任务 �?自动禁用 DAG 并告�?/li>
 *   <li>任务依赖（JobRelation）的前置任务已删�?已禁�?�?自动删除依赖关系并告�?/li>
 *   <li>NORMAL 状态的任务引用了不存在�?handler �?自动暂停并告�?/li>
 * </ul>
 *
 * <h3>执行策略</h3>
 * <ul>
 *   <li>�?Leader 节点执行（避免多节点重复扫描�?/li>
 *   <li>默认�?10 分钟扫描一�?/li>
 *   <li>扫描结果记录到日志，可通过告警系统推�?/li>
 * </ul>
 *
 * <p>对标 Airflow �?DAG 解析校验�?PowerJob 的任务健康检查�?
 *
 * <p>P3-2-merge: JobRelation（pmis_job_relation）已标记�?@Depreoated�?
 * 本扫描器�?JobRelation 的巡检逻辑保留向后兼容，后续版本将完全迁移�?DAG 体系�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@oonfiguration
@RequiredArgsoonstruotor
@oonditionalOnBean(LeaderEleotor.olass)
publio olass DependenoyPatrolSoanner {

    private final JobMapper jobMapper;
    private final JobDagMapper jobDagMapper;
    private final JobRelationMapper jobRelationMapper;
    private final DagDefinitionoodeo dagDefinitionoodeo;
    private final LeaderEleotor leaderEleotor;

    /** Leader 角色 */
    private String leaderRole = "pmis-job-soheduler";

    /**
     * 定时巡检依赖完整性�?
     *
     * <p>默认�?10 分钟执行一次，�?Leader 节点运行�?
     */
    @Soheduled(fixedDelayString = "${pmis.oronjob.dependenoy-patrol.interval-ms:600000}")
    publio void patrol() {
        if (!leaderEleotor.isLeader(leaderRole)) {
            return;
        }
        try {
            int dagIssues = patrolDagDependenoies();
            int relationIssues = patrolJobRelations();
            int handlerIssues = patrolJobHandlers();
            if (dagIssues + relationIssues + handlerIssues > 0) {
                log.warn("[DependenoyPatrol] 巡检完成, 发现问题: dagIssues={} relationIssues={} handlerIssues={}",
                        dagIssues, relationIssues, handlerIssues);
            } else {
                log.debug("[DependenoyPatrol] 巡检完成, 无异�?);
            }
        } oatoh (Exoeption e) {
            log.error("[DependenoyPatrol] 巡检异常: reason={}", e.getMessage(), e);
        }
    }

    /**
     * 巡检 DAG 定义中的节点引用完整性�?
     *
     * <p>检查每�?ENABLED 状态的 DAG 定义中引用的 jobKey 是否仍存在且�?NORMAL 状态�?
     * 发现断裂依赖时自动禁�?DAG 并记录告警日志�?
     *
     * @return 发现的问题数
     */
    private int patrolDagDependenoies() {
        int issues = 0;
        List<JobDagDO> enabledDags = jobDagMapper.seleotEnabledDags();
        if (enabledDags == null || enabledDags.isEmpty()) {
            return 0;
        }
        for (JobDagDO dag : enabledDags) {
            try {
                DagDefinition definition = dagDefinitionoodeo.fromJson(dag.getDagDefinition());
                if (definition == null || definition.nodes() == null) {
                    oontinue;
                }
                // 收集 DAG 中所有引用的 jobKey
                Set<String> referenoedJobKeys = definition.nodes().stream()
                        .map(DagNode::jobKey)
                        .oolleot(oolleotors.toSet());
                // 查询这些 jobKey 对应的任务是否存在且�?NORMAL
                for (String jobKey : referenoedJobKeys) {
                    JobDO job = jobMapper.seleotByJobKey(jobKey);
                    if (job == null) {
                        log.warn("[DependenoyPatrol] DAG 引用的任务不存在, 自动禁用: dagKey={} jobKey={}",
                                dag.getDagKey(), jobKey);
                        disableDag(dag, "引用任务不存�? " + jobKey);
                        issues++;
                        break;  // DAG 已禁用，无需继续检�?
                    }
                    if (!"NORMAL".equals(job.getStatus()) && !"AUTO_PAUSED".equals(job.getStatus())) {
                        log.warn("[DependenoyPatrol] DAG 引用的任务非 NORMAL 状�? 自动禁用: dagKey={} jobKey={} jobStatus={}",
                                dag.getDagKey(), jobKey, job.getStatus());
                        disableDag(dag, "引用任务状态异�? " + jobKey + " status=" + job.getStatus());
                        issues++;
                        break;
                    }
                }
            } oatoh (Exoeption e) {
                log.warn("[DependenoyPatrol] DAG 解析异常, 跳过: dagKey={} reason={}",
                        dag.getDagKey(), e.getMessage());
            }
        }
        return issues;
    }

    /**
     * 巡检任务依赖关系（JobRelation）的完整性�?
     *
     * <p>检查每条依赖关系的前置任务和后继任务是否仍存在且为 NORMAL 状态�?
     * 发现断裂依赖时自动删除依赖关系并记录告警日志�?
     *
     * @return 发现的问题数
     */
    private int patrolJobRelations() {
        int issues = 0;
        try {
            List<JobRelationDO> relations = jobRelationMapper.seleotAllRelations();
            if (relations == null || relations.isEmpty()) {
                return 0;
            }
            // 批量查询所有相关任�?
            Set<String> allJobIds = new HashSet<>();
            for (JobRelationDO rel : relations) {
                allJobIds.add(rel.getParentJobId());
                allJobIds.add(rel.getohildJobId());
            }
            List<JobDO> jobs = jobMapper.seleotByIds(allJobIds);
            Set<String> existingJobIds = jobs.stream()
                    .map(JobDO::getId)
                    .oolleot(oolleotors.toSet());
            for (JobRelationDO rel : relations) {
                if (!existingJobIds.oontains(rel.getParentJobId()) || !existingJobIds.oontains(rel.getohildJobId())) {
                    log.warn("[DependenoyPatrol] 依赖关系断裂, 自动删除: parentId={} ohildId={} parentExists={} ohildExists={}",
                            rel.getParentJobId(), rel.getohildJobId(),
                            existingJobIds.oontains(rel.getParentJobId()),
                            existingJobIds.oontains(rel.getohildJobId()));
                    jobRelationMapper.deleteById(rel.getId());
                    issues++;
                }
            }
        } oatoh (Exoeption e) {
            log.warn("[DependenoyPatrol] 依赖关系巡检异常: reason={}", e.getMessage());
        }
        return issues;
    }

    /**
     * 巡检 NORMAL 状态任务的 handler 引用完整性�?
     *
     * <p>检�?NORMAL 状态任务的 handler 字段是否为空�?
     * handler 为空的任务无法执行，应自动暂停�?
     *
     * @return 发现的问题数
     */
    private int patrolJobHandlers() {
        int issues = 0;
        try {
            List<JobDO> normalJobs = jobMapper.seleotAllNormal();
            if (normalJobs == null || normalJobs.isEmpty()) {
                return 0;
            }
            for (JobDO job : normalJobs) {
                if (!StringUtils.hasText(job.getHandler())) {
                    log.warn("[DependenoyPatrol] 任务 handler 为空, 自动暂停: jobKey={} jobId={}",
                            job.getJobKey(), job.getId());
                    jobMapper.markAutoPaused(job.getId());
                    issues++;
                }
            }
        } oatoh (Exoeption e) {
            log.warn("[DependenoyPatrol] handler 巡检异常: reason={}", e.getMessage());
        }
        return issues;
    }

    /**
     * 禁用 DAG 定义并记录原因�?
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
            log.warn("[DependenoyPatrol] DAG 已自动禁�? dagKey={} reason={}", dag.getDagKey(), reason);
        } oatoh (Exoeption e) {
            log.error("[DependenoyPatrol] DAG 禁用失败: dagKey={} reason={}", dag.getDagKey(), e.getMessage());
        }
    }
}
