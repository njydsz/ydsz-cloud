package com.njydsz.pmis.cronjob.server.core.dispatch;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.cronjob.domain.entity.job.JobDO;
import com.njydsz.pmis.cronjob.domain.entity.job.JobNodeDO;
import com.njydsz.pmis.cronjob.domain.entity.log.JobLogDO;
import com.njydsz.pmis.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.pmis.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.pmis.cronjob.server.config.CronjobProperties;
import com.njydsz.pmis.cronjob.server.core.LockKeyUtil;
import com.njydsz.pmis.cronjob.server.core.discovery.NodeDiscoveryStrategy;
import com.njydsz.pmis.cronjob.server.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.server.metrics.CronjobMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 失败自动转移扫描器（P1-4）。
 *
 * <p>仅当 {@code pmis.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。
 * 定时（默认 30s）扫描已下线执行器节点上的 RUNNING 任务日志，
 * 标记为 FAILED 后以 triggerType=FAILOVER 重新派发任务，避免任务因节点宕机而永久卡死。
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>检查 Leader 身份（非 Leader 节点直接返回，避免重复扫描）</li>
 *   <li>获取在线节点列表（通过 {@link NodeDiscoveryStrategy}，兼容 Nacos / DB 模式）</li>
 *   <li>查询所有 RUNNING 日志的 exec_node_id，找出不在在线列表中的下线节点</li>
 *   <li>对每个下线节点：
 *     <ul>
 *       <li>调用 {@code selectRunningByNode} 获取 RUNNING 日志</li>
 *       <li>调用 {@code markFailedByNodeOffline} 标记为 FAILED</li>
 *       <li>对每条失败日志，查询对应的 JobDO，若任务仍为 NORMAL 状态，
 *           调用 {@code taskDispatcher.dispatch(job, null, FAILOVER)} 重新派发</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>Leader 独占</b>：通过 {@link LeaderElector#isLeader(String)} 判定，
 *       避免多实例重复扫描与重复派发</li>
 *   <li><b>节点发现抽象</b>：通过 {@link NodeDiscoveryStrategy} 统一获取在线节点，
 *       Nacos 模式对比 Nacos 实例列表，DB 模式对比 pmis_job_node 心跳表</li>
 *   <li><b>容错</b>：单条任务转移失败不影响其他任务；外层 try-catch 兜底</li>
 *   <li><b>限流</b>：单批最多扫描 {@code scanNodeLimit} 个节点，
 *       单节点最多转移 {@code failoverTaskLimit} 个任务，避免雪崩</li>
 *   <li><b>幂等</b>：{@code markFailedByNodeOffline} 使用 CAS 语义
 *       （WHERE status='RUNNING'），重复扫描不会重复标记</li>
 * </ul>
 *
 * <h3>与 JobNodeReaper 的关系</h3>
 * <p>{@link com.njydsz.pmis.cronjob.server.core.executor.JobNodeReaper} 负责 DB 模式下的
 * 节点状态回收（标记 OFFLINE + 物理删除过期记录），其 P1-3 故障转移逻辑仅释放锁和标记 FAILED，
 * 不重新派发任务。本扫描器专注于"标记 FAILED + 重新派发"，二者职责互补：
 * <ul>
 *   <li>FailoverScanner（30s）：快速发现下线节点并重新派发任务，减少任务延迟</li>
 *   <li>JobNodeReaper（5min）：清理节点状态，避免 pmis_job_node 表膨胀</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class FailoverScanner {

    private final JobLogMapper jobLogMapper;
    private final JobMapper jobMapper;
    private final TaskDispatcher taskDispatcher;
    private final LeaderElector leaderElector;
    private final CronjobProperties cronjobProperties;
    /** P1-1: 节点发现策略（可选注入，Nacos/DB 模式统一抽象） */
    private final ObjectProvider<NodeDiscoveryStrategy> nodeDiscoveryStrategyProvider;
    /** P6-2: Prometheus 指标收集器（可选注入，未配置时不记录指标） */
    private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;
    /** P0-10: Redis 模板，用于故障转移时安全释放死节点持有的任务锁 */
    private final StringRedisTemplate redisTemplate;

    /** P0-10: Lua 脚本: 安全释放锁（仅当 value 匹配时才 delete），避免误删其他节点持有的锁 */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT;

    static {
        RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>();
        RELEASE_LOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
        RELEASE_LOCK_SCRIPT.setResultType(Long.class);
    }

    private String leaderRole;

    @PostConstruct
    public void init() {
        this.leaderRole = cronjobProperties.getLeader().getRole();
        if (cronjobProperties.getLeader().isEnabled()) {
            log.info("[FailoverScanner] 初始化完成, role={} scanInterval={}s scanNodeLimit={} taskLimit={}",
                    leaderRole,
                    cronjobProperties.getFailover().getScanIntervalSeconds(),
                    cronjobProperties.getFailover().getScanNodeLimit(),
                    cronjobProperties.getFailover().getFailoverTaskLimit());
        } else {
            log.info("[FailoverScanner] leader.enabled=false, 故障转移扫描不启用");
        }
    }

    /**
     * 定时扫描下线节点上的 RUNNING 任务并执行故障转移（默认 30s 一次）。
     *
     * <p>使用 {@code fixedDelayString} 而非 {@code fixedRateString}，
     * 避免上次扫描耗时较长时任务堆积。
     * 配置项 {@code pmis.cronjob.failover.scan-interval-seconds} 为秒数，
     * 拼接 "000" 转换为毫秒供 Spring 解析。
     */
    @Scheduled(fixedDelayString = "${pmis.cronjob.failover.scan-interval-seconds:30}000")
    public void scan() {
        if (!cronjobProperties.getFailover().isEnabled()) {
            return;
        }
        if (!cronjobProperties.getLeader().isEnabled()) {
            return;
        }
        if (!leaderElector.isLeader(leaderRole)) {
            return;
        }
        try {
            doScan();
        } catch (Exception e) {
            log.error("[FailoverScanner] 扫描异常: role={} reason={}", leaderRole, e.getMessage(), e);
        }
    }

    /**
     * 执行一次故障转移扫描。
     */
    private void doScan() {
        NodeDiscoveryStrategy strategy = nodeDiscoveryStrategyProvider.getIfAvailable();
        if (strategy == null) {
            log.debug("[FailoverScanner] NodeDiscoveryStrategy 不可用, 跳过扫描");
            return;
        }

        // 1. 获取在线节点列表（Nacos 模式查 Nacos 实例，DB 模式查 pmis_job_node 心跳表）
        List<JobNodeDO> onlineNodes;
        try {
            onlineNodes = strategy.getOnlineNodes();
        } catch (Exception e) {
            log.warn("[FailoverScanner] 获取在线节点失败, 跳过本次扫描: reason={}", e.getMessage());
            return;
        }
        Set<String> onlineNodeIds = onlineNodes.stream()
                .map(JobNodeDO::getNodeId)
                .filter(nodeId -> nodeId != null && !nodeId.isBlank())
                .collect(Collectors.toSet());

        // 2. 查询所有 RUNNING 任务的 exec_node_id
        Set<String> runningNodeIds = getRunningNodeIds();
        if (runningNodeIds.isEmpty()) {
            return;
        }

        // 3. 找出下线节点：有 RUNNING 任务但不在在线列表中
        int scanNodeLimit = cronjobProperties.getFailover().getScanNodeLimit();
        List<String> offlineNodeIds = runningNodeIds.stream()
                .filter(nodeId -> !onlineNodeIds.contains(nodeId))
                .limit(scanNodeLimit)
                .collect(Collectors.toList());

        if (offlineNodeIds.isEmpty()) {
            return;
        }

        log.warn("[FailoverScanner] 发现 {} 个下线节点待故障转移: role={} onlineNodes={} runningNodes={}",
                offlineNodeIds.size(), leaderRole, onlineNodeIds.size(), runningNodeIds.size());

        // 4. 对每个下线节点执行故障转移
        int totalRedispatched = 0;
        for (String nodeId : offlineNodeIds) {
            try {
                totalRedispatched += failoverNode(nodeId);
            } catch (Exception e) {
                log.error("[FailoverScanner] 节点故障转移异常: nodeId={} reason={}",
                        nodeId, e.getMessage(), e);
            }
        }

        if (totalRedispatched > 0) {
            log.warn("[FailoverScanner] 扫描完成: role={} offlineNodes={} redispatched={}",
                    leaderRole, offlineNodeIds.size(), totalRedispatched);
        }
    }

    /**
     * 查询所有 RUNNING 状态日志的 exec_node_id（去重）。
     *
     * <p>调用 {@link JobLogMapper#selectRunningNodeIds()} 获取去重后的节点 ID 列表，
     * 避免 MyBatis Plus LambdaQueryWrapper 在无 Spring 上下文环境下（如单元测试）的 lambda 缓存问题。
     *
     * @return 有 RUNNING 任务的节点 ID 集合；查询异常时返回空集合
     */
    private Set<String> getRunningNodeIds() {
        try {
            List<String> nodeIds = jobLogMapper.selectRunningNodeIds();
            if (nodeIds == null || nodeIds.isEmpty()) {
                return Collections.emptySet();
            }
            return nodeIds.stream()
                    .filter(nodeId -> nodeId != null && !nodeId.isBlank())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("[FailoverScanner] 查询 RUNNING 任务节点失败: reason={}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 对单个下线节点执行故障转移。
     *
     * <p>流程：
     * <ol>
     *   <li>调用 {@link JobLogMapper#selectRunningByNode(String)} 获取 RUNNING 日志</li>
     *   <li>调用 {@link JobLogMapper#markFailedByNodeOffline(String, LocalDateTime)} 标记为 FAILED</li>
     *   <li>对每条失败日志，查询对应的 JobDO</li>
     *   <li>若任务仍为 NORMAL 状态，调用 {@link TaskDispatcher#dispatch} 重新派发（triggerType=FAILOVER）</li>
     * </ol>
     *
     * <p>容错：单条任务转移失败不影响其他任务（内层 try-catch）。
     *
     * @param nodeId 下线节点 ID
     * @return 成功重新派发的任务数
     */
    private int failoverNode(String nodeId) {
        LocalDateTime now = LocalDateTime.now();
        List<JobLogDO> runningLogs = jobLogMapper.selectRunningByNode(nodeId);
        if (runningLogs.isEmpty()) {
            return 0;
        }

        int taskLimit = cronjobProperties.getFailover().getFailoverTaskLimit();
        log.warn("[FailoverScanner] 节点故障转移开始: nodeId={} runningTasks={} taskLimit={}",
                nodeId, runningLogs.size(), taskLimit);

        // 1. P0-10: 先逐条释放死节点持有的任务锁（必须先于 markFailed，
        //    否则新派发的任务会因旧锁未释放而 tryAcquire 失败，故障转移无效）
        //    使用 Lua 安全释放脚本：仅当 lockHolder 匹配时才 del，避免误删其他节点持有的锁
        int releasedLocks = 0;
        for (JobLogDO logEntry : runningLogs) {
            if (releaseLockSafe(logEntry)) {
                releasedLocks++;
            }
        }
        if (releasedLocks > 0) {
            log.info("[FailoverScanner] 已释放死节点任务锁: nodeId={} releasedLocks={}/{}",
                    nodeId, releasedLocks, runningLogs.size());
        }

        // 2. 标记为 FAILED（批量，CAS 语义仅更新 status='RUNNING' 的记录）
        int markedFailed = 0;
        try {
            markedFailed = jobLogMapper.markFailedByNodeOffline(nodeId, now);
        } catch (Exception e) {
            log.error("[FailoverScanner] 标记节点任务 FAILED 失败: nodeId={} reason={}",
                    nodeId, e.getMessage(), e);
            // 标记失败仍尝试重新派发（日志状态可能已被其他流程标记）
        }

        // 3. 重新派发任务
        int redispatched = 0;
        CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
        for (JobLogDO logEntry : runningLogs) {
            if (redispatched >= taskLimit) {
                log.warn("[FailoverScanner] 达到单节点转移上限 {}, 剩余任务不再派发: nodeId={} total={}",
                        taskLimit, nodeId, runningLogs.size());
                break;
            }
            try {
                JobDO job = jobMapper.selectById(logEntry.getJobId());
                if (job == null) {
                    log.debug("[FailoverScanner] 任务已删除, 跳过: jobId={} logId={}",
                            logEntry.getJobId(), logEntry.getId());
                    continue;
                }
                if (!"NORMAL".equals(job.getStatus())) {
                    log.debug("[FailoverScanner] 任务非 NORMAL 状态, 跳过: jobKey={} status={}",
                            job.getJobKey(), job.getStatus());
                    continue;
                }
                String newLogId = taskDispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_FAILOVER);
                redispatched++;
                // P6-2: 记录故障转移派发指标
                if (metrics != null) {
                    metrics.incJobDispatched(DefaultTaskDispatcher.TRIGGER_FAILOVER, "SUCCESS");
                }
                log.info("[FailoverScanner] 故障转移派发: jobKey={} oldLogId={} newLogId={} nodeId={}",
                        job.getJobKey(), logEntry.getId(), newLogId, nodeId);
            } catch (Exception e) {
                log.error("[FailoverScanner] 任务转移失败: logId={} jobKey={} reason={}",
                        logEntry.getId(), logEntry.getJobKey(), e.getMessage(), e);
            }
        }

        log.warn("[FailoverScanner] 节点故障转移完成: nodeId={} runningTasks={} releasedLocks={} markedFailed={} redispatched={}",
                nodeId, runningLogs.size(), releasedLocks, markedFailed, redispatched);
        return redispatched;
    }

    /**
     * 安全释放死节点持有的任务锁（P0-10）。
     *
     * <p>使用 Lua 脚本保证"检查 lockHolder 匹配 + delete"的原子性，
     * 避免误删其他节点（如新派发任务的执行器）持有的锁。
     *
     * <p>分片任务（shardIndex >= 0）使用 {@code :shard:{shardIndex}} 后缀的锁 key，
     * 通过 {@link LockKeyUtil#buildJobLockKey(String, Integer)} 统一构造。
     *
     * @param logEntry 死节点上的 RUNNING 日志（含 jobKey / shardIndex / lockHolder）
     * @return true 表示锁释放成功；false 表示锁不存在、holder 不匹配或释放异常
     */
    private boolean releaseLockSafe(JobLogDO logEntry) {
        String lockHolder = logEntry.getLockHolder();
        if (lockHolder == null || lockHolder.isBlank()) {
            return false;
        }
        // P0-11: 通过 LockKeyUtil 统一构造 lockKey（含分片感知）
        String lockKey = LockKeyUtil.buildJobLockKey(logEntry.getJobKey(), logEntry.getShardIndex());
        try {
            Long result = redisTemplate.execute(RELEASE_LOCK_SCRIPT,
                    Collections.singletonList(lockKey), lockHolder);
            if (result != null && result > 0) {
                log.debug("[FailoverScanner] 释放死节点锁成功: lockKey={} holder={}",
                        lockKey, lockHolder);
                return true;
            }
            log.debug("[FailoverScanner] 锁 holder 不匹配或已过期, 跳过释放: lockKey={} holder={}",
                    lockKey, lockHolder);
            return false;
        } catch (Exception e) {
            log.warn("[FailoverScanner] 释放锁失败(将等待 TTL 自动过期): lockKey={} reason={}",
                    lockKey, e.getMessage());
            return false;
        }
    }
}
