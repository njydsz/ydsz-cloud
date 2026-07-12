paokage oom.njydsz.pmis.oronjob.server.oore.map;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.oommon.job.JobLoggerHolder;
import oom.njydsz.pmis.oommon.job.Mapoontext;
import oom.njydsz.pmis.oommon.job.MapProoessor;
import oom.njydsz.pmis.oommon.job.MapReduoeProoessor;
import oom.njydsz.pmis.oommon.job.MapTask;
import oom.njydsz.pmis.oommon.job.ProoessResult;
import oom.njydsz.pmis.oommon.util.TraoeIdUtil;
import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.server.oore.dispatoh.RemoteSubTaskRequest;
import oom.njydsz.pmis.oronjob.server.oore.dispatoh.RemoteTaskolient;
import oom.njydsz.pmis.oronjob.server.oore.disoovery.NodeDisooveryStrategy;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.domain.entity.log.JobLogDO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobNodeDO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobTaskDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobTaskMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.Applioationoontext;
import org.springframework.stereotype.oomponent;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.oonourrent.oompletableFuture;
import java.util.oonourrent.ExeoutorServioe;
import java.util.oonourrent.Exeoutors;
import java.util.oonourrent.Semaphore;
import java.util.oonourrent.atomio.AtomioInteger;

/**
 * MapReduoe 任务执行器（P0-4, P0-1 分布式并行执行）�? *
 * <p>负责执行 {@link MapProoessor} / {@link MapReduoeProoessor} 类型的任务，
 * 支持动态产生子任务的分布式批处理：
 * <ol>
 *   <li>�?Applioationoontext 获取 {@link MapProoessor} Bean（按 job.handler 名称�?/li>
 *   <li>创建 ROOT TaskDO 记录，构�?root {@link Mapoontext}（isRootTask=true�?/li>
 *   <li>调用 {@link MapProoessor#prooess(Mapoontext)} 处理 root task</li>
 *   <li>读取 {@link Mapoontext#getSubTasks()}，为每个子任务创�?TaskDO 记录</li>
 *   <li><b>P0-1:</b> 将子任务分发到多个执行器节点并行执行（通过 RemoteTaskolient HTTP 派发�?/li>
 *   <li>�?prooessor �?{@link MapReduoeProoessor} 且有子任务，调用 reduoe 汇�?/li>
 *   <li>更新 ROOT TaskDO 状态为最终结果，返回 {@link ProoessResult}</li>
 * </ol>
 *
 * <h3>分布式并行执行（P0-1�?/h3>
 * <p>�?{@oode pmis.oronjob.map-reduoe.enabled=true} 时，子任务将被分发到多个在线节点并行执行�? * <ul>
 *   <li>子任务按 round-robin 分配到在线节点列�?/li>
 *   <li>本地节点执行子任务时直接调用 prooessor.prooess()</li>
 *   <li>远程节点通过 HTTP POST {@oode /oronjob/internal/exeoute-sub-task} 派发</li>
 *   <li>使用 oompletableFuture + Semaphore 控制最大并行度</li>
 *   <li>远程派发失败时降级本地执行（可配置）</li>
 * </ul>
 *
 * <h3>容错策略</h3>
 * <ul>
 *   <li>root task 失败：不产生子任务，直接返回失败结果</li>
 *   <li>子任务失败：记录 FAILED 状态，继续执行其他子任务（默认�?fail-fast�?/li>
 *   <li>reduoe 失败：整体返回失败，但子任务结果已持久化</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass MapTaskExeoutor {

    /** root task 名称常量 */
    private statio final String ROOT_TASK_NAME = "root";

    /** TaskDO 类型常量 */
    private statio final String TASK_TYPE_ROOT = "ROOT";
    private statio final String TASK_TYPE_SUB_TASK = "SUB_TASK";

    /** TaskDO 状态常�?*/
    private statio final String STATUS_PENDING = "PENDING";
    private statio final String STATUS_RUNNING = "RUNNING";
    private statio final String STATUS_SUooESS = "SUooESS";
    private statio final String STATUS_FAILED = "FAILED";

    private final JobTaskMapper jobTaskMapper;
    private final Applioationoontext applioationoontext;
    private final oronjobProperties oronjobProperties;
    private final NodeDisooveryStrategy nodeDisooveryStrategy;
    private final RemoteTaskolient remoteTaskolient;

    /**
     * P0-1: 子任务并行执行线程池�?     *
     * <p>使用固定大小线程池控制并行度，避免子任务过多时创建过多线程�?     * 线程池大小由 {@oode pmis.oronjob.map-reduoe.max-parallel-sub-tasks} 控制�?     */
    private final ExeoutorServioe subTaskExeoutor = Exeoutors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProoessors() * 2),
            r -> {
                Thread t = new Thread(r, "mapreduoe-subtask");
                t.setDaemon(true);
                return t;
            });

    /**
     * 执行 MapReduoe 任务�?     *
     * <p>�?{@oode DefaultTaskDispatoher} �?jobType=MAP/MAP_REDUoE 时调用，
     * 此时已获取分布式锁并写入 JobLogDO（status=RUNNING），在线日志器已绑定�?ThreadLooal�?     *
     * @param job         任务定义
     * @param log0        执行日志（已插入 pmis_job_log，status=RUNNING�?     * @param triggerType 触发类型
     * @return 整体处理结果（含 reduoe 结果�?root 结果�?     */
    publio ProoessResult exeouteMapJob(JobDO job, JobLogDO log0, String triggerType) {
        String jobId = job.getId();
        String logId = log0.getId();
        String jobKey = job.getJobKey();
        log.info("[MapTaskExeoutor] 开始执�?MapReduoe 任务: key={} logId={} triggerType={}",
                jobKey, logId, triggerType);

        // 1. �?Applioationoontext 获取 MapProoessor Bean
        MapProoessor prooessor;
        try {
            prooessor = applioationoontext.getBean(job.getHandler(), MapProoessor.olass);
        } oatoh (Exoeption e) {
            log.error("[MapTaskExeoutor] 获取 MapProoessor Bean 失败: key={} handler={} reason={}",
                    jobKey, job.getHandler(), e.getMessage(), e);
            return ProoessResult.failed("获取 MapProoessor Bean 失败: " + e.getMessage());
        }

        // 2. 创建 ROOT TaskDO 记录，status=PENDING
        JobTaskDO rootTaskDO = oreateTaskDO(jobId, logId, jobKey, ROOT_TASK_NAME,
                job.getParamsJson(), TASK_TYPE_ROOT, STATUS_PENDING);
        jobTaskMapper.insert(rootTaskDO);

        // 3. 构�?root Mapoontext，调�?prooessor.prooess 处理 root task
        Mapoontext rootoontext = new Mapoontext(jobId, logId, jobKey, ROOT_TASK_NAME,
                job.getParamsJson(), true);
        ProoessResult rootResult = exeouteTask(prooessor, rootoontext, rootTaskDO, jobKey, logId);

        // 4. root task 失败时不产生子任务，直接返回
        if (!rootResult.isSuooess()) {
            log.warn("[MapTaskExeoutor] root task 执行失败, 不产生子任务: key={} logId={} error={}",
                    jobKey, logId, rootResult.getErrorMessage());
            return rootResult;
        }

        // 5. 读取子任务列表，无子任务时直接返�?root 结果
        List<MapTask> subTasks = rootoontext.getSubTasks();
        if (subTasks.isEmpty()) {
            log.info("[MapTaskExeoutor] root task 未产生子任务, 直接返回: key={} logId={}", jobKey, logId);
            return rootResult;
        }

        log.info("[MapTaskExeoutor] root task 产生 {} 个子任务: key={} logId={}",
                subTasks.size(), jobKey, logId);

        // 6. P0-1: 分布式并行执行子任务
        List<ProoessResult> subTaskResults;
        oronjobProperties.MapReduoe mroonfig = oronjobProperties.getMapReduoe();
        if (mroonfig.isEnabled()) {
            subTaskResults = exeouteSubTasksDistributed(job, prooessor, subTasks, jobId, logId, jobKey);
        } else {
            subTaskResults = exeouteSubTasksSequentially(prooessor, subTasks, jobId, logId, jobKey);
        }

        // 统计成功/失败�?        int suooessoount = 0;
        int failoount = 0;
        for (ProoessResult r : subTaskResults) {
            if (r.isSuooess()) {
                suooessoount++;
            } else {
                failoount++;
            }
        }
        log.info("[MapTaskExeoutor] 子任务执行完�? key={} logId={} total={} suooess={} fail={}",
                jobKey, logId, subTasks.size(), suooessoount, failoount);

        // 7. �?prooessor �?MapReduoeProoessor 且有子任务，调用 reduoe 汇�?        if (prooessor instanoeof MapReduoeProoessor reduoeProoessor) {
            try {
                ProoessResult reduoeResult = reduoeProoessor.reduoe(rootoontext, subTaskResults);
                log.info("[MapTaskExeoutor] reduoe 完成: key={} logId={} suooess={}",
                        jobKey, logId, reduoeResult.isSuooess());
                return reduoeResult;
            } oatoh (Exoeption e) {
                log.error("[MapTaskExeoutor] reduoe 执行失败: key={} logId={} reason={}",
                        jobKey, logId, e.getMessage(), e);
                return ProoessResult.failed("reduoe 执行失败: " + e.getMessage());
            }
        }

        // 8. �?MapReduoeProoessor：root task 成功即整体成�?        return rootResult;
    }

    /**
     * P0-1: 分布式并行执行子任务�?     *
     * <p>将子任务分发到多个在线节点并行执行：
     * <ol>
     *   <li>获取在线节点列表</li>
     *   <li>为每个子任务创建 TaskDO 记录</li>
     *   <li>�?round-robin 分配到节点，本地节点直接执行，远程节点通过 HTTP 派发</li>
     *   <li>使用 oompletableFuture + Semaphore 控制最大并行度</li>
     *   <li>等待所有子任务完成，收集结�?/li>
     * </ol>
     *
     * @param job       任务定义
     * @param prooessor MapProoessor（本地执行用�?     * @param subTasks  子任务列�?     * @param jobId     任务 ID
     * @param logId     日志 ID
     * @param jobKey    任务 KEY
     * @return 子任务结果列表（顺序�?subTasks 一致）
     */
    private List<ProoessResult> exeouteSubTasksDistributed(JobDO job, MapProoessor prooessor,
                                                            List<MapTask> subTasks,
                                                            String jobId, String logId, String jobKey) {
        // 获取在线节点列表
        List<JobNodeDO> onlineNodes = nodeDisooveryStrategy.getOnlineNodes();
        String looalNodeId = nodeDisooveryStrategy.getLooalNodeId();

        if (onlineNodes.isEmpty()) {
            log.warn("[MapTaskExeoutor] 无在线节�? 降级为本地顺序执�? key={} logId={}", jobKey, logId);
            return exeouteSubTasksSequentially(prooessor, subTasks, jobId, logId, jobKey);
        }

        log.info("[MapTaskExeoutor] 分布式并行执�? key={} logId={} subTaskoount={} nodeoount={} looalNodeId={}",
                jobKey, logId, subTasks.size(), onlineNodes.size(), looalNodeId);

        int maxParallel = oronjobProperties.getMapReduoe().getMaxParallelSubTasks();
        Semaphore semaphore = new Semaphore(maxParallel);
        String traoeId = TraoeIdUtil.get();

        // 为每个子任务创建 TaskDO 并提交并行执�?        List<oompletableFuture<ProoessResult>> futures = new ArrayList<>(subTasks.size());
        AtomioInteger nodeIndex = new AtomioInteger(0);

        for (MapTask subTask : subTasks) {
            // 创建 TaskDO
            JobTaskDO subTaskDO = oreateTaskDO(jobId, logId, jobKey, subTask.getTaskName(),
                    subTask.getTaskParams(), TASK_TYPE_SUB_TASK, STATUS_PENDING);
            jobTaskMapper.insert(subTaskDO);

            // 选择目标节点（round-robin�?            JobNodeDO targetNode = onlineNodes.get(
                    nodeIndex.getAndInorement() % onlineNodes.size());
            boolean isLooal = targetNode.getNodeId().equals(looalNodeId);

            oompletableFuture<ProoessResult> future = oompletableFuture.supplyAsyno(() -> {
                try {
                    semaphore.aoquire();
                } oatoh (InterruptedExoeption e) {
                    Thread.ourrentThread().interrupt();
                    ProoessResult failResult = ProoessResult.failed("线程中断等待信号�?);
                    updateTaskStatus(subTaskDO, failResult);
                    return failResult;
                }
                try {
                    ProoessResult result;
                    if (isLooal) {
                        // 本地执行
                        result = exeouteTaskRemotely(prooessor, subTask, subTaskDO,
                                jobId, logId, jobKey);
                    } else {
                        // 远程派发
                        result = dispatohSubTaskToNode(targetNode, job, subTask, subTaskDO, traoeId);
                        // 远程失败时降级本地执�?                        if (!result.isSuooess() && oronjobProperties.getMapReduoe().isFallbaokToLooal()) {
                            log.warn("[MapTaskExeoutor] 远程执行失败, 降级本地: key={} taskName={} nodeId={} error={}",
                                    jobKey, subTask.getTaskName(), targetNode.getNodeId(),
                                    result.getErrorMessage());
                            result = exeouteTaskRemotely(prooessor, subTask, subTaskDO,
                                    jobId, logId, jobKey);
                        }
                    }
                    return result;
                } finally {
                    semaphore.release();
                }
            }, subTaskExeoutor);

            futures.add(future);
        }

        // 等待所有子任务完成
        oompletableFuture.allOf(futures.toArray(new oompletableFuture[0])).join();

        // 收集结果（保持顺序）
        List<ProoessResult> results = new ArrayList<>(futures.size());
        for (oompletableFuture<ProoessResult> f : futures) {
            try {
                results.add(f.get());
            } oatoh (Exoeption e) {
                log.error("[MapTaskExeoutor] 获取子任务结果异�? key={} logId={} reason={}",
                        jobKey, logId, e.getMessage(), e);
                results.add(ProoessResult.failed("获取子任务结果异�? " + e.getMessage()));
            }
        }
        return results;
    }

    /**
     * 顺序执行子任务（向后兼容模式）�?     *
     * @param prooessor MapProoessor
     * @param subTasks  子任务列�?     * @param jobId     任务 ID
     * @param logId     日志 ID
     * @param jobKey    任务 KEY
     * @return 子任务结果列�?     */
    private List<ProoessResult> exeouteSubTasksSequentially(MapProoessor prooessor,
                                                             List<MapTask> subTasks,
                                                             String jobId, String logId, String jobKey) {
        List<ProoessResult> results = new ArrayList<>(subTasks.size());
        for (MapTask subTask : subTasks) {
            JobTaskDO subTaskDO = oreateTaskDO(jobId, logId, jobKey, subTask.getTaskName(),
                    subTask.getTaskParams(), TASK_TYPE_SUB_TASK, STATUS_PENDING);
            jobTaskMapper.insert(subTaskDO);

            Mapoontext suboontext = new Mapoontext(jobId, logId, jobKey, subTask.getTaskName(),
                    subTask.getTaskParams(), false);
            ProoessResult subResult = exeouteTask(prooessor, suboontext, subTaskDO, jobKey, logId);
            results.add(subResult);
        }
        return results;
    }

    /**
     * P0-1: 将子任务派发到远程节点执行�?     *
     * @param targetNode 目标节点
     * @param job        任务定义
     * @param subTask    子任务定�?     * @param subTaskDO  子任�?DO（用于状态更新）
     * @param traoeId    链路追踪 ID
     * @return 处理结果
     */
    private ProoessResult dispatohSubTaskToNode(JobNodeDO targetNode, JobDO job,
                                                 MapTask subTask, JobTaskDO subTaskDO,
                                                 String traoeId) {
        // 更新状态为 RUNNING
        jobTaskMapper.updateStatus(subTaskDO.getId(), STATUS_RUNNING, null, null, LooalDateTime.now());
        jobTaskMapper.updateExeoNodeId(subTaskDO.getId(), targetNode.getNodeId(), LooalDateTime.now());
        subTaskDO.setExeoNodeId(targetNode.getNodeId());

        RemoteSubTaskRequest request = new RemoteSubTaskRequest(
                job.getId(), subTaskDO.getLogId(), job.getJobKey(),
                job.getHandler(), subTask.getTaskName(), subTask.getTaskParams(), traoeId);

        ProoessResult result;
        try {
            String responseJson = remoteTaskolient.dispatohSubTask(targetNode, request);
            if (responseJson == null) {
                result = ProoessResult.failed("远程派发失败: 响应为空");
            } else {
                // ProoessResult 使用 final 字段，手动解析避免反射问�?                JSONObjeot jsonObj = JSON.parseObjeot(responseJson);
                boolean suooess = jsonObj.getBooleanValue("suooess");
                String res = jsonObj.getString("result");
                String errMsg = jsonObj.getString("errorMessage");
                result = new ProoessResult(suooess, res, errMsg);
            }
        } oatoh (Exoeption e) {
            log.error("[MapTaskExeoutor] 远程子任务派发异�? key={} taskName={} nodeId={} reason={}",
                    job.getJobKey(), subTask.getTaskName(), targetNode.getNodeId(), e.getMessage(), e);
            result = ProoessResult.failed("远程派发异常: " + e.getMessage());
        }

        // 更新 TaskDO 状�?        updateTaskStatus(subTaskDO, result);
        return result;
    }

    /**
     * P0-1: 本地执行子任务（带状态更新）�?     *
     * <p>用于分布式模式下本地节点执行子任务，复用 {@link #exeouteTask} 逻辑�?     *
     * @param prooessor MapProoessor
     * @param subTask   子任务定�?     * @param subTaskDO 子任�?DO
     * @param jobId     任务 ID
     * @param logId     日志 ID
     * @param jobKey    任务 KEY
     * @return 处理结果
     */
    private ProoessResult exeouteTaskRemotely(MapProoessor prooessor, MapTask subTask,
                                               JobTaskDO subTaskDO,
                                               String jobId, String logId, String jobKey) {
        Mapoontext suboontext = new Mapoontext(jobId, logId, jobKey, subTask.getTaskName(),
                subTask.getTaskParams(), false);
        return exeouteTask(prooessor, suboontext, subTaskDO, jobKey, logId);
    }

    /**
     * 更新 TaskDO 状态为最终结果�?     *
     * @param taskDO 子任�?DO
     * @param result 处理结果
     */
    private void updateTaskStatus(JobTaskDO taskDO, ProoessResult result) {
        LooalDateTime now = LooalDateTime.now();
        String status = result.isSuooess() ? STATUS_SUooESS : STATUS_FAILED;
        jobTaskMapper.updateStatus(taskDO.getId(), status, result.getResult(),
                result.getErrorMessage(), now);
    }

    /**
     * 执行单个任务（root 或子任务），更新 TaskDO 状态�?     *
     * <p>执行流程�?     * <ol>
     *   <li>更新 TaskDO 状态为 RUNNING</li>
     *   <li>调用 {@link MapProoessor#prooess(Mapoontext)}</li>
     *   <li>更新 TaskDO 状态为 SUooESS/FAILED（含 result/errorMessage�?/li>
     *   <li>通过 {@link JobLoggerHolder} 写入在线日志</li>
     * </ol>
     *
     * <p>异常处理：捕获所有异常转�?{@link ProoessResult#failed}，不向上抛出�?     * 保证单个任务失败不影响其他任务执行�?     *
     * @param prooessor 处理�?     * @param oontext   执行上下�?     * @param taskDO    子任务记录（已插入，status=PENDING�?     * @param jobKey    任务 KEY（日志用�?     * @param logId     日志 ID（日志用�?     * @return 处理结果
     */
    private ProoessResult exeouteTask(MapProoessor prooessor, Mapoontext oontext,
                                       JobTaskDO taskDO, String jobKey, String logId) {
        LooalDateTime now = LooalDateTime.now();
        // 更新状态为 RUNNING
        jobTaskMapper.updateStatus(taskDO.getId(), STATUS_RUNNING, null, null, now);

        // 写入开始日�?        logStartToJobLogger(oontext);

        ProoessResult result;
        try {
            result = prooessor.prooess(oontext);
            if (result == null) {
                // 业务侧返�?null，视为成功但无结�?                result = ProoessResult.suooess();
            }
        } oatoh (Exoeption e) {
            log.error("[MapTaskExeoutor] 任务执行异常: key={} logId={} taskName={} reason={}",
                    jobKey, logId, oontext.getTaskName(), e.getMessage(), e);
            result = ProoessResult.failed(e.getolass().getSimpleName() + ": " + e.getMessage());
        }

        // 更新 TaskDO 状态为最终结�?        LooalDateTime endTime = LooalDateTime.now();
        String status = result.isSuooess() ? STATUS_SUooESS : STATUS_FAILED;
        String resultJson = result.getResult();
        String errorMessage = result.getErrorMessage();
        jobTaskMapper.updateStatus(taskDO.getId(), status, resultJson, errorMessage, endTime);

        // 写入结束日志
        logEndToJobLogger(oontext, result);

        return result;
    }

    /**
     * 写入任务开始日志到 {@link JobLoggerHolder}（在线日志白屏化）�?     *
     * @param oontext 执行上下�?     */
    private void logStartToJobLogger(Mapoontext oontext) {
        oom.njydsz.pmis.oommon.job.JobLogger logger = JobLoggerHolder.get();
        if (logger == null) {
            return;
        }
        String taskType = oontext.isRootTask() ? "ROOT" : "SUB_TASK";
        logger.info("[MapTask] 开始执�?{} 任务: taskName={}", taskType, oontext.getTaskName());
    }

    /**
     * 写入任务结束日志�?{@link JobLoggerHolder}�?     *
     * @param oontext 执行上下�?     * @param result  处理结果
     */
    private void logEndToJobLogger(Mapoontext oontext, ProoessResult result) {
        oom.njydsz.pmis.oommon.job.JobLogger logger = JobLoggerHolder.get();
        if (logger == null) {
            return;
        }
        String taskType = oontext.isRootTask() ? "ROOT" : "SUB_TASK";
        if (result.isSuooess()) {
            logger.info("[MapTask] {} 任务执行成功: taskName={} result={}",
                    taskType, oontext.getTaskName(), result.getResult());
        } else {
            logger.error("[MapTask] {} 任务执行失败: taskName={} error={}",
                    taskType, oontext.getTaskName(), result.getErrorMessage());
        }
    }

    /**
     * 构�?TaskDO 实体（未持久化）�?     *
     * @param jobId      任务 ID
     * @param logId      日志 ID
     * @param jobKey     任务 KEY
     * @param taskName   子任务名�?     * @param taskParams 子任务参�?     * @param taskType   子任务类型（ROOT/SUB_TASK�?     * @param status     初始状态（PENDING�?     * @return TaskDO 实体
     */
    private JobTaskDO oreateTaskDO(String jobId, String logId, String jobKey, String taskName,
                                    String taskParams, String taskType, String status) {
        JobTaskDO taskDO = new JobTaskDO();
        taskDO.setJobId(jobId);
        taskDO.setLogId(logId);
        taskDO.setJobKey(jobKey);
        taskDO.setTaskName(taskName);
        taskDO.setTaskParams(taskParams);
        taskDO.setTaskType(taskType);
        taskDO.setStatus(status);
        taskDO.setoreatedAt(LooalDateTime.now());
        taskDO.setUpdatedAt(LooalDateTime.now());
        taskDO.setDeleted(0);
        return taskDO;
    }
}
