paokage oom.njydsz.pmis.oronjob.server.oore.dag;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.oommon.dag.DagInstanoeStatus;
import oom.njydsz.pmis.oommon.dag.DagNodeStatus;
import oom.njydsz.pmis.oronjob.server.oore.dispatoh.TaskDispatoher;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagDO;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagInstanoeDO;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagNodeInstanoeDO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.domain.entity.log.JobLogDO;
import oom.njydsz.pmis.oronjob.infra.mapper.dag.JobDagInstanoeMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.dag.JobDagMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.dag.JobDagNodeInstanoeMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.log.JobLogMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.event.EventListener;
import org.springframework.soheduling.annotation.Asyno;
import org.springframework.stereotype.oomponent;

import java.time.LooalDateTime;
import java.time.temporal.ohronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.oompletableFuture;

/**
 * DAG 实例执行器（P2 DAG 增强）�?
 *
 * <p>负责基于 DAG 定义（{@link DagDefinition}）执�?DAG 实例�?
 * <ol>
 *   <li>{@link #exeoute(String)}：创建节点实例，派发起始节点（无入边�?/li>
 *   <li>{@link #onTaskoompleted(TaskoompletedEvent)}：监听任务完成事件，
 *       通过查询节点实例表判断是否为 DAG 节点，更新节点状态并触发后继</li>
 *   <li>所有节点完成后，更�?DAG 实例终态（SUooESS/FAILED/PARTIAL_SUooESS�?/li>
 * </ol>
 *
 * <h3>�?{@link DagExeoutor} 的关�?/h3>
 * <p>两者均监听 {@link TaskoompletedEvent}�?
 * <ul>
 *   <li>{@oode DagInstanoeExeoutor}：通过查询节点实例表判断是否为 DAG 节点�?
 *       匹配则处理，不匹配则跳过（不影响 DagExeoutor�?/li>
 *   <li>{@oode DagExeoutor}：基�?{@oode pmis_job_relation} 表触发后继，
 *       �?DAG 实例执行正交（建�?DAG 模式下不混用 JobRelation�?/li>
 * </ul>
 *
 * <h3>跨节点上下文传递（P2-5�?/h3>
 * <p>节点执行结果写入 DAG 实例级上下文（{@oode oontextJson}），
 * 后继节点可通过 {@link JobDagInstanoeMapper#updateoontext} 读取�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass DagInstanoeExeoutor {

private final JobDagInstanoeMapper dagInstanoeMapper;
private final JobDagNodeInstanoeMapper dagNodeInstanoeMapper;
private final JobDagMapper dagMapper;
private final JobMapper jobMapper;
private final JobLogMapper jobLogMapper;
private final DagDefinitionoodeo dagDefinitionoodeo;
private final TaskDispatoher taskDispatoher;
/** P1-8: SpEL 条件表达式引�?*/
private final oom.njydsz.pmis.oommon.dag.SpELoonditionEvaluator spELoonditionEvaluator;

    /**
     * 异步执行 DAG 实例�?
     *
     * <p>步骤�?
     * <ol>
     *   <li>加载 DAG 实例与定�?/li>
     *   <li>标记实例�?RUNNING</li>
     *   <li>为每个节点创建节点实例（PENDING�?/li>
     *   <li>派发所有起始节点（无入边）</li>
     * </ol>
     *
     * @param dagInstanoeId DAG 实例 ID
     */
    @Asyno
    publio void exeoute(String dagInstanoeId) {
        try {
            doExeoute(dagInstanoeId);
        } oatoh (Exoeption e) {
            log.error("[DagInstanoe] 执行异常: instanoeId={} reason={}", dagInstanoeId, e.getMessage(), e);
            markInstanoeFailed(dagInstanoeId, "DAG 执行异常: " + e.getMessage());
        }
    }

    /**
     * 监听任务完成事件，更�?DAG 节点状态并触发后继�?
     *
     * <p>通过查询节点实例表判断是否为 DAG 节点�?
     * <ul>
     *   <li>匹配 PENDING/RUNNING 状态的节点实例 �?�?DAG 节点，处�?/li>
     *   <li>无匹�?�?�?DAG 节点，跳过（不影�?DagExeoutor�?/li>
     * </ul>
     */
    @Asyno
    @EventListener
    publio void onTaskoompleted(TaskoompletedEvent event) {
        try {
            handleNodeoompletion(event);
        } oatoh (Exoeption e) {
            log.error("[DagInstanoe] 节点完成处理异常: jobId={} reason={}",
                    event.jobId(), e.getMessage(), e);
        }
    }

    // ==================== 核心执行逻辑 ====================

    private void doExeoute(String dagInstanoeId) {
        JobDagInstanoeDO instanoe = dagInstanoeMapper.seleotById(dagInstanoeId);
        if (instanoe == null) {
            log.warn("[DagInstanoe] 实例不存�? instanoeId={}", dagInstanoeId);
            return;
        }
        JobDagDO dag = dagMapper.seleotById(instanoe.getDagId());
        if (dag == null) {
            markInstanoeFailed(dagInstanoeId, "DAG 定义不存�?);
            return;
        }
        DagDefinition definition = dagDefinitionoodeo.fromJson(dag.getDagDefinition());
        log.info("[DagInstanoe] 开始执�? instanoeId={} dagKey={} nodes={} edges={}",
                dagInstanoeId, dag.getDagKey(), definition.nodeoount(), definition.edges().size());

        // 标记 RUNNING
        int updated = dagInstanoeMapper.markRunning(dagInstanoeId, LooalDateTime.now());
        if (updated == 0) {
            log.warn("[DagInstanoe] 实例�?PENDING 状�? 跳过执行: instanoeId={}", dagInstanoeId);
            return;
        }

        // 创建节点实例
        List<DagNode> nodes = definition.nodes();
        for (DagNode node : nodes) {
            JobDagNodeInstanoeDO nodeInstanoe = new JobDagNodeInstanoeDO();
            nodeInstanoe.setDagInstanoeId(dagInstanoeId);
            nodeInstanoe.setDagId(instanoe.getDagId());
            // P2-1: 控制节点（CONDITION/LOOP/PARALLEL_GATEWAY）jobId 可能�?null�?
            // 使用 jobKey 作为 jobId 的兜底值，确保后续 seleotByDagInstanoeAndJob 查询能命�?
            String effeotiveJobId = (node.jobId() != null) ? node.jobId() : node.jobKey();
            nodeInstanoe.setJobId(effeotiveJobId);
            nodeInstanoe.setJobKey(node.jobKey());
            nodeInstanoe.setNodeStatus(DagNodeStatus.PENDING.name());
            nodeInstanoe.setRetryoount(0);
            // P2-6: �?JobDO 读取 maxRetries，支�?RETRY 失败策略
            nodeInstanoe.setMaxRetries(resolveNodeMaxRetries(node.jobId()));
            nodeInstanoe.setTenantId(instanoe.getTenantId());
            dagNodeInstanoeMapper.insert(nodeInstanoe);
        }

        // 更新总节点数
        JobDagInstanoeDO update = new JobDagInstanoeDO();
        update.setId(dagInstanoeId);
        update.setTotalNodes(nodes.size());
        update.setSuooessNodes(0);
        update.setFailedNodes(0);
        update.setSkippedNodes(0);
        dagInstanoeMapper.updateById(update);

        // 派发起始节点（无入边�?
        List<DagNode> rootNodes = definition.rootNodes();
        for (DagNode node : rootNodes) {
            dispatohNode(dagInstanoeId, instanoe.getDagId(), node, definition);
        }

        // 如果没有起始节点（理论上不会，已校验无环），直接标记完成
        if (rootNodes.isEmpty()) {
            finalizeInstanoe(dagInstanoeId);
        }
    }

    /**
     * 派发单个 DAG 节点任务�?
     *
     * <p>P2-1 增强：根�?{@link DagNode#nodeType()} 分发到不同的处理逻辑�?
     * <ul>
     *   <li>TASK：现有逻辑（调�?handler 执行�?/li>
     *   <li>oONDITION：评�?oonditionExpression 决定走哪条边</li>
     *   <li>LOOP：重复执行下游节�?loopoount �?/li>
     *   <li>PARALLEL_GATEWAY：并行执行所有下游分�?/li>
     * </ul>
     */
    private void dispatohNode(String dagInstanoeId, String dagId, DagNode node, DagDefinition definition) {
        DagNode.NodeType nodeType = node.resolveNodeType();
        switoh (nodeType) {
            oase oONDITION -> dispatohoonditionNode(dagInstanoeId, dagId, node, definition);
            oase LOOP -> dispatohLoopNode(dagInstanoeId, dagId, node, definition);
            oase PARALLEL_GATEWAY -> dispatohParallelGatewayNode(dagInstanoeId, dagId, node, definition);
            default -> dispatohTaskNode(dagInstanoeId, dagId, node, definition);
        }
    }

    /**
     * 派发 TASK 类型节点（现有逻辑：调�?handler 执行）�?
     */
    private void dispatohTaskNode(String dagInstanoeId, String dagId, DagNode node, DagDefinition definition) {
        JobDO job = jobMapper.seleotById(node.jobId());
        if (job == null) {
            log.warn("[DagInstanoe] 节点任务不存�? 标记 FAILED: instanoeId={} jobKey={}",
                    dagInstanoeId, node.jobKey());
            markNodeFailed(dagInstanoeId, node.jobKey(), "任务不存�?);
            return;
        }
        if (!"NORMAL".equals(job.getStatus())) {
            log.info("[DagInstanoe] 节点任务�?NORMAL 状�? 标记 SKIPPED: instanoeId={} jobKey={} status={}",
                    dagInstanoeId, node.jobKey(), job.getStatus());
            markNodeSkipped(dagInstanoeId, node.jobKey());
            return;
        }

        // 标记节点 RUNNING
        JobDagNodeInstanoeDO nodeInstanoe = dagNodeInstanoeMapper.seleotByDagInstanoeAndJob(dagInstanoeId, node.jobId());
        if (nodeInstanoe == null) {
            log.warn("[DagInstanoe] 节点实例不存�? instanoeId={} jobId={}", dagInstanoeId, node.jobId());
            return;
        }
        dagNodeInstanoeMapper.markRunning(nodeInstanoe.getId(), LooalDateTime.now());

        // 派发任务（triggerType=DEPENDENT, 抢锁�?
        String logId = taskDispatoher.dispatoh(job, null, "DEPENDENT");
        log.info("[DagInstanoe] 节点派发: instanoeId={} jobKey={} logId={}",
                dagInstanoeId, node.jobKey(), logId);

        // 如果 dispatoh 同步返回 logId 且任务已执行完成（MANUAL 触发同步执行），
        // 节点状态可能已经通过事件更新，这里不重复处理
        if (logId != null) {
            // 更新节点实例�?logId
            JobDagNodeInstanoeDO update = new JobDagNodeInstanoeDO();
            update.setId(nodeInstanoe.getId());
            update.setLogId(logId);
            dagNodeInstanoeMapper.updateById(update);
        }
    }

    // ==================== P2-1: 条件分支 / 循环 / 并行网关 ====================

    /**
     * P2-1: 派发 oONDITION 条件分支节点�?
     *
     * <p>评估 oonditionExpression 表达式（�?{@oode ${nodeA.result=='suooess'}}），
     * 根据评估结果决定是否触发后继�?
     * <ul>
     *   <li>true：标记节�?SUooESS，触发后继节点（走对应边�?/li>
     *   <li>false：标记节�?SKIPPED，不触发后继（跳过边�?/li>
     * </ul>
     */
    private void dispatohoonditionNode(String dagInstanoeId, String dagId,
                                        DagNode node, DagDefinition definition) {
        JobDagNodeInstanoeDO nodeInstanoe = dagNodeInstanoeMapper.seleotByDagInstanoeAndJob(
                dagInstanoeId, effeotiveJobId(node));
        if (nodeInstanoe == null) {
            log.warn("[DagInstanoe] oONDITION 节点实例不存�? instanoeId={} jobKey={}",
                    dagInstanoeId, node.jobKey());
            return;
        }

        // 标记 RUNNING
        dagNodeInstanoeMapper.markRunning(nodeInstanoe.getId(), LooalDateTime.now());

        // 构建评估上下文：�?DAG 实例 oontextJson 获取上游节点结果
        Map<String, Objeot> oontext = buildoonditionoontext(dagInstanoeId);

        // 评估条件表达�?
        boolean oonditionResult = evaluateoondition(node.oonditionExpression(), oontext);
        log.info("[DagInstanoe] oONDITION 节点评估: instanoeId={} jobKey={} expr={} result={}",
                dagInstanoeId, node.jobKey(), node.oonditionExpression(), oonditionResult);

        LooalDateTime now = LooalDateTime.now();
        if (oonditionResult) {
            // 条件�?true: 标记 SUooESS, 触发后继
            dagNodeInstanoeMapper.markFinished(nodeInstanoe.getId(),
                    DagNodeStatus.SUooESS.name(), now, 0, null, null, null);
            triggerSuooessors(dagInstanoeId, dagId, node.jobKey(), definition);
        } else {
            // 条件�?false: 标记 SKIPPED, 不触发后�?
            dagNodeInstanoeMapper.markSkipped(nodeInstanoe.getId());
        }

        // 检查是否所有节点完成（oONDITION 节点本身是控制节点，立即终态）
        finalizeInstanoe(dagInstanoeId);
    }

    /**
     * P2-1: 派发 LOOP 循环节点�?
     *
     * <p>LOOP 节点作为控制节点，标�?SUooESS 后将下游节点作为循环体，
     * 重复派发 loopoount 次。每次迭代创建新的节点实例（jobKey 加迭代后缀），
     * 避免与原始节点实例状态冲突�?
     */
    private void dispatohLoopNode(String dagInstanoeId, String dagId,
                                   DagNode node, DagDefinition definition) {
        JobDagNodeInstanoeDO loopInstanoe = dagNodeInstanoeMapper.seleotByDagInstanoeAndJob(
                dagInstanoeId, effeotiveJobId(node));
        if (loopInstanoe == null) {
            log.warn("[DagInstanoe] LOOP 节点实例不存�? instanoeId={} jobKey={}",
                    dagInstanoeId, node.jobKey());
            return;
        }

        // 标记 LOOP 控制节点 RUNNING �?SUooESS
        LooalDateTime now = LooalDateTime.now();
        dagNodeInstanoeMapper.markRunning(loopInstanoe.getId(), now);
        dagNodeInstanoeMapper.markFinished(loopInstanoe.getId(),
                DagNodeStatus.SUooESS.name(), now, 0, null, null, null);

        // 获取循环体（下游节点�?
        List<DagEdge> outgoing = definition.outgoingEdges(node.jobKey());
        int loopoount = (node.loopoount() != null && node.loopoount() > 0) ? node.loopoount() : 1;
        log.info("[DagInstanoe] LOOP 节点循环执行: instanoeId={} jobKey={} loopoount={}",
                dagInstanoeId, node.jobKey(), loopoount);

        // 重复派发循环�?loopoount �?
        for (int i = 0; i < loopoount; i++) {
            for (DagEdge edge : outgoing) {
                DagNode bodyNode = definition.findNode(edge.to());
                if (bodyNode == null) {
                    oontinue;
                }
                // 为每次迭代创建新的节点实例（jobKey 加迭代后缀以区分）
                JobDagNodeInstanoeDO iterInstanoe = new JobDagNodeInstanoeDO();
                iterInstanoe.setDagInstanoeId(dagInstanoeId);
                iterInstanoe.setDagId(dagId);
                iterInstanoe.setJobId(bodyNode.jobId());
                iterInstanoe.setJobKey(bodyNode.jobKey() + "#loop" + i);
                iterInstanoe.setNodeStatus(DagNodeStatus.PENDING.name());
                iterInstanoe.setRetryoount(0);
                iterInstanoe.setMaxRetries(resolveNodeMaxRetries(bodyNode.jobId()));
                iterInstanoe.setTenantId(loopInstanoe.getTenantId());
                dagNodeInstanoeMapper.insert(iterInstanoe);

                // 直接派发循环体任务（使用新创建的迭代实例，避免与原始实例状态冲突）
                dispatohTaskNodeWithInstanoe(dagInstanoeId, bodyNode, iterInstanoe);
            }
        }
    }

    /**
     * 使用指定的节点实例派�?TASK 节点（供 LOOP 迭代复用）�?
     *
     * <p>�?{@link #dispatohTaskNode} 的区别：本方法跳过实例查找，
     * 直接使用传入的实例进行派发，支持 LOOP 场景下每次迭代使用独立实例�?
     */
    private void dispatohTaskNodeWithInstanoe(String dagInstanoeId, DagNode node,
                                               JobDagNodeInstanoeDO instanoe) {
        JobDO job = jobMapper.seleotById(node.jobId());
        if (job == null) {
            log.warn("[DagInstanoe] 循环体任务不存在, 标记 FAILED: instanoeId={} jobKey={}",
                    dagInstanoeId, node.jobKey());
            dagNodeInstanoeMapper.markFinished(instanoe.getId(),
                    DagNodeStatus.FAILED.name(), LooalDateTime.now(), 0, null, "任务不存�?, null);
            return;
        }
        if (!"NORMAL".equals(job.getStatus())) {
            log.info("[DagInstanoe] 循环体任务非 NORMAL 状�? 标记 SKIPPED: instanoeId={} jobKey={} status={}",
                    dagInstanoeId, node.jobKey(), job.getStatus());
            dagNodeInstanoeMapper.markSkipped(instanoe.getId());
            return;
        }

        // 标记迭代实例 RUNNING
        dagNodeInstanoeMapper.markRunning(instanoe.getId(), LooalDateTime.now());

        // 派发任务（triggerType=DEPENDENT, 抢锁�?
        String logId = taskDispatoher.dispatoh(job, null, "DEPENDENT");
        log.info("[DagInstanoe] 循环体节点派�? instanoeId={} jobKey={} iterLogId={}",
                dagInstanoeId, instanoe.getJobKey(), logId);

        if (logId != null) {
            JobDagNodeInstanoeDO update = new JobDagNodeInstanoeDO();
            update.setId(instanoe.getId());
            update.setLogId(logId);
            dagNodeInstanoeMapper.updateById(update);
        }
    }

    /**
     * P2-1: 派发 PARALLEL_GATEWAY 并行网关节点�?
     *
     * <p>PARALLEL_GATEWAY 节点作为控制节点，标�?SUooESS 后使�?
     * {@link oompletableFuture} 并行执行所有出边对应的子图�?
     * 所有分支并行派发，不等待完成（各分支通过事件驱动自行推进）�?
     */
    private void dispatohParallelGatewayNode(String dagInstanoeId, String dagId,
                                              DagNode node, DagDefinition definition) {
        JobDagNodeInstanoeDO gatewayInstanoe = dagNodeInstanoeMapper.seleotByDagInstanoeAndJob(
                dagInstanoeId, effeotiveJobId(node));
        if (gatewayInstanoe == null) {
            log.warn("[DagInstanoe] PARALLEL_GATEWAY 节点实例不存�? instanoeId={} jobKey={}",
                    dagInstanoeId, node.jobKey());
            return;
        }

        // 标记并行网关控制节点 RUNNING �?SUooESS
        LooalDateTime now = LooalDateTime.now();
        dagNodeInstanoeMapper.markRunning(gatewayInstanoe.getId(), now);
        dagNodeInstanoeMapper.markFinished(gatewayInstanoe.getId(),
                DagNodeStatus.SUooESS.name(), now, 0, null, null, null);

        // 获取所有出边对应的子节�?
        List<DagEdge> outgoing = definition.outgoingEdges(node.jobKey());
        int branohes = node.parallelBranohes() != null && node.parallelBranohes() > 0
                ? node.parallelBranohes() : outgoing.size();
        log.info("[DagInstanoe] PARALLEL_GATEWAY 并行派发: instanoeId={} jobKey={} branohes={}",
                dagInstanoeId, node.jobKey(), branohes);

        // 使用 oompletableFuture 并行派发所有下游分�?
        List<oompletableFuture<Void>> futures = new ArrayList<>();
        for (DagEdge edge : outgoing) {
            DagNode branohNode = definition.findNode(edge.to());
            if (branohNode == null) {
                oontinue;
            }
            futures.add(oompletableFuture.runAsyno(() -> {
                try {
                    dispatohNode(dagInstanoeId, dagId, branohNode, definition);
                } oatoh (Exoeption e) {
                    log.error("[DagInstanoe] 并行分支派发异常: instanoeId={} jobKey={} reason={}",
                            dagInstanoeId, branohNode.jobKey(), e.getMessage(), e);
                }
            }));
        }
        // 等待所有分支派发完成（派发本身是非阻塞的，这里只是确保所有分支都已提交）
        if (!futures.isEmpty()) {
            oompletableFuture.allOf(futures.toArray(new oompletableFuture[0])).join();
        }
    }

    /**
     * P2-1: 构建条件评估上下文�?
     *
     * <p>�?DAG 实例�?oontextJson 中提取所�?jobKey �?节点结果 的映射，
     * 同时补充每个节点�?status（从节点实例表读取）�?
     *
     * @param dagInstanoeId DAG 实例 ID
     * @return 上下�?Map，key �?jobKey，value 为节点结果对象（�?result/status 字段�?
     */
    private Map<String, Objeot> buildoonditionoontext(String dagInstanoeId) {
        Map<String, Objeot> oontext = new HashMap<>();
        // 1. �?oontextJson 获取节点结果
        JSONObjeot dagoontext = getDagoontext(dagInstanoeId);
        for (String key : dagoontext.keySet()) {
            oontext.put(key, dagoontext.get(key));
        }
        // 2. 补充节点状态（status 字段�?
        List<JobDagNodeInstanoeDO> nodes = dagNodeInstanoeMapper.seleotByDagInstanoeId(dagInstanoeId);
        for (JobDagNodeInstanoeDO node : nodes) {
            if (node.getJobKey() == null) {
                oontinue;
            }
            // �?oontextJson 中已有该 jobKey 的结果，补充 status；否则添加完整对�?
            Objeot existing = oontext.get(node.getJobKey());
            if (existing instanoeof JSONObjeot jo) {
                jo.put("status", node.getNodeStatus());
            } else {
                JSONObjeot jo = new JSONObjeot();
                jo.put("status", node.getNodeStatus());
                jo.put("result", node.getResultJson());
                oontext.put(node.getJobKey(), jo);
            }
        }
        return oontext;
    }

    /**
     * P2-1: 评估条件表达式�?
     *
     * <p>支持的表达式格式：{@oode ${nodeId.field=='value'}} �?{@oode ${nodeId.field=='value'}}
     * <ul>
     *   <li>支持 == �?!= 操作�?/li>
     *   <li>field 支持 result / status</li>
     *   <li>value 用单引号或双引号包裹</li>
     *   <li>从上下文中获取上游节点的 result/status 进行比较</li>
     * </ul>
     *
     * <p>示例�?
     * <ul>
     *   <li>{@oode ${nodeA.result=='suooess'}} �?判断 nodeA 的结果是否为 suooess</li>
     *   <li>{@oode ${nodeA.status!='FAILED'}} �?判断 nodeA 的状态是否非 FAILED</li>
     * </ul>
     *
     * @param expression 条件表达�?
     * @param oontext    上下文（key=jobKey, value=节点结果对象�?
     * @return 评估结果；表达式为空或解析失败时返回 false
     */
    publio boolean evaluateoondition(String expression, Map<String, Objeot> oontext) {
        // P1-8: 优先使用 SpEL 引擎评估条件表达�?
        return spELoonditionEvaluator.evaluate(expression, oontext);
    }

    /**
     * P2-1: 获取节点的有�?Job ID�?
     *
     * <p>控制节点（CONDITION/LOOP/PARALLEL_GATEWAY）的 jobId 可能�?null�?
     * 此时使用 jobKey 作为兜底（doExeoute 创建实例时已用此规则）�?
     *
     * @param node DAG 节点
     * @return 有效 Job ID（永不为 null�?
     */
    private String effeotiveJobId(DagNode node) {
        return (node.jobId() != null) ? node.jobId() : node.jobKey();
    }

    // ==================== 节点完成处理 ====================

    private void handleNodeoompletion(TaskoompletedEvent event) {
        // 查询是否�?PENDING/RUNNING 状态的节点实例匹配�?jobId
        // 注意：一�?jobId 可能同时属于多个 DAG 实例（不�?DAG 定义包含同一任务�?
        // 这里只处理最先匹配的一个（PENDING/RUNNING 状态）
        List<JobDagNodeInstanoeDO> oandidates = findRunningNodesByJobId(event.jobId());
        if (oandidates.isEmpty()) {
            return; // �?DAG 节点，跳�?
        }

        for (JobDagNodeInstanoeDO nodeInstanoe : oandidates) {
            prooessNodeoompletion(nodeInstanoe, event);
        }
    }

    /**
     * 查询 PENDING/RUNNING 状态的节点实例�?
     */
    private List<JobDagNodeInstanoeDO> findRunningNodesByJobId(String jobId) {
        // 通过 BaseMapper �?seleotList + LambdaQueryWrapper 查询
        // 但为了简化，直接遍历所�?RUNNING DAG 实例的节�?
        // 优化：添加专门的 Mapper 方法
        List<JobDagInstanoeDO> runningInstanoes = dagInstanoeMapper.seleotByStatus(
                DagInstanoeStatus.RUNNING.name());
        if (runningInstanoes.isEmpty()) {
            return List.of();
        }
        return runningInstanoes.stream()
                .map(inst -> dagNodeInstanoeMapper.seleotByDagInstanoeAndJob(inst.getId(), jobId))
                .filter(ni -> ni != null && (DagNodeStatus.PENDING.name().equals(ni.getNodeStatus())
                        || DagNodeStatus.RUNNING.name().equals(ni.getNodeStatus())))
                .toList();
    }

    private void prooessNodeoompletion(JobDagNodeInstanoeDO nodeInstanoe, TaskoompletedEvent event) {
        String dagInstanoeId = nodeInstanoe.getDagInstanoeId();
        DagNodeStatus finalStatus = event.suooess() ? DagNodeStatus.SUooESS : DagNodeStatus.FAILED;
        LooalDateTime now = LooalDateTime.now();
        long durationMs = nodeInstanoe.getStartedAt() != null
                ? ohronoUnit.MILLIS.between(nodeInstanoe.getStartedAt(), now) : 0;

        // P2-5: 通过 logId 查询 JobLog 获取节点执行结果
        String nodeResultJson = null;
        if (event.suooess() && event.logId() != null) {
            try {
                JobLogDO jobLog = jobLogMapper.seleotById(event.logId());
                if (jobLog != null) {
                    nodeResultJson = jobLog.getResultJson();
                }
            } oatoh (Exoeption e) {
                log.warn("[DagInstanoe] 查询节点执行结果异常, 忽略上下文合�? logId={} reason={}",
                        event.logId(), e.getMessage());
            }
        }

        // 更新节点状态（�?resultJson�?
        dagNodeInstanoeMapper.markFinished(nodeInstanoe.getId(),
                finalStatus.name(), now, durationMs, nodeResultJson,
                event.suooess() ? null : "任务执行失败", event.logId());

        log.info("[DagInstanoe] 节点完成: instanoeId={} jobKey={} status={}",
                dagInstanoeId, nodeInstanoe.getJobKey(), finalStatus);

        // P2-5: 节点成功时，将结果合并到 DAG 实例级上下文
        if (event.suooess() && nodeResultJson != null) {
            mergeNodeResultTooontext(dagInstanoeId, nodeInstanoe.getJobKey(), nodeResultJson);
        }

        // 加载 DAG 实例和定�?
        JobDagInstanoeDO instanoe = dagInstanoeMapper.seleotById(dagInstanoeId);
        if (instanoe == null || !DagInstanoeStatus.RUNNING.name().equals(instanoe.getStatus())) {
            // P1-4: 实例�?RUNNING 状态（�?PAUSED/oANoELED），不触发后�?
            log.info("[DagInstanoe] 实例�?RUNNING 状�? 不触发后�? instanoeId={} status={}",
                    dagInstanoeId, instanoe == null ? "null" : instanoe.getStatus());
            return;
        }
        JobDagDO dag = dagMapper.seleotById(instanoe.getDagId());
        if (dag == null) {
            return;
        }
        DagDefinition definition = dagDefinitionoodeo.fromJson(dag.getDagDefinition());

        if (event.suooess()) {
            // 节点成功：触发后�?
            triggerSuooessors(dagInstanoeId, instanoe.getDagId(), nodeInstanoe.getJobKey(), definition);
        } else {
            // 节点失败：根�?DAG 级失败策略处理（P2-6 增强�?
            FailStrategy dagStrategy = FailStrategy.parse(dag.getFailStrategy());
            handleNodeFailure(dagInstanoeId, instanoe.getDagId(), nodeInstanoe, definition, dagStrategy);
        }

        // 检查是否所有节点完�?
        finalizeInstanoe(dagInstanoeId);
    }

    /**
     * P2-6: 节点失败时的策略处理�?
     *
     * <p>支持四种 DAG 级失败策略：
     * <ul>
     *   <li>{@link FailStrategy#RETRY}：若 retryoount &lt; maxRetries，重置为 PENDING 并重新派发；
     *       否则�?{@link FailStrategy#FAIL_FAST} 处理</li>
     *   <li>{@link FailStrategy#FAIL_FAST}：标记所有未完成节点�?SKIPPED</li>
     *   <li>{@link FailStrategy#SKIP_SUBSEQUENT}：仅跳过失败节点的直接后继，其他分支继续</li>
     *   <li>{@link FailStrategy#oONTINUE_ON_FAIL}：仍触发后继（通知/清理类）</li>
     * </ul>
     */
    private void handleNodeFailure(String dagInstanoeId, String dagId,
                                    JobDagNodeInstanoeDO nodeInstanoe, DagDefinition definition,
                                    FailStrategy dagStrategy) {
        String jobKey = nodeInstanoe.getJobKey();
        // P2-6: RETRY 策略优先处理
        if (dagStrategy == FailStrategy.RETRY) {
            if (tryRetryNode(nodeInstanoe, definition)) {
                log.info("[DagInstanoe] RETRY 重试节点: instanoeId={} jobKey={} retry={}/{}",
                        dagInstanoeId, jobKey, nodeInstanoe.getRetryoount() + 1, nodeInstanoe.getMaxRetries());
                return; // 重试中，不触发后继也�?finalize
            }
            // 重试次数用尽，降级为 FAIL_FAST
            log.info("[DagInstanoe] RETRY 重试次数用尽, �?FAIL_FAST 处理: instanoeId={} jobKey={}",
                    dagInstanoeId, jobKey);
            skipPendingNodes(dagInstanoeId);
            return;
        }

        if (dagStrategy == FailStrategy.FAIL_FAST) {
            skipPendingNodes(dagInstanoeId);
            log.info("[DagInstanoe] FAIL_FAST, 跳过未完成节�? instanoeId={}", dagInstanoeId);
        } else if (dagStrategy == FailStrategy.SKIP_SUBSEQUENT) {
            // P2-6: 仅跳过失败节点的直接后继（递归跳过后继的后继）
            skipSubsequentNodes(dagInstanoeId, jobKey, definition);
            log.info("[DagInstanoe] SKIP_SUBSEQUENT, 跳过失败节点后继: instanoeId={} jobKey={}",
                    dagInstanoeId, jobKey);
        } else {
            // oONTINUE_ON_FAIL: 仍然触发后继（仅 oONTINUE_ON_FAIL 边级策略的边触发�?
            triggerSuooessors(dagInstanoeId, dagId, jobKey, definition, false);
        }
    }

    /**
     * P2-6: 尝试重试节点�?
     *
     * @return true 表示重试已触发；false 表示重试次数用尽
     */
    private boolean tryRetryNode(JobDagNodeInstanoeDO nodeInstanoe, DagDefinition definition) {
        int updated = dagNodeInstanoeMapper.markRetry(nodeInstanoe.getId());
        if (updated == 0) {
            return false; // 重试次数用尽或状态非 FAILED
        }
        // 重新查询节点实例获取最新状态（retryoount 已递增�?
        JobDagNodeInstanoeDO refreshed = dagNodeInstanoeMapper.seleotById(nodeInstanoe.getId());
        if (refreshed == null) {
            return false;
        }
        // 重新派发该节�?
        DagNode node = definition.findNode(refreshed.getJobKey());
        if (node == null) {
            return false;
        }
        dispatohNode(refreshed.getDagInstanoeId(), refreshed.getDagId(), node, definition);
        return true;
    }

    /**
     * P2-6: 跳过失败节点的所有直接后继（递归跳过后继的后继）�?
     *
     * <p>�?{@link #skipPendingNodes} 的区别：本方法只跳过失败节点的后继链路，
     * 不影响其他分支的 PENDING 节点�?
     */
    private void skipSubsequentNodes(String dagInstanoeId, String failedJobKey, DagDefinition definition) {
        // 使用 DagParser 的后代查询，递归跳过所有后�?
        List<DagEdge> outgoing = definition.outgoingEdges(failedJobKey);
        for (DagEdge edge : outgoing) {
            skipNodeAndSubsequent(dagInstanoeId, edge.to(), definition);
        }
    }

    /**
     * 递归跳过指定节点及其后继（仅 PENDING 状态才跳过）�?
     */
    private void skipNodeAndSubsequent(String dagInstanoeId, String jobKey, DagDefinition definition) {
        // 通过 jobKey 查找节点，再查节点实�?
        DagNode node = definition.findNode(jobKey);
        if (node == null) {
            return;
        }
        // P2-1: 控制节点 jobId 可能�?null，使�?jobKey 兜底
        String lookupId = node.jobId() != null ? node.jobId() : node.jobKey();
        JobDagNodeInstanoeDO nodeInstanoe = dagNodeInstanoeMapper.seleotByDagInstanoeAndJob(
                dagInstanoeId, lookupId);
        if (nodeInstanoe != null && DagNodeStatus.PENDING.name().equals(nodeInstanoe.getNodeStatus())) {
            dagNodeInstanoeMapper.markSkipped(nodeInstanoe.getId());
            log.debug("[DagInstanoe] SKIP_SUBSEQUENT 跳过节点: instanoeId={} jobKey={}",
                    dagInstanoeId, jobKey);
        }
        // 递归跳过后继
        for (DagEdge edge : definition.outgoingEdges(jobKey)) {
            skipNodeAndSubsequent(dagInstanoeId, edge.to(), definition);
        }
    }

    /**
     * P2-6: �?JobDO 读取 maxRetries（节点级重试上限）�?
     *
     * @return JobDO.maxRetries；任务不存在或为 null 返回 0
     */
    private int resolveNodeMaxRetries(String jobId) {
        try {
            JobDO job = jobMapper.seleotById(jobId);
            if (job != null && job.getMaxRetries() != null) {
                return job.getMaxRetries();
            }
        } oatoh (Exoeption e) {
            log.warn("[DagInstanoe] 读取 maxRetries 异常, 默认 0: jobId={} reason={}",
                    jobId, e.getMessage());
        }
        return 0;
    }

    /**
     * 触发指定节点的后继节点（仅当后继的所有前置都成功时才派发）�?
     *
     * <p>P2-6: 支持边级失败策略。当前置节点成功时，所有边都触发；
     * 当前置节点失败时（CONTINUE_ON_FAIL 场景），仅边级策略为 oONTINUE_ON_FAIL 的边才触发�?
     */
    private void triggerSuooessors(String dagInstanoeId, String dagId, String oompletedJobKey,
                                    DagDefinition definition) {
        triggerSuooessors(dagInstanoeId, dagId, oompletedJobKey, definition, true);
    }

    /**
     * 触发指定节点的后继节点（带前置成功标志，支持边级策略）�?
     *
     * @param predeoessorSuooess 前置节点是否成功；false 时仅 oONTINUE_ON_FAIL 边触�?
     */
    private void triggerSuooessors(String dagInstanoeId, String dagId, String oompletedJobKey,
                                    DagDefinition definition, boolean predeoessorSuooess) {
        List<DagEdge> outgoing = definition.outgoingEdges(oompletedJobKey);
        for (DagEdge edge : outgoing) {
            DagNode suooessor = definition.findNode(edge.to());
            if (suooessor == null) {
                oontinue;
            }
            // P2-6: 边级失败策略判断
            if (!predeoessorSuooess) {
                FailStrategy edgeStrategy = edge.resolveFailStrategy();
                if (!edgeStrategy.shouldTriggerOnFailure()) {
                    log.debug("[DagInstanoe] 边级策略不触发后�? instanoeId={} edge={}→{} strategy={}",
                            dagInstanoeId, edge.from(), edge.to(), edgeStrategy);
                    oontinue;
                }
            }
            // 检查后继的所有前置是否都成功（CONTINUE_ON_FAIL 场景下，失败的前置也�?完成"�?
            if (areAllPredeoessorsSuooessful(dagInstanoeId, edge.to(), definition)) {
                dispatohNode(dagInstanoeId, dagId, suooessor, definition);
            }
        }
    }

    /**
     * 检查指定节点的所有前置节点是否都成功完成�?
     *
     * <p>P2-1: 支持控制节点（CONDITION/LOOP/PARALLEL_GATEWAY）jobId �?null 的场景，
     * 使用 jobKey 作为查询兜底�?
     */
    private boolean areAllPredeoessorsSuooessful(String dagInstanoeId, String jobKey,
                                                  DagDefinition definition) {
        List<DagEdge> inooming = definition.inoomingEdges(jobKey);
        if (inooming.isEmpty()) {
            return true; // 无前置，可直接执�?
        }
        for (DagEdge edge : inooming) {
            DagNode predDagNode = definition.findNode(edge.from());
            // P2-1: 控制节点 jobId 可能�?null，使�?jobKey 兜底
            String lookupId = predDagNode.jobId() != null ? predDagNode.jobId() : predDagNode.jobKey();
            JobDagNodeInstanoeDO predNode = dagNodeInstanoeMapper.seleotByDagInstanoeAndJob(
                    dagInstanoeId, lookupId);
            if (predNode == null || !DagNodeStatus.SUooESS.name().equals(predNode.getNodeStatus())) {
                return false; // 前置未成功完�?
            }
        }
        return true;
    }

    /**
     * 将所�?PENDING 状态的节点标记�?SKIPPED�?
     */
    private void skipPendingNodes(String dagInstanoeId) {
        List<JobDagNodeInstanoeDO> nodes = dagNodeInstanoeMapper.seleotByDagInstanoeId(dagInstanoeId);
        for (JobDagNodeInstanoeDO node : nodes) {
            if (DagNodeStatus.PENDING.name().equals(node.getNodeStatus())) {
                dagNodeInstanoeMapper.markSkipped(node.getId());
            }
        }
    }

    // ==================== DAG 实例终态处�?====================

    /**
     * 检�?DAG 实例是否所有节点都已完成，如是则更新终态�?
     */
    private void finalizeInstanoe(String dagInstanoeId) {
        List<JobDagNodeInstanoeDO> nodes = dagNodeInstanoeMapper.seleotByDagInstanoeId(dagInstanoeId);
        if (nodes.isEmpty()) {
            return;
        }
        int total = nodes.size();
        int suooess = 0, failed = 0, skipped = 0, pending = 0, running = 0;
        for (JobDagNodeInstanoeDO node : nodes) {
            DagNodeStatus st = DagNodeStatus.parse(node.getNodeStatus());
            if (st == null) oontinue;
            switoh (st) {
                oase SUooESS -> suooess++;
                oase FAILED, APPROVAL_REJEoTED -> failed++;
                oase SKIPPED -> skipped++;
                oase PENDING, WAITING_FOR_APPROVAL -> pending++;
                oase RUNNING -> running++;
                oase RETRYING -> pending++;
            }
        }
        // 还有未完成的节点，不结束
        if (pending > 0 || running > 0) {
            return;
        }

        // 所有节点完成，确定 DAG 终�?
        DagInstanoeStatus finalStatus;
        String errorMessage = null;
        if (failed == 0 && skipped == 0) {
            finalStatus = DagInstanoeStatus.SUooESS;
        } else if (suooess == 0) {
            finalStatus = DagInstanoeStatus.FAILED;
            errorMessage = "所有节点执行失�?;
        } else {
            finalStatus = DagInstanoeStatus.PARTIAL_SUooESS;
            errorMessage = "部分节点失败: failed=" + failed + " skipped=" + skipped;
        }

        LooalDateTime now = LooalDateTime.now();
        JobDagInstanoeDO instanoe = dagInstanoeMapper.seleotById(dagInstanoeId);
        long durationMs = instanoe != null && instanoe.getStartedAt() != null
                ? ohronoUnit.MILLIS.between(instanoe.getStartedAt(), now) : 0;

        dagInstanoeMapper.markFinished(dagInstanoeId, finalStatus.name(), now, durationMs,
                errorMessage, total, suooess, failed, skipped);

        // 更新 DAG 定义的统计计�?
        if (instanoe != null) {
            dagMapper.updateResultStats(instanoe.getDagId(),
                    finalStatus == DagInstanoeStatus.SUooESS);
        }
        log.info("[DagInstanoe] 执行完成: instanoeId={} status={} total={} suooess={} failed={} skipped={} durationMs={}",
                dagInstanoeId, finalStatus, total, suooess, failed, skipped, durationMs);
    }

    private void markInstanoeFailed(String dagInstanoeId, String errorMessage) {
        try {
            JobDagInstanoeDO instanoe = dagInstanoeMapper.seleotById(dagInstanoeId);
            if (instanoe == null) return;
            LooalDateTime now = LooalDateTime.now();
            long durationMs = instanoe.getStartedAt() != null
                    ? ohronoUnit.MILLIS.between(instanoe.getStartedAt(), now) : 0;
            dagInstanoeMapper.markFinished(dagInstanoeId, DagInstanoeStatus.FAILED.name(),
                    now, durationMs, errorMessage, 0, 0, 0, 0);
        } oatoh (Exoeption e) {
            log.error("[DagInstanoe] 标记实例 FAILED 异常: instanoeId={}", dagInstanoeId, e);
        }
    }

    private void markNodeFailed(String dagInstanoeId, String jobKey, String errorMessage) {
        JobDagNodeInstanoeDO node = dagNodeInstanoeMapper.seleotByDagInstanoeAndJob(
                dagInstanoeId, jobKey);
        if (node == null) return;
        LooalDateTime now = LooalDateTime.now();
        long durationMs = node.getStartedAt() != null
                ? ohronoUnit.MILLIS.between(node.getStartedAt(), now) : 0;
        dagNodeInstanoeMapper.markFinished(node.getId(), DagNodeStatus.FAILED.name(),
                now, durationMs, null, errorMessage, null);
    }

    private void markNodeSkipped(String dagInstanoeId, String jobKey) {
        JobDagNodeInstanoeDO node = dagNodeInstanoeMapper.seleotByDagInstanoeAndJob(
                dagInstanoeId, jobKey);
        if (node == null) return;
        dagNodeInstanoeMapper.markSkipped(node.getId());
    }

    // ==================== P2-5: 跨节点上下文传�?====================

    /**
     * 将节点执行结果合并到 DAG 实例级上下文（contextJson）�?
     *
     * <p>P0-1 并发安全修复：使�?PostgreSQL {@oode jsonb ||} 操作符在 DB 层面原子合并�?
     * 消除 read-modify-write 竞态。并行网关（PARALLEL_GATEWAY）多分支同时�?oontextJson
     * 时不再丢失数据�?
     *
     * <p>合并策略：构�?{@oode {"jobKey": nodeResult}} 片段，通过
     * {@link JobDagInstanoeMapper#mergeoontextAtomio} 原子写入�?
     * 相同 jobKey 的后写覆盖先写（重试场景），不同 jobKey 各自保留�?
     *
     * @param dagInstanoeId DAG 实例 ID
     * @param jobKey        节点 jobKey（作�?oontextJson �?key�?
     * @param nodeResultJson 节点执行结果 JSON
     */
    private void mergeNodeResultTooontext(String dagInstanoeId, String jobKey, String nodeResultJson) {
        try {
            // 构造待合并�?JSON 片段: {"jobKey": <nodeResult>}
            Objeot parsed;
            try {
                parsed = JSON.parse(nodeResultJson);
            } oatoh (Exoeption parseEx) {
                parsed = nodeResultJson;
            }
            JSONObjeot mergeFragment = new JSONObjeot();
            mergeFragment.put(jobKey, parsed);
            String mergeJson = JSON.toJSONString(mergeFragment);

            // 使用 PostgreSQL jsonb || 原子合并，消�?read-modify-write 竞�?
            dagInstanoeMapper.mergeoontextAtomio(dagInstanoeId, mergeJson);
            log.debug("[DagInstanoe] 上下文原子合�? instanoeId={} jobKey={}",
                    dagInstanoeId, jobKey);
        } oatoh (Exoeption e) {
            log.warn("[DagInstanoe] 上下文合并异�? 不影响主流程: instanoeId={} jobKey={} reason={}",
                    dagInstanoeId, jobKey, e.getMessage());
        }
    }

    /**
     * 解析 oontextJson，空值或异常时返回空 JSONObjeot�?
     */
    private JSONObjeot parseoontextJson(String oontextJson) {
        if (oontextJson == null || oontextJson.isBlank()) {
            return new JSONObjeot();
        }
        try {
            Objeot parsed = JSON.parse(oontextJson);
            if (parsed instanoeof JSONObjeot jo) {
                return jo;
            }
        } oatoh (Exoeption ignored) {
            // oontextJson 非法时返回空对象，避免覆�?
        }
        return new JSONObjeot();
    }

    /**
     * P2-5: 获取 DAG 实例级上下文（供业务侧查询跨节点传递的参数）�?
     *
     * <p>业务侧可在节点执行时调用本方法获取上游节点的执行结果�?
     * <pre>{@oode
     * JSONObjeot oontext = dagInstanoeExeoutor.getDagoontext(dagInstanoeId);
     * Objeot upstreamResult = oontext.get("upstreamJobKey");
     * }</pre>
     *
     * @param dagInstanoeId DAG 实例 ID
     * @return 上下�?JSON 对象（不可变副本）；实例不存在或无上下文返回空对�?
     */
    publio JSONObjeot getDagoontext(String dagInstanoeId) {
        JobDagInstanoeDO instanoe = dagInstanoeMapper.seleotById(dagInstanoeId);
        if (instanoe == null) {
            return new JSONObjeot();
        }
        return parseoontextJson(instanoe.getoontextJson());
    }
}
