package com.njydsz.pmis.cronjob.core.dag;

import com.njydsz.pmis.cronjob.entity.job.JobDO;
import com.njydsz.pmis.cronjob.entity.job.JobRelationDO;
import com.njydsz.pmis.cronjob.mapper.job.JobMapper;
import com.njydsz.pmis.cronjob.mapper.job.JobRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DAG 依赖触发执行器（P4-3 DAG 工作流）。
 *
 * <p>监听 {@link TaskCompletedEvent}，根据前置任务执行结果和 {@link FailStrategy}
 * 决定是否触发后继任务。
 *
 * <h3>触发逻辑</h3>
 * <ul>
 *   <li>前置成功 → 触发所有后继（triggerType=DEPENDENT）</li>
 *   <li>前置失败 + FAIL_FAST → 不触发后继</li>
 *   <li>前置失败 + CONTINUE_ON_FAIL → 触发后继（适用于通知/清理类）</li>
 * </ul>
 *
 * <p>使用 {@code @Async} 异步执行，避免阻塞 Dispatcher 主流程；
 * 后继任务通过 {@code TaskDispatcher.dispatch} 派发，与正常调度路径一致。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DagExecutor {

    private final JobRelationMapper jobRelationMapper;
    private final JobMapper jobMapper;
    private final com.njydsz.pmis.cronjob.core.dispatch.TaskDispatcher taskDispatcher;

    /**
     * 监听任务完成事件，异步触发后继任务。
     *
     * @param event 任务完成事件
     */
    @Async
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        try {
            triggerDependents(event.jobId(), event.success());
        } catch (Exception e) {
            log.error("[DagExecutor] 触发后继任务异常: parentJobId={} reason={}",
                    event.jobId(), e.getMessage(), e);
        }
    }

    /**
     * 触发指定任务的后继依赖任务。
     *
     * @param parentJobId    前置任务 ID
     * @param parentSuccess  前置任务是否执行成功
     */
    void triggerDependents(String parentJobId, boolean parentSuccess) {
        List<JobRelationDO> relations = jobRelationMapper.selectByParentJobId(parentJobId);
        if (relations == null || relations.isEmpty()) {
            return;
        }
        log.info("[DagExecutor] 触发后继任务: parentJobId={} success={} depCount={}",
                parentJobId, parentSuccess, relations.size());
        for (JobRelationDO relation : relations) {
            try {
                triggerOneDependent(relation, parentJobId, parentSuccess);
            } catch (Exception e) {
                log.error("[DagExecutor] 触发后继任务失败: parent={} child={} reason={}",
                        parentJobId, relation.getChildJobId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 触发单个后继任务。
     */
    private void triggerOneDependent(JobRelationDO relation, String parentJobId, boolean parentSuccess) {
        FailStrategy strategy = FailStrategy.parse(relation.getFailStrategy());
        // 前置失败 + FAIL_FAST → 跳过
        if (!parentSuccess && strategy == FailStrategy.FAIL_FAST) {
            log.info("[DagExecutor] 前置失败 + FAIL_FAST, 跳过后继: parent={} child={}",
                    parentJobId, relation.getChildJobId());
            return;
        }
        // 查询后继任务定义
        JobDO childJob = jobMapper.selectById(relation.getChildJobId());
        if (childJob == null) {
            log.warn("[DagExecutor] 后继任务不存在: childJobId={}", relation.getChildJobId());
            return;
        }
        if (!"NORMAL".equals(childJob.getStatus())) {
            log.info("[DagExecutor] 后继任务非 NORMAL 状态, 跳过: childKey={} status={}",
                    childJob.getJobKey(), childJob.getStatus());
            return;
        }
        // 派发后继任务（triggerType=DEPENDENT, 抢锁）
        String logId = taskDispatcher.dispatch(childJob, null, "DEPENDENT");
        log.info("[DagExecutor] 后继任务派发: parent={} child={} childKey={} logId={} strategy={} parentSuccess={}",
                parentJobId, relation.getChildJobId(), childJob.getJobKey(),
                logId, strategy, parentSuccess);
    }
}
