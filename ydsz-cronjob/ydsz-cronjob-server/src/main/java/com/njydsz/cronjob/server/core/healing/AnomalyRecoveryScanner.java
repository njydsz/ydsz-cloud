package com.njydsz.cronjob.server.core.healing;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.domain.vo.JobNodeVO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.config.AnomalyRecoveryConfig;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.LockKeyUtil;
import com.njydsz.cronjob.server.core.alert.AlertContext;
import com.njydsz.cronjob.server.core.alert.AlertTrigger;
import com.njydsz.cronjob.server.core.alert.AlertType;
import com.njydsz.cronjob.server.core.discovery.NodeDiscoveryStrategy;
import com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher;
import com.njydsz.cronjob.server.core.dispatch.TaskDispatcher;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * 异常修复统一扫描器（P1-4）。
 *
 * <p>合并原 FailoverScanner 与 SelfHealingScanner 的能力，统一处理三类异常任务：
 *
 * <ul>
 *   <li><b>离线节点任务恢复</b>：检测下线节点上的 RUNNING 任务并重新派发
 *   <li><b>卡死任务修复</b>：修复 RUNNING 状态超过阈值的任务
 *   <li><b>AUTO_PAUSED 恢复</b>：到达恢复时间后自动恢复为 NORMAL
 * </ul>
 *
 * <p>仅在 Leader 节点执行扫描，避免多节点重复修复。各子流程独立开关控制：
 * <ul>
 *   <li>故障转移：{@code ydsz.cronjob.anomaly-recovery.failover-enabled=true}
 *   <li>卡死修复：{@code ydsz.cronjob.anomaly-recovery.self-healing-enabled=true}
 * </ul>
 *
 * <h3>对标</h3>
 *
 * <p>对标 XXL-Job 的失败重试 + 分片任务转移、PowerJob 的自愈能力、SchedulerX 的自动恢复机制。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class AnomalyRecoveryScanner {

  private final JobLogRepository jobLogRepository;
  private final JobRepository jobRepository;
  private final TaskDispatcher taskDispatcher;
  private final LeaderElector leaderElector;
  private final CronjobProperties cronjobProperties;
  private final RedisTemplate<String, Object> redisTemplate;
  private final RedisStringOps redisStringOps;

  /** P1-1: 节点发现策略（可选注入，Nacos/DB 模式统一抽象） */
  private final ObjectProvider<NodeDiscoveryStrategy> nodeDiscoveryStrategyProvider;

  /** P5: 告警触发器（可选注入） */
  private final ObjectProvider<AlertTrigger> alertTriggerProvider;

  /** P6-2: Prometheus 指标收集器（可选注入） */
  private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

  /** Lua 脚本: 安全释放锁 */
  private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT;

  /** 自愈重试计数 Redis key 前缀 */
  private static final String HEAL_RETRY_PREFIX = "ydsz:job:heal:retry:";

  static {
    RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>();
    RELEASE_LOCK_SCRIPT.setScriptText(LockKeyUtil.RELEASE_LOCK_SCRIPT);
    RELEASE_LOCK_SCRIPT.setResultType(Long.class);
  }

  private String leaderRole;

  /**
   * 初始化异常修复扫描器：缓存 Leader 角色并打印生效中的配置参数。
   *
   * <p>{@code leaderRole} 一次性读入供后续每次扫描判定身份，避免高频穿透配置对象。
   */
  @PostConstruct
  public void init() {
    this.leaderRole = cronjobProperties.getLeader().getRole();
    AnomalyRecoveryConfig config = cronjobProperties.getAnomalyRecovery();
    if (cronjobProperties.getLeader().isEnabled()) {
      log.info(
          "[AnomalyRecovery] 初始化完成, role={} scanInterval={}s stuckThreshold={}s "
              + "failoverEnabled={} selfHealingEnabled={}",
          leaderRole,
          config.getScanIntervalSeconds(),
          config.getStuckThresholdSeconds(),
          config.isFailoverEnabled(),
          config.isSelfHealingEnabled());
    } else {
      log.info("[AnomalyRecovery] leader.enabled=false, 异常修复扫描不启用");
    }
  }

  /**
   * 定时扫描异常任务（默认 30s 一次）。
   *
   * <p>依次执行三组修复流程：离线节点恢复 → 卡死任务修复 → AUTO_PAUSED 恢复。
   * 各流程内部独立限流与容错，单流程异常不影响其他流程。
   */
  @DistributedScheduled(lockKey = "cronjob:anomaly-recovery")
  @Scheduled(fixedDelayString = "${ydsz.cronjob.anomaly-recovery.scan-interval-ms:30000}")
  public void scan() {
    if (!cronjobProperties.getLeader().isEnabled()) {
      return;
    }
    if (!leaderElector.isLeader(leaderRole)) {
      return;
    }
    AnomalyRecoveryConfig config = cronjobProperties.getAnomalyRecovery();
    try {
      // 1. 离线节点任务恢复
      if (config.isFailoverEnabled()) {
        scanOfflineNodeTasks(config);
      }
      // 2. 卡死任务修复 + 3. AUTO_PAUSED 恢复
      if (config.isSelfHealingEnabled()) {
        scanStuckTasks(config);
        healAutoPausedTasks(config);
      }
    } catch (Exception e) {
      log.error("[AnomalyRecovery] 扫描异常: role={} reason={}", leaderRole, e.getMessage(), e);
    }
  }

  /**
   * P2-P6: 立即触发一次异常扫描（供节点变更事件驱动）。
   *
   * <p>由 {@code ShardRebalanceListener} 在检测到节点下线时调用，立即恢复下线节点的 RUNNING 任务，
   * 无需等待下一个 30s 扫描周期。内部仍复用 {@link #scan()} 的 Leader 校验与分布式锁语义，
   * 多节点并发触发时仅一个节点实际执行。
   */
  public void scanImmediately() {
    scan();
  }

  // ==================== 离线节点任务恢复（原 FailoverScanner） ====================

  /**
   * 扫描下线节点上的 RUNNING 任务并执行故障转移。
   *
   * <p>通过 NodeDiscoveryStrategy 获取在线节点列表，识别有 RUNNING 任务但已下线的节点，
   * 释放锁 → 标记 FAILED → 重新派发。
   *
   * @param config 异常修复配置
   */
  private void scanOfflineNodeTasks(AnomalyRecoveryConfig config) {
    NodeDiscoveryStrategy strategy = nodeDiscoveryStrategyProvider.getIfAvailable();
    if (strategy == null) {
      log.debug("[AnomalyRecovery] NodeDiscoveryStrategy 不可用, 跳过离线节点扫描");
      return;
    }

    List<JobNodeVO> onlineNodes;
    try {
      onlineNodes = strategy.getOnlineNodes();
    } catch (Exception e) {
      log.warn("[AnomalyRecovery] 获取在线节点失败, 跳过本次扫描: reason={}", e.getMessage());
      return;
    }
    Set<String> onlineNodeIds =
        onlineNodes.stream()
            .map(JobNodeVO::getNodeId)
            .filter(nodeId -> nodeId != null && !nodeId.isBlank())
            .collect(Collectors.toSet());

    Set<String> runningNodeIds = getRunningNodeIds();
    if (runningNodeIds.isEmpty()) {
      return;
    }

    int scanNodeLimit = config.getScanNodeLimit();
    List<String> offlineNodeIds =
        runningNodeIds.stream()
            .filter(nodeId -> !onlineNodeIds.contains(nodeId))
            .limit(scanNodeLimit)
            .collect(Collectors.toList());

    if (offlineNodeIds.isEmpty()) {
      return;
    }

    log.warn(
        "[AnomalyRecovery] 发现 {} 个下线节点待故障转移: role={} onlineNodes={} runningNodes={}",
        offlineNodeIds.size(),
        leaderRole,
        onlineNodeIds.size(),
        runningNodeIds.size());

    int totalRedispatched = 0;
    for (String nodeId : offlineNodeIds) {
      try {
        totalRedispatched += recoverOfflineNode(nodeId, config);
      } catch (Exception e) {
        log.error(
            "[AnomalyRecovery] 节点故障转移异常: nodeId={} reason={}", nodeId, e.getMessage(), e);
      }
    }

    if (totalRedispatched > 0) {
      log.warn(
          "[AnomalyRecovery] 离线节点扫描完成: role={} offlineNodes={} redispatched={}",
          leaderRole,
          offlineNodeIds.size(),
          totalRedispatched);
    }
  }

  /**
   * 查询所有 RUNNING 状态日志的 exec_node_id（去重）。
   *
   * @return 有 RUNNING 任务的节点 ID 集合；查询异常时返回空集合
   */
  private Set<String> getRunningNodeIds() {
    try {
      List<String> nodeIds = jobLogRepository.findRunningNodeIds();
      if (nodeIds == null || nodeIds.isEmpty()) {
        return Set.of();
      }
      return nodeIds.stream()
          .filter(nodeId -> nodeId != null && !nodeId.isBlank())
          .collect(Collectors.toSet());
    } catch (Exception e) {
      log.warn("[AnomalyRecovery] 查询 RUNNING 任务节点失败: reason={}", e.getMessage());
      return Set.of();
    }
  }

  /**
   * 对单个下线节点执行故障转移：释放锁 → 标记 FAILED → 重新派发。
   *
   * @param nodeId 下线节点 ID
   * @param config 异常修复配置
   * @return 成功重新派发的任务数
   */
  private int recoverOfflineNode(String nodeId, AnomalyRecoveryConfig config) {
    LocalDateTime now = LocalDateTime.now();
    List<JobLogVO> runningLogs = jobLogRepository.findRunningByNode(nodeId);
    if (runningLogs.isEmpty()) {
      return 0;
    }

    int taskLimit = config.getFailoverTaskLimit();
    log.warn(
        "[AnomalyRecovery] 节点故障转移开始: nodeId={} runningTasks={} taskLimit={}",
        nodeId,
        runningLogs.size(),
        taskLimit);

    // 1. 先释放死节点持有的任务锁
    int releasedLocks = 0;
    for (JobLogVO logEntry : runningLogs) {
      if (releaseJobLock(logEntry.getJobKey(), logEntry.getShardIndex(), logEntry.getLockHolder())) {
        releasedLocks++;
      }
    }
    if (releasedLocks > 0) {
      log.info(
          "[AnomalyRecovery] 已释放死节点任务锁: nodeId={} releasedLocks={}/{}",
          nodeId,
          releasedLocks,
          runningLogs.size());
    }

    // 2. 标记为 FAILED（批量 CAS 语义）
    int markedFailed = 0;
    try {
      markedFailed = jobLogRepository.markFailedByNodeOffline(nodeId, now);
    } catch (Exception e) {
      log.error(
          "[AnomalyRecovery] 标记节点任务 FAILED 失败: nodeId={} reason={}",
          nodeId,
          e.getMessage(),
          e);
    }

    // 3. 重新派发任务
    int redispatched = 0;
    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    for (JobLogVO logEntry : runningLogs) {
      if (redispatched >= taskLimit) {
        log.warn(
            "[AnomalyRecovery] 达到单节点转移上限 {}, 剩余任务不再派发: nodeId={} total={}",
            taskLimit,
            nodeId,
            runningLogs.size());
        break;
      }
      try {
        JobVO job = jobRepository.findById(logEntry.getJobId()).orElse(null);
        if (job == null) {
          log.debug(
              "[AnomalyRecovery] 任务已删除, 跳过: jobId={} logId={}",
              logEntry.getJobId(),
              logEntry.getId());
          continue;
        }
        if (!"NORMAL".equals(job.getStatus())) {
          log.debug(
              "[AnomalyRecovery] 任务非 NORMAL 状态, 跳过: jobKey={} status={}",
              job.getJobKey(),
              job.getStatus());
          continue;
        }
        String newLogId =
            taskDispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_FAILOVER);
        redispatched++;
        if (metrics != null) {
          metrics.incJobDispatched(DefaultTaskDispatcher.TRIGGER_FAILOVER, "SUCCESS");
        }
        log.info(
            "[AnomalyRecovery] 故障转移派发: jobKey={} oldLogId={} newLogId={} nodeId={}",
            job.getJobKey(),
            logEntry.getId(),
            newLogId,
            nodeId);
      } catch (Exception e) {
        log.error(
            "[AnomalyRecovery] 任务转移失败: logId={} jobKey={} reason={}",
            logEntry.getId(),
            logEntry.getJobKey(),
            e.getMessage(),
            e);
      }
    }

    log.warn(
        "[AnomalyRecovery] 节点故障转移完成: nodeId={} runningTasks={} releasedLocks={} markedFailed={} redispatched={}",
        nodeId,
        runningLogs.size(),
        releasedLocks,
        markedFailed,
        redispatched);
    return redispatched;
  }

  // ==================== 卡死任务修复（原 SelfHealingScanner.healStuckTasks） ====================

  /**
   * 检测并修复卡死任务。
   *
   * <p>RUNNING 状态超过阈值未更新视为卡死（可能因 JVM 崩溃、线程死锁、网络中断导致）。
   */
  private void scanStuckTasks(AnomalyRecoveryConfig config) {
    LocalDateTime threshold =
        LocalDateTime.now().minusSeconds(config.getStuckThresholdSeconds());

    List<JobLogVO> stuckLogs = jobLogRepository.findStuckTasks(threshold, config.getMaxHealPerScan());

    if (stuckLogs.isEmpty()) {
      return;
    }

    log.warn("[AnomalyRecovery] 发现 {} 个卡死任务, 开始修复", stuckLogs.size());
    int healed = 0;
    int failed = 0;
    for (JobLogVO stuckLog : stuckLogs) {
      try {
        healSingleStuckTask(stuckLog, config);
        healed++;
      } catch (Exception e) {
        failed++;
        log.error(
            "[AnomalyRecovery] 修复卡死任务失败: logId={} jobKey={} reason={}",
            stuckLog.getId(),
            stuckLog.getJobKey(),
            e.getMessage(),
            e);
      }
    }
    log.info(
        "[AnomalyRecovery] 卡死任务修复完成: total={} healed={} failed={}",
        stuckLogs.size(),
        healed,
        failed);
  }

  /**
   * 修复单个卡死任务。
   *
   * <p>流程：CAS 标记 FAILED → 释放锁 → 更新统计 → 重新派发 → 触发告警。
   *
   * @param stuckLog 卡死任务日志
   * @param config 异常修复配置
   */
  private void healSingleStuckTask(JobLogVO stuckLog, AnomalyRecoveryConfig config) {
    LocalDateTime now = LocalDateTime.now();
    long durationMs = Duration.between(stuckLog.getStartTime(), now).toMillis();
    String errorMsg =
        "Self-healing: task stuck (start="
            + stuckLog.getStartTime()
            + ", detected="
            + now
            + ", duration="
            + durationMs
            + "ms)";

    // 1. CAS 标记日志为 FAILED
    int affected = jobLogRepository.markTimeout(stuckLog.getId(), now, durationMs, errorMsg);
    if (affected == 0) {
      log.debug("[AnomalyRecovery] 日志已非 RUNNING 状态, 跳过: logId={}", stuckLog.getId());
      return;
    }

    // 2. 释放分布式锁
    releaseJobLock(stuckLog.getJobKey(), stuckLog.getShardIndex(), stuckLog.getLockHolder());

    // 3. 更新任务统计
    try {
      jobRepository.updateStats(
          stuckLog.getJobId(), stuckLog.getStartTime(), null, null, 0L, 1L, "ERROR");
    } catch (Exception e) {
      log.warn(
          "[AnomalyRecovery] 更新任务统计失败: jobId={} reason={}",
          stuckLog.getJobId(),
          e.getMessage());
    }

    // 4. 记录指标
    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.incJobTimeout(stuckLog.getJobKey());
    }

    // 5. 判断是否重新派发
    if (config.isAutoRedispatch()) {
      tryRedispatch(stuckLog, config);
    }

    // 6. 触发告警
    triggerRecoveryAlert(stuckLog, durationMs);
  }

  /**
   * 尝试重新派发修复后的任务。
   *
   * <p>使用 Redis 计数限制重试次数，超限后标记 AUTO_PAUSED。
   *
   * @param stuckLog 卡死任务日志
   * @param config 异常修复配置
   */
  private void tryRedispatch(JobLogVO stuckLog, AnomalyRecoveryConfig config) {
    String retryKey = HEAL_RETRY_PREFIX + stuckLog.getJobKey();
    try {
      Long retryCount = redisStringOps.incr(retryKey, 1);
      if (retryCount == null) {
        retryCount = 1L;
      }
      // 仅在首次计数时设置过期
      if (retryCount == 1) {
        long ttlSeconds =
            Math.max(
                Duration.ofHours(1).getSeconds(),
                config.getMaxRedispatchRetries() * config.getScanIntervalSeconds() * 2L
                    + Duration.ofHours(1).getSeconds());
        redisStringOps.expire(retryKey, Duration.ofSeconds(ttlSeconds));
      }

      if (retryCount > config.getMaxRedispatchRetries()) {
        log.warn(
            "[AnomalyRecovery] 任务重试次数超限, 标记 AUTO_PAUSED: jobKey={} retries={}",
            stuckLog.getJobKey(),
            retryCount);
        jobRepository.markAutoPaused(stuckLog.getJobId());
        return;
      }

      // 查询任务定义，确认仍为 NORMAL 状态
      JobVO job = jobRepository.findById(stuckLog.getJobId()).orElse(null);
      if (job == null || !"NORMAL".equals(job.getStatus())) {
        log.debug(
            "[AnomalyRecovery] 任务非 NORMAL 状态, 跳过重派: jobKey={} status={}",
            stuckLog.getJobKey(),
            job != null ? job.getStatus() : "null");
        return;
      }

      // 重新派发
      String logId =
          taskDispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_FAILOVER);
      log.info(
          "[AnomalyRecovery] 任务重新派发成功: jobKey={} retries={} newLogId={}",
          stuckLog.getJobKey(),
          retryCount,
          logId);
    } catch (Exception e) {
      log.warn(
          "[AnomalyRecovery] 重新派发失败: jobKey={} reason={}",
          stuckLog.getJobKey(),
          e.getMessage());
    }
  }

  // ==================== AUTO_PAUSED 恢复（原 SelfHealingScanner.healAutoPausedTasks） ====================

  /**
   * 修复 AUTO_PAUSED 状态的任务（到达恢复时间后自动恢复）。
   *
   * <p>查询 AUTO_PAUSED 状态且 lastFireTime 超过 1 小时的任务，清除重试计数后恢复为 NORMAL。
   *
   * @param config 异常修复配置
   */
  private void healAutoPausedTasks(AnomalyRecoveryConfig config) {
    // 查找 AUTO_PAUSED 状态且 lastFireTime 超过 1 小时的任务
    LocalDateTime threshold = LocalDateTime.now().minusHours(1);
    List<JobVO> autoPausedJobs = jobRepository.findAutoResumeCandidates(threshold);

    if (autoPausedJobs.isEmpty()) {
      return;
    }

    log.info("[AnomalyRecovery] 发现 {} 个 AUTO_PAUSED 任务待恢复", autoPausedJobs.size());
    for (JobVO job : autoPausedJobs) {
      try {
        // 清除重试计数
        redisStringOps.del(HEAL_RETRY_PREFIX + job.getJobKey());
        // 恢复为 NORMAL
        jobRepository.resumeAutoPaused(job.getId());
        log.info("[AnomalyRecovery] 任务已自动恢复: jobKey={}", job.getJobKey());
      } catch (Exception e) {
        log.warn(
            "[AnomalyRecovery] 恢复任务失败: jobKey={} reason={}",
            job.getJobKey(),
            e.getMessage());
      }
    }
  }

  // ==================== 共享工具方法 ====================

  /**
   * 安全释放任务锁。
   *
   * <p>使用 Lua 脚本保证"检查 lockHolder 匹配 + delete"的原子性，避免误删其他节点持有的锁。
   * 分片任务使用 {@code :shard:{shardIndex}} 后缀的锁 key。
   *
   * @param jobKey 任务 key
   * @param shardIndex 分片索引（null 或负数表示非分片任务）
   * @param lockHolder 锁持有者标识
   * @return true 表示锁释放成功
   */
  private boolean releaseJobLock(String jobKey, Integer shardIndex, String lockHolder) {
    if (lockHolder == null || lockHolder.isBlank()) {
      return false;
    }
    try {
      String lockKey = LockKeyUtil.buildJobLockKey(jobKey, shardIndex);
      Long released =
          redisTemplate.execute(
              RELEASE_LOCK_SCRIPT, List.of(lockKey), lockHolder);
      if (released != null && released > 0) {
        log.info(
            "[AnomalyRecovery] 释放任务锁成功: jobKey={} shardIndex={} lockKey={}",
            jobKey,
            shardIndex,
            lockKey);
        return true;
      }
      log.debug(
          "[AnomalyRecovery] 锁 holder 不匹配或已过期, 跳过释放: lockKey={} holder={}",
          lockKey,
          lockHolder);
      return false;
    } catch (Exception e) {
      log.warn(
          "[AnomalyRecovery] 释放锁失败(将等待 TTL 自动过期): jobKey={} reason={}",
          jobKey,
          e.getMessage());
      return false;
    }
  }

  /**
   * 触发修复告警。
   *
   * @param stuckLog 被修复的日志记录 VO
   * @param durationMs 卡死持续时间（毫秒）
   */
  private void triggerRecoveryAlert(JobLogVO stuckLog, long durationMs) {
    AlertTrigger trigger = alertTriggerProvider.getIfAvailable();
    if (trigger == null) {
      return;
    }
    try {
      AlertContext context =
          AlertContext.of(
              AlertType.TIMEOUT,
              stuckLog.getJobId(),
              stuckLog.getJobKey(),
              null,
              stuckLog.getId(),
              String.valueOf(durationMs),
              "Self-healing: task stuck and auto-recovered",
              stuckLog.getTraceId(),
              null);
      trigger.trigger(context);
    } catch (Exception e) {
      log.warn(
          "[AnomalyRecovery] 触发告警失败(不影响主流程): logId={} reason={}",
          stuckLog.getId(),
          e.getMessage());
    }
  }
}
