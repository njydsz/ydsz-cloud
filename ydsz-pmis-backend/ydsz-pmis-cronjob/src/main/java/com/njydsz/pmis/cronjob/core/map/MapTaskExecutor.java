package com.njydsz.pmis.cronjob.core.map;

import com.njydsz.pmis.common.job.JobLoggerHolder;
import com.njydsz.pmis.common.job.MapContext;
import com.njydsz.pmis.common.job.MapProcessor;
import com.njydsz.pmis.common.job.MapReduceProcessor;
import com.njydsz.pmis.common.job.MapTask;
import com.njydsz.pmis.common.job.ProcessResult;
import com.njydsz.pmis.common.job.TaskResult;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.entity.JobTaskDO;
import com.njydsz.pmis.cronjob.mapper.JobTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MapReduce 任务执行器（P0-4）。
 *
 * <p>负责执行 {@link MapProcessor} / {@link MapReduceProcessor} 类型的任务，
 * 支持动态产生子任务的分布式批处理：
 * <ol>
 *   <li>从 ApplicationContext 获取 {@link MapProcessor} Bean（按 job.handler 名称）</li>
 *   <li>创建 ROOT TaskDO 记录，构造 root {@link MapContext}（isRootTask=true）</li>
 *   <li>调用 {@link MapProcessor#process(MapContext)} 处理 root task</li>
 *   <li>读取 {@link MapContext#getSubTasks()}，为每个子任务创建 TaskDO 记录</li>
 *   <li>顺序执行每个子任务（构造子 {@link MapContext}，isRootTask=false）</li>
 *   <li>若 processor 是 {@link MapReduceProcessor} 且有子任务，调用 reduce 汇总</li>
 *   <li>更新 ROOT TaskDO 状态为最终结果，返回 {@link ProcessResult}</li>
 * </ol>
 *
 * <h3>容错策略</h3>
 * <ul>
 *   <li>root task 失败：不产生子任务，直接返回失败结果</li>
 *   <li>子任务失败：记录 FAILED 状态，继续执行其他子任务（默认非 fail-fast）</li>
 *   <li>reduce 失败：整体返回失败，但子任务结果已持久化</li>
 * </ul>
 *
 * <p>对标 PowerJob 的 MapReduceProcessorDemo，本执行器在单节点顺序执行子任务；
 * 分布式并行执行留作后续扩展（通过 RemoteTaskClient 派发子任务到多节点）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MapTaskExecutor {

    /** root task 名称常量 */
    private static final String ROOT_TASK_NAME = "root";

    /** TaskDO 类型常量 */
    private static final String TASK_TYPE_ROOT = "ROOT";
    private static final String TASK_TYPE_SUB_TASK = "SUB_TASK";

    /** TaskDO 状态常量 */
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final JobTaskMapper jobTaskMapper;
    private final ApplicationContext applicationContext;

    /**
     * 执行 MapReduce 任务。
     *
     * <p>由 {@code DefaultTaskDispatcher} 在 jobType=MAP/MAP_REDUCE 时调用，
     * 此时已获取分布式锁并写入 JobLogDO（status=RUNNING），在线日志器已绑定到 ThreadLocal。
     *
     * @param job         任务定义
     * @param log0        执行日志（已插入 pmis_job_log，status=RUNNING）
     * @param triggerType 触发类型
     * @return 整体处理结果（含 reduce 结果或 root 结果）
     */
    public ProcessResult executeMapJob(JobDO job, JobLogDO log0, String triggerType) {
        String jobId = job.getId();
        String logId = log0.getId();
        String jobKey = job.getJobKey();
        log.info("[MapTaskExecutor] 开始执行 MapReduce 任务: key={} logId={} triggerType={}",
                jobKey, logId, triggerType);

        // 1. 从 ApplicationContext 获取 MapProcessor Bean
        MapProcessor processor;
        try {
            processor = applicationContext.getBean(job.getHandler(), MapProcessor.class);
        } catch (Exception e) {
            log.error("[MapTaskExecutor] 获取 MapProcessor Bean 失败: key={} handler={} reason={}",
                    jobKey, job.getHandler(), e.getMessage(), e);
            return ProcessResult.failed("获取 MapProcessor Bean 失败: " + e.getMessage());
        }

        // 2. 创建 ROOT TaskDO 记录，status=PENDING
        JobTaskDO rootTaskDO = createTaskDO(jobId, logId, jobKey, ROOT_TASK_NAME,
                job.getParamsJson(), TASK_TYPE_ROOT, STATUS_PENDING);
        jobTaskMapper.insert(rootTaskDO);

        // 3. 构造 root MapContext，调用 processor.process 处理 root task
        MapContext rootContext = new MapContext(jobId, logId, jobKey, ROOT_TASK_NAME,
                job.getParamsJson(), true);
        ProcessResult rootResult = executeTask(processor, rootContext, rootTaskDO, jobKey, logId);

        // 4. root task 失败时不产生子任务，直接返回
        if (!rootResult.isSuccess()) {
            log.warn("[MapTaskExecutor] root task 执行失败, 不产生子任务: key={} logId={} error={}",
                    jobKey, logId, rootResult.getErrorMessage());
            return rootResult;
        }

        // 5. 读取子任务列表，无子任务时直接返回 root 结果
        List<MapTask> subTasks = rootContext.getSubTasks();
        if (subTasks.isEmpty()) {
            log.info("[MapTaskExecutor] root task 未产生子任务, 直接返回: key={} logId={}", jobKey, logId);
            return rootResult;
        }

        log.info("[MapTaskExecutor] root task 产生 {} 个子任务: key={} logId={}",
                subTasks.size(), jobKey, logId);

        // 6. 为每个子任务创建 TaskDO 记录并顺序执行
        List<ProcessResult> subTaskResults = new ArrayList<>(subTasks.size());
        List<TaskResult> taskResults = new ArrayList<>(subTasks.size());
        int successCount = 0;
        int failCount = 0;
        for (int i = 0; i < subTasks.size(); i++) {
            MapTask subTask = subTasks.get(i);
            JobTaskDO subTaskDO = createTaskDO(jobId, logId, jobKey, subTask.getTaskName(),
                    subTask.getTaskParams(), TASK_TYPE_SUB_TASK, STATUS_PENDING);
            jobTaskMapper.insert(subTaskDO);

            MapContext subContext = new MapContext(jobId, logId, jobKey, subTask.getTaskName(),
                    subTask.getTaskParams(), false);
            ProcessResult subResult = executeTask(processor, subContext, subTaskDO, jobKey, logId);
            subTaskResults.add(subResult);
            taskResults.add(new TaskResult(subTask.getTaskName(), subResult));
            if (subResult.isSuccess()) {
                successCount++;
            } else {
                failCount++;
            }
        }

        log.info("[MapTaskExecutor] 子任务执行完成: key={} logId={} total={} success={} fail={}",
                jobKey, logId, subTasks.size(), successCount, failCount);

        // 7. 若 processor 是 MapReduceProcessor 且有子任务，调用 reduce 汇总
        if (processor instanceof MapReduceProcessor reduceProcessor) {
            try {
                ProcessResult reduceResult = reduceProcessor.reduce(rootContext, subTaskResults);
                log.info("[MapTaskExecutor] reduce 完成: key={} logId={} success={}",
                        jobKey, logId, reduceResult.isSuccess());
                return reduceResult;
            } catch (Exception e) {
                log.error("[MapTaskExecutor] reduce 执行失败: key={} logId={} reason={}",
                        jobKey, logId, e.getMessage(), e);
                return ProcessResult.failed("reduce 执行失败: " + e.getMessage());
            }
        }

        // 8. 非 MapReduceProcessor：root task 成功即整体成功
        return rootResult;
    }

    /**
     * 执行单个任务（root 或子任务），更新 TaskDO 状态。
     *
     * <p>执行流程：
     * <ol>
     *   <li>更新 TaskDO 状态为 RUNNING</li>
     *   <li>调用 {@link MapProcessor#process(MapContext)}</li>
     *   <li>更新 TaskDO 状态为 SUCCESS/FAILED（含 result/errorMessage）</li>
     *   <li>通过 {@link JobLoggerHolder} 写入在线日志</li>
     * </ol>
     *
     * <p>异常处理：捕获所有异常转为 {@link ProcessResult#failed}，不向上抛出，
     * 保证单个任务失败不影响其他任务执行。
     *
     * @param processor 处理器
     * @param context   执行上下文
     * @param taskDO    子任务记录（已插入，status=PENDING）
     * @param jobKey    任务 KEY（日志用）
     * @param logId     日志 ID（日志用）
     * @return 处理结果
     */
    private ProcessResult executeTask(MapProcessor processor, MapContext context,
                                       JobTaskDO taskDO, String jobKey, String logId) {
        LocalDateTime now = LocalDateTime.now();
        // 更新状态为 RUNNING
        jobTaskMapper.updateStatus(taskDO.getId(), STATUS_RUNNING, null, null, now);

        // 写入开始日志
        logStartToJobLogger(context);

        ProcessResult result;
        try {
            result = processor.process(context);
            if (result == null) {
                // 业务侧返回 null，视为成功但无结果
                result = ProcessResult.success();
            }
        } catch (Exception e) {
            log.error("[MapTaskExecutor] 任务执行异常: key={} logId={} taskName={} reason={}",
                    jobKey, logId, context.getTaskName(), e.getMessage(), e);
            result = ProcessResult.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        // 更新 TaskDO 状态为最终结果
        LocalDateTime endTime = LocalDateTime.now();
        String status = result.isSuccess() ? STATUS_SUCCESS : STATUS_FAILED;
        String resultJson = result.getResult();
        String errorMessage = result.getErrorMessage();
        jobTaskMapper.updateStatus(taskDO.getId(), status, resultJson, errorMessage, endTime);

        // 写入结束日志
        logEndToJobLogger(context, result);

        return result;
    }

    /**
     * 写入任务开始日志到 {@link JobLoggerHolder}（在线日志白屏化）。
     *
     * @param context 执行上下文
     */
    private void logStartToJobLogger(MapContext context) {
        com.njydsz.pmis.common.job.JobLogger logger = JobLoggerHolder.get();
        if (logger == null) {
            return;
        }
        String taskType = context.isRootTask() ? "ROOT" : "SUB_TASK";
        logger.info("[MapTask] 开始执行 {} 任务: taskName={}", taskType, context.getTaskName());
    }

    /**
     * 写入任务结束日志到 {@link JobLoggerHolder}。
     *
     * @param context 执行上下文
     * @param result  处理结果
     */
    private void logEndToJobLogger(MapContext context, ProcessResult result) {
        com.njydsz.pmis.common.job.JobLogger logger = JobLoggerHolder.get();
        if (logger == null) {
            return;
        }
        String taskType = context.isRootTask() ? "ROOT" : "SUB_TASK";
        if (result.isSuccess()) {
            logger.info("[MapTask] {} 任务执行成功: taskName={} result={}",
                    taskType, context.getTaskName(), result.getResult());
        } else {
            logger.error("[MapTask] {} 任务执行失败: taskName={} error={}",
                    taskType, context.getTaskName(), result.getErrorMessage());
        }
    }

    /**
     * 构造 TaskDO 实体（未持久化）。
     *
     * @param jobId      任务 ID
     * @param logId      日志 ID
     * @param jobKey     任务 KEY
     * @param taskName   子任务名称
     * @param taskParams 子任务参数
     * @param taskType   子任务类型（ROOT/SUB_TASK）
     * @param status     初始状态（PENDING）
     * @return TaskDO 实体
     */
    private JobTaskDO createTaskDO(String jobId, String logId, String jobKey, String taskName,
                                    String taskParams, String taskType, String status) {
        JobTaskDO taskDO = new JobTaskDO();
        taskDO.setJobId(jobId);
        taskDO.setLogId(logId);
        taskDO.setJobKey(jobKey);
        taskDO.setTaskName(taskName);
        taskDO.setTaskParams(taskParams);
        taskDO.setTaskType(taskType);
        taskDO.setStatus(status);
        taskDO.setCreatedAt(LocalDateTime.now());
        taskDO.setUpdatedAt(LocalDateTime.now());
        taskDO.setDeleted(0);
        return taskDO;
    }
}
