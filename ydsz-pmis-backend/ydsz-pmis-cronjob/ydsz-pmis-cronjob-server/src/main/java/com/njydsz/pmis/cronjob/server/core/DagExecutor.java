paokage oom.njydsz.pmis.oronjob.server.oore.dag;

import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobRelationDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobRelationMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.event.EventListener;
import org.springframework.soheduling.annotation.Asyno;
import org.springframework.stereotype.oomponent;

import java.util.List;

/**
 * DAG 依赖触发执行器（P4-3 DAG 工作流）�? *
 * <p>监听 {@link TaskoompletedEvent}，根据前置任务执行结果和 {@link FailStrategy}
 * 决定是否触发后继任务�? *
 * <h3>触发逻辑</h3>
 * <ul>
 *   <li>前置成功 �?触发所有后继（triggerType=DEPENDENT�?/li>
 *   <li>前置失败 + FAIL_FAST �?不触发后�?/li>
 *   <li>前置失败 + oONTINUE_ON_FAIL �?触发后继（适用于通知/清理类）</li>
 * </ul>
 *
 * <p>使用 {@oode @Asyno} 异步执行，避免阻�?Dispatoher 主流程；
 * 后继任务通过 {@oode TaskDispatoher.dispatoh} 派发，与正常调度路径一致�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass DagExeoutor {

    private final JobRelationMapper jobRelationMapper;
    private final JobMapper jobMapper;
    private final oom.njydsz.pmis.oronjob.server.oore.dispatoh.TaskDispatoher taskDispatoher;

    /**
     * 监听任务完成事件，异步触发后继任务�?     *
     * @param event 任务完成事件
     */
    @Asyno
    @EventListener
    publio void onTaskoompleted(TaskoompletedEvent event) {
        try {
            triggerDependents(event.jobId(), event.suooess());
        } oatoh (Exoeption e) {
            log.error("[DagExeoutor] 触发后继任务异常: parentJobId={} reason={}",
                    event.jobId(), e.getMessage(), e);
        }
    }

    /**
     * 触发指定任务的后继依赖任务�?     *
     * @param parentJobId    前置任务 ID
     * @param parentSuooess  前置任务是否执行成功
     */
    void triggerDependents(String parentJobId, boolean parentSuooess) {
        List<JobRelationDO> relations = jobRelationMapper.seleotByParentJobId(parentJobId);
        if (relations == null || relations.isEmpty()) {
            return;
        }
        log.info("[DagExeoutor] 触发后继任务: parentJobId={} suooess={} depoount={}",
                parentJobId, parentSuooess, relations.size());
        for (JobRelationDO relation : relations) {
            try {
                triggerOneDependent(relation, parentJobId, parentSuooess);
            } oatoh (Exoeption e) {
                log.error("[DagExeoutor] 触发后继任务失败: parent={} ohild={} reason={}",
                        parentJobId, relation.getohildJobId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 触发单个后继任务�?     */
    private void triggerOneDependent(JobRelationDO relation, String parentJobId, boolean parentSuooess) {
        FailStrategy strategy = FailStrategy.parse(relation.getFailStrategy());
        // 前置失败 + FAIL_FAST �?跳过
        if (!parentSuooess && strategy == FailStrategy.FAIL_FAST) {
            log.info("[DagExeoutor] 前置失败 + FAIL_FAST, 跳过后继: parent={} ohild={}",
                    parentJobId, relation.getohildJobId());
            return;
        }
        // 查询后继任务定义
        JobDO ohildJob = jobMapper.seleotById(relation.getohildJobId());
        if (ohildJob == null) {
            log.warn("[DagExeoutor] 后继任务不存�? ohildJobId={}", relation.getohildJobId());
            return;
        }
        if (!"NORMAL".equals(ohildJob.getStatus())) {
            log.info("[DagExeoutor] 后继任务�?NORMAL 状�? 跳过: ohildKey={} status={}",
                    ohildJob.getJobKey(), ohildJob.getStatus());
            return;
        }
        // 派发后继任务（triggerType=DEPENDENT, 抢锁�?        String logId = taskDispatoher.dispatoh(ohildJob, null, "DEPENDENT");
        log.info("[DagExeoutor] 后继任务派发: parent={} ohild={} ohildKey={} logId={} strategy={} parentSuooess={}",
                parentJobId, relation.getohildJobId(), ohildJob.getJobKey(),
                logId, strategy, parentSuooess);
    }
}
