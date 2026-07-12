package com.njydsz.pmis.cronjob.server.core.map;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.core.job.JobLogger;
import com.njydsz.pmis.common.core.job.JobLoggerHolder;
import com.njydsz.pmis.common.core.job.MapContext;
import com.njydsz.pmis.common.core.job.MapProcessor;
import com.njydsz.pmis.common.core.job.MapReduceProcessor;
import com.njydsz.pmis.common.core.job.MapTask;
import com.njydsz.pmis.common.core.job.ProcessResult;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.cronjob.server.config.CronjobProperties;
import com.njydsz.pmis.cronjob.server.core.dispatch.RemoteSubTaskRequest;
import com.njydsz.pmis.cronjob.server.core.dispatch.RemoteTaskClient;
import com.njydsz.pmis.cronjob.server.core.discovery.NodeDiscoveryStrategy;
import com.njydsz.pmis.cronjob.domain.entity.job.JobDO;
import com.njydsz.pmis.cronjob.domain.entity.log.JobLogDO;
import com.njydsz.pmis.cronjob.domain.entity.job.JobNodeDO;
import com.njydsz.pmis.cronjob.domain.entity.job.JobTaskDO;
import com.njydsz.pmis.cronjob.infra.mapper.job.JobTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MapReduce 任务执行器（P0-4, P0-1 分布式并行执行）。
 *
 * <p>负责执行 {@link MapProcessor} / {@link MapReduceProcessor} 类型的任务，
 * 支持动态产生子任务的分布式批处理：
 * <ol>
 *   <li>从 ApplicationContext 获取 {@link MapProcessor} Bean（按 job.handler 名称）</li>
 *   <li>创建 ROOT TaskDO 记录，构造 root {@link MapContext}（isRootTask=true）</li>
 *   <li>调用 {@link MapProcessor#process(MapContext)} 处理 root task</li>
 *   <li>读取 {@link MapContext#getSubTasks()}，为每个子任务创建 TaskDO 记录</li>
 *   <li><b>P0-1:</b> 将子任务分发到多个执行器节点并行执行（通过 RemoteTaskClient HTTP 派发）</li>
 *   <li>若 processor 是 {@link MapReduceProcessor} 且有子任务，调用 reduce 汇总</li>
 *   <li>更新 ROOT TaskDO 状态为最终结果，返回 {@link ProcessResult}</li>
 * </ol>
 *
 * <h3>分布式并行执行（P0-1）</h3>
 * <p>当 {@code pmis.cronjob.map-reduce.enabled=true} 时，子任务将被分发到多个在线节点并行执行：
 * <ul>
 *   <li>子任务按 round-robin 分配到在线节点列表</li>
 *   <li>本地节点执行子任务时直接调用 processor.process()</li>
 *   <li>远程节点通过 HTTP POST {@code /cronjob/internal/execute-sub-task} 派发</li>
 *   <li>使用 CompletableFuture + Semaphore 控制最大并行度</li>
 *   <li>远程派发失败时降级本地执行（可配置）</li>
 * </ul>
 *
 * <h3>容错策略</h3>
 * <ul>
 *   <li>root task 失败：不产生子任务，直接返回失败结果</li>
 *   <li>子任务失败：记录 FAILED 状态，继续执行其他子任务（默认非 fail-fast）</li>
 *   <li>reduce 失败：整体返回失败，但子任务结果已持久化</li>
 * </ul>
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
    private final CronjobProperties cronjobProperties;
    private final NodeDiscoveryStrategy nodeDiscoveryStrategy;
    private final RemoteTaskClient remoteTaskClient;

    /**
     * P0-1: 子任务并行执行线程池。
     *
     * <p>使用固定大小线程池控制并行度，避免子任务过多时创建过多线程。
     * 线程池大小由 {@code pmis.cronjob.map-reduce.max-parallel-sub-tasks} 控制。
     */
    private final ExecutorService subTaskExecutor = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2),
            r -> {
                Thread t = new Thread(r, "mapreduce-subtask");
                t.setDaemon(true);
                return t;
            });

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

        // 6. P0-1: 分布式并行执行子任务
        List<ProcessResult> subTaskResults;
        CronjobProperties.MapReduce mrConfig = cronjobProperties.getMapReduce();
        if (mrConfig.isEnabled()) {
            subTaskResults = executeSubTasksDistributed(job, processor, subTasks, jobId, logId, jobKey);
        } else {
            subTaskResults = executeSubTasksSequentially(processor, subTasks, jobId, logId, jobKey);
        }

        // 统计成功/失败数
        int successCount = 0;
        int failCount = 0;
        for (ProcessResult r : subTaskResults) {
            if (r.isSuccess()) {
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
     * P0-1: 分布式并行执行子任务。
     *
     * <p>将子任务分发到多个在线节点并行执行：
     * <ol>
     *   <li>获取在线节点列表</li>
     *   <li>为每个子任务创建 TaskDO 记录</li>
     *   <li>按 round-robin 分配到节点，本地节点直接执行，远程节点通过 HTTP 派发</li>
     *   <li>使用 CompletableFuture + Semaphore 控制最大并行度</li>
     *   <li>等待所有子任务完成，收集结果</li>
     * </ol>
     *
     * @param job       任务定义
     * @param processor MapProcessor（本地执行用）
     * @param subTasks  子任务列表
     * @param jobId     任务 ID
     * @param logId     日志 ID
     * @param jobKey    任务 KEY
     * @return 子任务结果列表（顺序与 subTasks 一致）
     */
    private List<ProcessResult> executeSubTasksDistributed(JobDO job, MapProcessor processor,
                                                            List<MapTask> subTasks,
                                                            String jobId, String logId, String jobKey) {
        // 获取在线节点列表
        List<JobNodeDO> onlineNodes = nodeDiscoveryStrategy.getOnlineNodes();
        String localNodeId = nodeDiscoveryStrategy.getLocalNodeId();

        if (onlineNodes.isEmpty()) {
            log.warn("[MapTaskExecutor] 无在线节点, 降级为本地顺序执行: key={} logId={}", jobKey, logId);
            return executeSubTasksSequentially(processor, subTasks, jobId, logId, jobKey);
        }

        log.info("[MapTaskExecutor] 分布式并行执行: key={} logId={} subTaskCount={} nodeCount={} localNodeId={}",
                jobKey, logId, subTasks.size(), onlineNodes.size(), localNodeId);

        int maxParallel = cronjobProperties.getMapReduce().getMaxParallelSubTasks();
        Semaphore semaphore = new Semaphore(maxParallel);
        String traceId = TraceIdUtil.get();

        // 为每个子任务创建 TaskDO 并提交并行执行
        List<CompletableFuture<ProcessResult>> futures = new ArrayList<>(subTasks.size());
        AtomicInteger nodeIndex = new AtomicInteger(0);

        for (MapTask subTask : subTasks) {
            // 创建 TaskDO
            JobTaskDO subTaskDO = createTaskDO(jobId, logId, jobKey, subTask.getTaskName(),
                    subTask.getTaskParams(), TASK_TYPE_SUB_TASK, STATUS_PENDING);
            jobTaskMapper.insert(subTaskDO);

            // 选择目标节点（round-robin）
            JobNodeDO targetNode = onlineNodes.get(
                    nodeIndex.getAndIncrement() % onlineNodes.size());
            boolean isLocal = targetNode.getNodeId().equals(localNodeId);

            CompletableFuture<ProcessResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    ProcessResult failResult = ProcessResult.failed("线程中断等待信号量");
                    updateTaskStatus(subTaskDO, failResult);
                    return failResult;
                }
                try {
                    ProcessResult result;
                    if (isLocal) {
                        // 本地执行
                        result = executeTaskRemotely(processor, subTask, subTaskDO,
                                jobId, logId, jobKey);
                    } else {
                        // 远程派发
                        result = dispatchSubTaskToNode(targetNode, job, subTask, subTaskDO, traceId);
                        // 远程失败时降级本地执行
                        if (!result.isSuccess() && cronjobProperties.getMapReduce().isFallbackToLocal()) {
                            log.warn("[MapTaskExecutor] 远程执行失败, 降级本地: key={} taskName={} nodeId={} error={}",
                                    jobKey, subTask.getTaskName(), targetNode.getNodeId(),
                                    result.getErrorMessage());
                            result = executeTaskRemotely(processor, subTask, subTaskDO,
                                    jobId, logId, jobKey);
                        }
                    }
                    return result;
                } finally {
                    semaphore.release();
                }
            }, subTaskExecutor);

            futures.add(future);
        }

        // 等待所有子任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 收集结果（保持顺序）
        List<ProcessResult> results = new ArrayList<>(futures.size());
        for (CompletableFuture<ProcessResult> f : futures) {
            try {
                results.add(f.get());
            } catch (Exception e) {
                log.error("[MapTaskExecutor] 获取子任务结果异常: key={} logId={} reason={}",
                        jobKey, logId, e.getMessage(), e);
                results.add(ProcessResult.failed("获取子任务结果异常: " + e.getMessage()));
            }
        }
        return results;
    }

    /**
     * 顺序执行子任务（向后兼容模式）。
     *
     * @param processor MapProcessor
     * @param subTasks  子任务列表
     * @param jobId     任务 ID
     * @param logId     日志 ID
     * @param jobKey    任务 KEY
     * @return 子任务结果列表
     */
    private List<ProcessResult> executeSubTasksSequentially(MapProcessor processor,
                                                             List<MapTask> subTasks,
                                                             String jobId, String logId, String jobKey) {
        List<ProcessResult> results = new ArrayList<>(subTasks.size());
        for (MapTask subTask : subTasks) {
            JobTaskDO subTaskDO = createTaskDO(jobId, logId, jobKey, subTask.getTaskName(),
                    subTask.getTaskParams(), TASK_TYPE_SUB_TASK, STATUS_PENDING);
            jobTaskMapper.insert(subTaskDO);

            MapContext subContext = new MapContext(jobId, logId, jobKey, subTask.getTaskName(),
                    subTask.getTaskParams(), false);
            ProcessResult subResult = executeTask(processor, subContext, subTaskDO, jobKey, logId);
            results.add(subResult);
        }
        return results;
    }

    /**
     * P0-1: 将子任务派发到远程节点执行。
     *
     * @param targetNode 目标节点
     * @param job        任务定义
     * @param subTask    子任务定义
     * @param subTaskDO  子任务 DO（用于状态更新）
     * @param traceId    链路追踪 ID
     * @return 处理结果
     */
    private ProcessResult dispatchSubTaskToNode(JobNodeDO targetNode, JobDO job,
                                                 MapTask subTask, JobTaskDO subTaskDO,
                                                 String traceId) {
        // 更新状态为 RUNNING
        jobTaskMapper.updateStatus(subTaskDO.getId(), STATUS_RUNNING, null, null, LocalDateTime.now());
        jobTaskMapper.updateExecNodeId(subTaskDO.getId(), targetNode.getNodeId(), LocalDateTime.now());
        subTaskDO.setExecNodeId(targetNode.getNodeId());

        RemoteSubTaskRequest request = new RemoteSubTaskRequest(
                job.getId(), subTaskDO.getLogId(), job.getJobKey(),
                job.getHandler(), subTask.getTaskName(), subTask.getTaskParams(), traceId);

        ProcessResult result;
        try {
            String responseJson = remoteTaskClient.dispatchSubTask(targetNode, request);
            if (responseJson == null) {
                result = ProcessResult.failed("远程派发失败: 响应为空");
            } else {
                // ProcessResult 使用 final 字段，手动解析避免反射问题
                JSONObject jsonObj = JSON.parseObject(responseJson);
                boolean success = jsonObj.getBooleanValue("success");
                String res = jsonObj.getString("result");
                String errMsg = jsonObj.getString("errorMessage");
                result = new ProcessResult(success, res, errMsg);
            }
        } catch (Exception e) {
            log.error("[MapTaskExecutor] 远程子任务派发异常: key={} taskName={} nodeId={} reason={}",
                    job.getJobKey(), subTask.getTaskName(), targetNode.getNodeId(), e.getMessage(), e);
            result = ProcessResult.failed("远程派发异常: " + e.getMessage());
        }

        // 更新 TaskDO 状态
        updateTaskStatus(subTaskDO, result);
        return result;
    }

    /**
     * P0-1: 本地执行子任务（带状态更新）。
     *
     * <p>用于分布式模式下本地节点执行子任务，复用 {@link #executeTask} 逻辑。
     *
     * @param processor MapProcessor
     * @param subTask   子任务定义
     * @param subTaskDO 子任务 DO
     * @param jobId     任务 ID
     * @param logId     日志 ID
     * @param jobKey    任务 KEY
     * @return 处理结果
     */
    private ProcessResult executeTaskRemotely(MapProcessor processor, MapTask subTask,
                                               JobTaskDO subTaskDO,
                                               String jobId, String logId, String jobKey) {
        MapContext subContext = new MapContext(jobId, logId, jobKey, subTask.getTaskName(),
                subTask.getTaskParams(), false);
        return executeTask(processor, subContext, subTaskDO, jobKey, logId);
    }

    /**
     * 更新 TaskDO 状态为最终结果。
     *
     * @param taskDO 子任务 DO
     * @param result 处理结果
     */
    private void updateTaskStatus(JobTaskDO taskDO, ProcessResult result) {
        LocalDateTime now = LocalDateTime.now();
        String status = result.isSuccess() ? STATUS_SUCCESS : STATUS_FAILED;
        jobTaskMapper.updateStatus(taskDO.getId(), status, result.getResult(),
                result.getErrorMessage(), now);
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
        JobLogger logger = JobLoggerHolder.get();
        if (logger == null) {
            return;
        }
        String taskType = context.isRoot() ? "ROOT" : "SUB_TASK";
        logger.info("[MapTask] 开始执行 {} 任务: taskName={}", taskType, context.getTaskName());
    }

    /**
     * 写入任务结束日志到 {@link JobLoggerHolder}。
     *
     * @param context 执行上下文
     * @param result  处理结果
     */
    private void logEndToJobLogger(MapContext context, ProcessResult result) {
        JobLogger logger = JobLoggerHolder.get();
        if (logger == null) {
            return;
        }
        String taskType = context.isRoot() ? "ROOT" : "SUB_TASK";
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
