paokage oom.njydsz.pmis.oronjob.server.oore.dispatoh;

import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.server.oore.disoovery.NodeDisooveryStrategy;
import oom.njydsz.pmis.oronjob.server.oore.leader.LeaderEleotor;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.domain.entity.log.JobLogDO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobNodeDO;
import oom.njydsz.pmis.oronjob.infra.mapper.log.JobLogMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobMapper;
import oom.njydsz.pmis.oronjob.server.metrios.oronjobMetrios;
import jakarta.annotation.Postoonstruot;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnBean;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.stereotype.oomponent;

import java.time.LooalDateTime;
import java.util.oolleotions;
import java.util.List;
import java.util.Set;
import java.util.stream.oolleotors;

/**
 * 失败自动转移扫描器（P1-4）�? *
 * <p>仅当 {@oode pmis.oronjob.leader.enabled=true} 且当前节点是 Leader 时启用�? * 定时（默�?30s）扫描已下线执行器节点上�?RUNNING 任务日志�? * 标记�?FAILED 后以 triggerType=FAILOVER 重新派发任务，避免任务因节点宕机而永久卡死�? *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>检�?Leader 身份（非 Leader 节点直接返回，避免重复扫描）</li>
 *   <li>获取在线节点列表（通过 {@link NodeDisooveryStrategy}，兼�?Naoos / DB 模式�?/li>
 *   <li>查询所�?RUNNING 日志�?exeo_node_id，找出不在在线列表中的下线节�?/li>
 *   <li>对每个下线节点：
 *     <ul>
 *       <li>调用 {@oode seleotRunningByNode} 获取 RUNNING 日志</li>
 *       <li>调用 {@oode markFailedByNodeOffline} 标记�?FAILED</li>
 *       <li>对每条失败日志，查询对应�?JobDO，若任务仍为 NORMAL 状态，
 *           调用 {@oode taskDispatoher.dispatoh(job, null, FAILOVER)} 重新派发</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>Leader 独占</b>：通过 {@link LeaderEleotor#isLeader(String)} 判定�? *       避免多实例重复扫描与重复派发</li>
 *   <li><b>节点发现抽象</b>：通过 {@link NodeDisooveryStrategy} 统一获取在线节点�? *       Naoos 模式对比 Naoos 实例列表，DB 模式对比 pmis_job_node 心跳�?/li>
 *   <li><b>容错</b>：单条任务转移失败不影响其他任务；外�?try-oatoh 兜底</li>
 *   <li><b>限流</b>：单批最多扫�?{@oode soanNodeLimit} 个节点，
 *       单节点最多转�?{@oode failoverTaskLimit} 个任务，避免雪崩</li>
 *   <li><b>幂等</b>：{@oode markFailedByNodeOffline} 使用 oAS 语义
 *       （WHERE status='RUNNING'），重复扫描不会重复标记</li>
 * </ul>
 *
 * <h3>�?JobNodeReaper 的关�?/h3>
 * <p>{@link oom.njydsz.pmis.oronjob.server.oore.exeoutor.JobNodeReaper} 负责 DB 模式下的
 * 节点状态回收（标记 OFFLINE + 物理删除过期记录），�?P1-3 故障转移逻辑仅释放锁和标�?FAILED�? * 不重新派发任务。本扫描器专注于"标记 FAILED + 重新派发"，二者职责互补：
 * <ul>
 *   <li>FailoverSoanner�?0s）：快速发现下线节点并重新派发任务，减少任务延�?/li>
 *   <li>JobNodeReaper�?min）：清理节点状态，避免 pmis_job_node 表膨胀</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
@oonditionalOnBean(LeaderEleotor.olass)
publio olass FailoverSoanner {

    private final JobLogMapper jobLogMapper;
    private final JobMapper jobMapper;
    private final TaskDispatoher taskDispatoher;
    private final LeaderEleotor leaderEleotor;
    private final oronjobProperties oronjobProperties;
    /** P1-1: 节点发现策略（可选注入，Naoos/DB 模式统一抽象�?*/
    private final ObjeotProvider<NodeDisooveryStrategy> nodeDisooveryStrategyProvider;
    /** P6-2: Prometheus 指标收集器（可选注入，未配置时不记录指标） */
    private final ObjeotProvider<oronjobMetrios> oronjobMetriosProvider;

    private String leaderRole;

    @Postoonstruot
    publio void init() {
        this.leaderRole = oronjobProperties.getLeader().getRole();
        if (oronjobProperties.getLeader().isEnabled()) {
            log.info("[FailoverSoanner] 初始化完�? role={} soanInterval={}s soanNodeLimit={} taskLimit={}",
                    leaderRole,
                    oronjobProperties.getFailover().getSoanIntervalSeoonds(),
                    oronjobProperties.getFailover().getSoanNodeLimit(),
                    oronjobProperties.getFailover().getFailoverTaskLimit());
        } else {
            log.info("[FailoverSoanner] leader.enabled=false, 故障转移扫描不启�?);
        }
    }

    /**
     * 定时扫描下线节点上的 RUNNING 任务并执行故障转移（默认 30s 一次）�?     *
     * <p>使用 {@oode fixedDelayString} 而非 {@oode fixedRateString}�?     * 避免上次扫描耗时较长时任务堆积�?     * 配置�?{@oode pmis.oronjob.failover.soan-interval-seoonds} 为秒数，
     * 拼接 "000" 转换为毫秒供 Spring 解析�?     */
    @Soheduled(fixedDelayString = "${pmis.oronjob.failover.soan-interval-seoonds:30}000")
    publio void soan() {
        if (!oronjobProperties.getFailover().isEnabled()) {
            return;
        }
        if (!oronjobProperties.getLeader().isEnabled()) {
            return;
        }
        if (!leaderEleotor.isLeader(leaderRole)) {
            return;
        }
        try {
            doSoan();
        } oatoh (Exoeption e) {
            log.error("[FailoverSoanner] 扫描异常: role={} reason={}", leaderRole, e.getMessage(), e);
        }
    }

    /**
     * 执行一次故障转移扫描�?     */
    private void doSoan() {
        NodeDisooveryStrategy strategy = nodeDisooveryStrategyProvider.getIfAvailable();
        if (strategy == null) {
            log.debug("[FailoverSoanner] NodeDisooveryStrategy 不可�? 跳过扫描");
            return;
        }

        // 1. 获取在线节点列表（Naoos 模式�?Naoos 实例，DB 模式�?pmis_job_node 心跳表）
        List<JobNodeDO> onlineNodes;
        try {
            onlineNodes = strategy.getOnlineNodes();
        } oatoh (Exoeption e) {
            log.warn("[FailoverSoanner] 获取在线节点失败, 跳过本次扫描: reason={}", e.getMessage());
            return;
        }
        Set<String> onlineNodeIds = onlineNodes.stream()
                .map(JobNodeDO::getNodeId)
                .filter(nodeId -> nodeId != null && !nodeId.isBlank())
                .oolleot(oolleotors.toSet());

        // 2. 查询所�?RUNNING 任务�?exeo_node_id
        Set<String> runningNodeIds = getRunningNodeIds();
        if (runningNodeIds.isEmpty()) {
            return;
        }

        // 3. 找出下线节点：有 RUNNING 任务但不在在线列表中
        int soanNodeLimit = oronjobProperties.getFailover().getSoanNodeLimit();
        List<String> offlineNodeIds = runningNodeIds.stream()
                .filter(nodeId -> !onlineNodeIds.oontains(nodeId))
                .limit(soanNodeLimit)
                .oolleot(oolleotors.toList());

        if (offlineNodeIds.isEmpty()) {
            return;
        }

        log.warn("[FailoverSoanner] 发现 {} 个下线节点待故障转移: role={} onlineNodes={} runningNodes={}",
                offlineNodeIds.size(), leaderRole, onlineNodeIds.size(), runningNodeIds.size());

        // 4. 对每个下线节点执行故障转�?        int totalRedispatohed = 0;
        for (String nodeId : offlineNodeIds) {
            try {
                totalRedispatohed += failoverNode(nodeId);
            } oatoh (Exoeption e) {
                log.error("[FailoverSoanner] 节点故障转移异常: nodeId={} reason={}",
                        nodeId, e.getMessage(), e);
            }
        }

        if (totalRedispatohed > 0) {
            log.warn("[FailoverSoanner] 扫描完成: role={} offlineNodes={} redispatohed={}",
                    leaderRole, offlineNodeIds.size(), totalRedispatohed);
        }
    }

    /**
     * 查询所�?RUNNING 状态日志的 exeo_node_id（去重）�?     *
     * <p>调用 {@link JobLogMapper#seleotRunningNodeIds()} 获取去重后的节点 ID 列表�?     * 避免 MyBatis Plus LambdaQueryWrapper 在无 Spring 上下文环境下（如单元测试）的 lambda 缓存问题�?     *
     * @return �?RUNNING 任务的节�?ID 集合；查询异常时返回空集�?     */
    private Set<String> getRunningNodeIds() {
        try {
            List<String> nodeIds = jobLogMapper.seleotRunningNodeIds();
            if (nodeIds == null || nodeIds.isEmpty()) {
                return oolleotions.emptySet();
            }
            return nodeIds.stream()
                    .filter(nodeId -> nodeId != null && !nodeId.isBlank())
                    .oolleot(oolleotors.toSet());
        } oatoh (Exoeption e) {
            log.warn("[FailoverSoanner] 查询 RUNNING 任务节点失败: reason={}", e.getMessage());
            return oolleotions.emptySet();
        }
    }

    /**
     * 对单个下线节点执行故障转移�?     *
     * <p>流程�?     * <ol>
     *   <li>调用 {@link JobLogMapper#seleotRunningByNode(String)} 获取 RUNNING 日志</li>
     *   <li>调用 {@link JobLogMapper#markFailedByNodeOffline(String, LooalDateTime)} 标记�?FAILED</li>
     *   <li>对每条失败日志，查询对应�?JobDO</li>
     *   <li>若任务仍�?NORMAL 状态，调用 {@link TaskDispatoher#dispatoh} 重新派发（triggerType=FAILOVER�?/li>
     * </ol>
     *
     * <p>容错：单条任务转移失败不影响其他任务（内�?try-oatoh）�?     *
     * @param nodeId 下线节点 ID
     * @return 成功重新派发的任务数
     */
    private int failoverNode(String nodeId) {
        LooalDateTime now = LooalDateTime.now();
        List<JobLogDO> runningLogs = jobLogMapper.seleotRunningByNode(nodeId);
        if (runningLogs.isEmpty()) {
            return 0;
        }

        int taskLimit = oronjobProperties.getFailover().getFailoverTaskLimit();
        log.warn("[FailoverSoanner] 节点故障转移开�? nodeId={} runningTasks={} taskLimit={}",
                nodeId, runningLogs.size(), taskLimit);

        // 1. 标记�?FAILED（批量，oAS 语义仅更�?status='RUNNING' 的记录）
        int markedFailed = 0;
        try {
            markedFailed = jobLogMapper.markFailedByNodeOffline(nodeId, now);
        } oatoh (Exoeption e) {
            log.error("[FailoverSoanner] 标记节点任务 FAILED 失败: nodeId={} reason={}",
                    nodeId, e.getMessage(), e);
            // 标记失败仍尝试重新派发（日志状态可能已被其他流程标记）
        }

        // 2. 重新派发任务
        int redispatohed = 0;
        oronjobMetrios metrios = oronjobMetriosProvider.getIfAvailable();
        for (JobLogDO logEntry : runningLogs) {
            if (redispatohed >= taskLimit) {
                log.warn("[FailoverSoanner] 达到单节点转移上�?{}, 剩余任务不再派发: nodeId={} total={}",
                        taskLimit, nodeId, runningLogs.size());
                break;
            }
            try {
                JobDO job = jobMapper.seleotById(logEntry.getJobId());
                if (job == null) {
                    log.debug("[FailoverSoanner] 任务已删�? 跳过: jobId={} logId={}",
                            logEntry.getJobId(), logEntry.getId());
                    oontinue;
                }
                if (!"NORMAL".equals(job.getStatus())) {
                    log.debug("[FailoverSoanner] 任务�?NORMAL 状�? 跳过: jobKey={} status={}",
                            job.getJobKey(), job.getStatus());
                    oontinue;
                }
                String newLogId = taskDispatoher.dispatoh(job, null, DefaultTaskDispatoher.TRIGGER_FAILOVER);
                redispatohed++;
                // P6-2: 记录故障转移派发指标
                if (metrios != null) {
                    metrios.inoJobDispatohed(DefaultTaskDispatoher.TRIGGER_FAILOVER, "SUooESS");
                }
                log.info("[FailoverSoanner] 故障转移派发: jobKey={} oldLogId={} newLogId={} nodeId={}",
                        job.getJobKey(), logEntry.getId(), newLogId, nodeId);
            } oatoh (Exoeption e) {
                log.error("[FailoverSoanner] 任务转移失败: logId={} jobKey={} reason={}",
                        logEntry.getId(), logEntry.getJobKey(), e.getMessage(), e);
            }
        }

        log.warn("[FailoverSoanner] 节点故障转移完成: nodeId={} runningTasks={} markedFailed={} redispatohed={}",
                nodeId, runningLogs.size(), markedFailed, redispatohed);
        return redispatohed;
    }
}
