package com.njydsz.pmis.cronjob.core.dispatch;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.common.job.ShardingContext;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.executor.JobNodeHeartbeat;
import com.njydsz.pmis.cronjob.core.sharding.ShardAssignment;
import com.njydsz.pmis.cronjob.core.sharding.ShardingStrategy;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.entity.JobNodeDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.mapper.JobNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * 默认任务派发器：本地执行 + 分布式锁。
 *
 * <p>P1 阶段实现：Leader 节点扫描到待触发任务后，通过本派发器在本地执行。
 * 远程派发（HTTP/Feign）留作 P3 阶段扩展。
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>抢占分布式锁（任务级 TTL，可选）</li>
 *   <li>写开始日志（pmis_job_log, status=RUNNING）</li>
 *   <li>调用 {@link JobHandler#execute(String)} 执行业务逻辑</li>
 *   <li>更新日志为 SUCCESS/FAILED + 任务统计</li>
 *   <li>释放锁（Lua 脚本安全释放）</li>
 * </ol>
 *
 * <p>与 {@link JobNodeHeartbeat} 联动：执行前后递增/递减 running_count，
 * 用于负载均衡选择。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnMissingBean(TaskDispatcher.class)
public class DefaultTaskDispatcher implements TaskDispatcher {

    private final JobMapper jobMapper;
    private final JobLogMapper jobLogMapper;
    private final ApplicationContext applicationContext;
    private final StringRedisTemplate redisTemplate;
    private final CronjobProperties cronjobProperties;
    private final JobNodeHeartbeat jobNodeHeartbeat;
    /** P3: 节点 Mapper，用于查询在线节点列表做分片分配 */
    private final JobNodeMapper jobNodeMapper;
    /** P3: 分片策略（可选注入，未配置时 fallback 到非分片模式） */
    private final ObjectProvider<ShardingStrategy> shardingStrategyProvider;
    /** P4: 事件发布器，任务完成后发布事件触发后继依赖 */
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /** 任务锁 key 前缀 */
    private static final String JOB_LOCK_PREFIX = "pmis:job:lock:";

    /** 当前实例标识（hostname:pid），用于锁值和安全释放 */
    private static final String INSTANCE_ID = initInstanceId();

    /** Lua 脚本: 安全释放锁（仅当 value 匹配时才 delete） */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = initReleaseScript();

    /** 触发类型常量 */
    public static final String TRIGGER_CRON = "CRON";
    public static final String TRIGGER_MANUAL = "MANUAL";
    public static final String TRIGGER_RETRY = "RETRY";
    public static final String TRIGGER_DEPENDENT = "DEPENDENT";
    /** P2-2: Misfire 触发（合并执行时使用，日志可识别） */
    public static final String TRIGGER_MISFIRED = "MISFIRED";

    @Override
    public String dispatch(JobDO job, String executorNode, String triggerType) {
        // 当前实现：executorNode 参数忽略，始终本地执行（P3 阶段扩展远程派发）
        boolean holdLock = !TRIGGER_MANUAL.equals(triggerType);
        // P3: 分片任务走分片执行路径
        if (isShardedJob(job)) {
            return executeShardedJob(job, holdLock, triggerType);
        }
        return executeJob(job, holdLock, triggerType);
    }

    /**
     * 判定是否为分片任务（P3）。
     *
     * <p>需同时满足：shardTotal > 1 且 ShardingStrategy Bean 可用。
     * 否则 fallback 到非分片模式，保证向后兼容。
     */
    private boolean isShardedJob(JobDO job) {
        Integer total = job.getShardTotal();
        if (total == null || total <= 1) {
            return false;
        }
        return shardingStrategyProvider.getIfAvailable() != null;
    }

    /**
     * 分片任务执行入口（P3）。
     *
     * <p>流程：
     * <ol>
     *   <li>查询在线节点列表（按 nodeId 升序保证确定性）</li>
     *   <li>通过 ShardingStrategy 计算分片分配方案</li>
     *   <li>筛选分配给本地节点的分片（P3 阶段仅本地执行，远程派发留作扩展）</li>
     *   <li>对每个本地分片独立加锁、写日志、执行 handler</li>
     * </ol>
     *
     * @return 第一个成功创建日志的分片 logId；无本地分片或全部被锁返回 null
     */
    private String executeShardedJob(JobDO job, boolean holdLock, String triggerType) {
        int shardTotal = job.getShardTotal();
        ShardingStrategy strategy = shardingStrategyProvider.getIfAvailable();
        if (strategy == null) {
            // 理论上不会走到这里（isShardedJob 已判定），防御性处理
            log.warn("[Dispatcher] ShardingStrategy 不可用, fallback 到非分片模式: key={}", job.getJobKey());
            return executeJob(job, holdLock, triggerType);
        }

        List<String> onlineNodes = getOnlineNodes();
        String localNodeId = jobNodeHeartbeat != null ? jobNodeHeartbeat.getNodeId() : null;

        List<ShardAssignment> assignments;
        if (onlineNodes.isEmpty() || localNodeId == null) {
            // fallback: 无在线节点信息或本地 nodeId 未知, 本地执行全部分片
            assignments = buildLocalOnlyAssignments(shardTotal, localNodeId);
        } else {
            assignments = strategy.assign(shardTotal, onlineNodes);
        }

        List<ShardAssignment> localShards = assignments.stream()
                .filter(a -> localNodeId == null || localNodeId.equals(a.nodeId()))
                .collect(Collectors.toList());

        if (localShards.isEmpty()) {
            log.info("[Dispatcher] 分片任务无本地分片, 跳过: key={} localNode={}",
                    job.getJobKey(), localNodeId);
            return null;
        }

        log.info("[Dispatcher] 分片任务派发: key={} shardTotal={} localShards={}",
                job.getJobKey(), shardTotal, localShards.size());

        String firstLogId = null;
        for (ShardAssignment assignment : localShards) {
            String logId = executeShard(job, assignment.shardIndex(), shardTotal, holdLock, triggerType);
            if (firstLogId == null && logId != null) {
                firstLogId = logId;
            }
        }
        return firstLogId;
    }

    /**
     * 执行单个分片（P3）。
     *
     * <p>与 {@link #executeJob} 类似，区别：
     * <ul>
     *   <li>锁 key 含分片索引: {@code pmis:job:lock:{jobKey}:shard:{shardIndex}}</li>
     *   <li>构造 {@link ShardingContext} 传入 handler</li>
     *   <li>不推进 next_fire_time（由 JobScanner 统一推进）</li>
     * </ul>
     */
    private String executeShard(JobDO job, int shardIndex, int shardTotal,
                                 boolean holdLock, String triggerType) {
        String lockKey = null;
        if (holdLock) {
            lockKey = JOB_LOCK_PREFIX + job.getJobKey() + ":shard:" + shardIndex;
            Duration ttl = resolveLockTtl(job);
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, INSTANCE_ID, ttl);
            if (!Boolean.TRUE.equals(acquired)) {
                log.info("[Dispatcher] 分片锁被其他实例持有, 跳过: key={} shard={}",
                        job.getJobKey(), shardIndex);
                return null;
            }
        }

        if (jobNodeHeartbeat != null) {
            jobNodeHeartbeat.onTaskStart();
        }

        JobLogDO log0 = new JobLogDO();
        log0.setJobId(job.getId());
        log0.setJobKey(job.getJobKey());
        log0.setStartTime(LocalDateTime.now());
        log0.setStatus("RUNNING");
        log0.setParamsJson(job.getParamsJson());
        log0.setTraceId(TraceIdUtil.get());
        log0.setTriggerType(triggerType);
        log0.setCreatedAt(LocalDateTime.now());
        log0.setDeleted(0);
        jobLogMapper.insert(log0);

        boolean success = false;
        Object result = null;
        try {
            JobHandler handler = applicationContext.getBean(job.getHandler(), JobHandler.class);
            ShardingContext ctx = new ShardingContext(shardTotal, shardIndex,
                    Collections.emptyList(), job.getJobKey(), log0.getId());
            result = handler.execute(job.getParamsJson(), ctx);
            success = true;
            log0.setResultJson(result == null ? null : JSON.toJSONString(result));
        } catch (Exception e) {
            log.error("[Dispatcher] 分片任务执行失败: key={} shard={} reason={}",
                    job.getJobKey(), shardIndex, e.getMessage(), e);
            log0.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            log0.setEndTime(LocalDateTime.now());
            log0.setDurationMs(Duration.between(log0.getStartTime(), log0.getEndTime()).toMillis());
            log0.setStatus(success ? "SUCCESS" : "FAILED");
            jobLogMapper.updateById(log0);

            // 分片场景: 只更新统计, 不推进 next_fire_time（由 Scanner 控制）
            Long incFire = 1L;
            Long incSucc = success ? 1L : 0L;
            Long incFail = success ? 0L : 1L;
            jobMapper.updateStats(job.getId(), null, null, incFire, incSucc, incFail,
                    success ? null : "ERROR");

            if (lockKey != null) {
                try {
                    redisTemplate.execute(RELEASE_LOCK_SCRIPT,
                            Collections.singletonList(lockKey), INSTANCE_ID);
                } catch (Exception e) {
                    log.warn("[Dispatcher] 释放分片锁失败(将等待 TTL 自动过期): key={} reason={}",
                            lockKey, e.getMessage());
                }
            }

            if (jobNodeHeartbeat != null) {
                jobNodeHeartbeat.onTaskComplete();
            }
        }
        return log0.getId();
    }

    /**
     * 查询在线节点列表（按 nodeId 升序，保证分片分配确定性）。
     */
    private List<String> getOnlineNodes() {
        try {
            long threshold = cronjobProperties.getExecutor().getOfflineThresholdSeconds();
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(threshold);
            LambdaQueryWrapper<JobNodeDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(JobNodeDO::getStatus, "ONLINE")
                    .ge(JobNodeDO::getLastHeartbeat, cutoff)
                    .orderByAsc(JobNodeDO::getNodeId);
            List<JobNodeDO> nodes = jobNodeMapper.selectList(wrapper);
            return nodes.stream().map(JobNodeDO::getNodeId).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[Dispatcher] 查询在线节点失败, fallback 到本地执行全部分片: reason={}",
                    e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 构建"本地执行全部分片"的分配方案（fallback）。
     */
    private List<ShardAssignment> buildLocalOnlyAssignments(int shardTotal, String localNodeId) {
        List<ShardAssignment> result = new ArrayList<>(shardTotal);
        for (int i = 0; i < shardTotal; i++) {
            result.add(new ShardAssignment(localNodeId, i));
        }
        return result;
    }

    /**
     * 执行任务（核心逻辑，从 JobServiceImpl 抽取）。
     *
     * @param job         任务定义
     * @param holdLock    是否抢占分布式锁
     * @param triggerType 触发类型
     * @return 执行日志 ID；锁被持有时返回 null
     */
    private String executeJob(JobDO job, boolean holdLock, String triggerType) {
        String lockKey = null;
        if (holdLock) {
            lockKey = JOB_LOCK_PREFIX + job.getJobKey();
            Duration ttl = resolveLockTtl(job);
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, INSTANCE_ID, ttl);
            if (!Boolean.TRUE.equals(acquired)) {
                log.info("[Dispatcher] 任务已被其他实例持有锁, 跳过: key={} triggerType={}",
                        job.getJobKey(), triggerType);
                return null;
            }
            log.debug("[Dispatcher] 获取分布式锁成功: key={} holder={} ttl={}ms",
                    lockKey, INSTANCE_ID, ttl.toMillis());
        }

        // 通知心跳组件：任务开始
        if (jobNodeHeartbeat != null) {
            jobNodeHeartbeat.onTaskStart();
        }

        // 写开始日志
        JobLogDO log0 = new JobLogDO();
        log0.setJobId(job.getId());
        log0.setJobKey(job.getJobKey());
        log0.setStartTime(LocalDateTime.now());
        log0.setStatus("RUNNING");
        log0.setParamsJson(job.getParamsJson());
        log0.setTraceId(TraceIdUtil.get());
        log0.setTriggerType(triggerType);
        log0.setCreatedAt(LocalDateTime.now());
        log0.setDeleted(0);
        jobLogMapper.insert(log0);

        boolean success = false;
        Object result = null;
        try {
            JobHandler handler = applicationContext.getBean(job.getHandler(), JobHandler.class);
            result = handler.execute(job.getParamsJson());
            success = true;
            log0.setResultJson(result == null ? null : JSON.toJSONString(result));
        } catch (Exception e) {
            log.error("[Dispatcher] 任务执行失败: key={} handler={} reason={}",
                    job.getJobKey(), job.getHandler(), e.getMessage(), e);
            log0.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            log0.setEndTime(LocalDateTime.now());
            log0.setDurationMs(Duration.between(log0.getStartTime(), log0.getEndTime()).toMillis());
            log0.setStatus(success ? "SUCCESS" : "FAILED");
            jobLogMapper.updateById(log0);

            // 更新任务统计
            Long incFire = 1L;
            Long incSucc = success ? 1L : 0L;
            Long incFail = success ? 0L : 1L;
            LocalDateTime next = TRIGGER_CRON.equals(triggerType)
                    ? nextFireTime(job.getCronExpression())
                    : null;
            jobMapper.updateStats(job.getId(), log0.getStartTime(), next, incFire, incSucc, incFail,
                    success ? null : "ERROR");

            // 释放分布式锁（Lua 脚本安全释放）
            if (lockKey != null) {
                try {
                    redisTemplate.execute(RELEASE_LOCK_SCRIPT,
                            Collections.singletonList(lockKey), INSTANCE_ID);
                } catch (Exception e) {
                    log.warn("[Dispatcher] 释放分布式锁失败(将等待 TTL 自动过期): key={} reason={}",
                            lockKey, e.getMessage());
                }
            }

            // 通知心跳组件：任务结束
            if (jobNodeHeartbeat != null) {
                jobNodeHeartbeat.onTaskComplete();
            }
        }
        // P4: 发布任务完成事件，触发后继依赖任务（DagExecutor 异步监听）
        publishTaskCompleted(job, success, log0.getId());
        return log0.getId();
    }

    /**
     * P4: 发布任务完成事件，触发后继依赖任务。
     *
     * <p>使用 try-catch 包裹，确保事件发布失败不影响主流程。
     */
    private void publishTaskCompleted(JobDO job, boolean success, String logId) {
        try {
            com.njydsz.pmis.cronjob.core.dag.TaskCompletedEvent event =
                    new com.njydsz.pmis.cronjob.core.dag.TaskCompletedEvent(
                            job.getId(), job.getJobKey(), success, logId);
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("[Dispatcher] 发布任务完成事件失败(不影响主流程): key={} reason={}",
                    job.getJobKey(), e.getMessage());
        }
    }

    /**
     * 解析任务实际使用的锁 TTL。
     */
    private Duration resolveLockTtl(JobDO job) {
        Duration taskLevel = null;
        if (job.getLockTtlMs() != null && job.getLockTtlMs() > 0) {
            taskLevel = Duration.ofMillis(job.getLockTtlMs());
        }
        return cronjobProperties.normalizeTtl(taskLevel);
    }

    /**
     * 计算下次触发时间（Asia/Shanghai 时区）。
     */
    private LocalDateTime nextFireTime(String cron) {
        try {
            CronTrigger trigger = new CronTrigger(cron,
                    TimeZone.getTimeZone("Asia/Shanghai"));
            TriggerContext ctx = new SimpleTriggerContext();
            Optional<Instant> next = trigger.nextExecution(ctx);
            return next.map(instant -> LocalDateTime.ofInstant(instant,
                    ZoneId.systemDefault())).orElse(null);
        } catch (IllegalArgumentException e) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_5d0044ca", e.getMessage());
        }
    }

    private static String initInstanceId() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name != null ? name : "unknown:" + ProcessHandle.current().pid();
    }

    private static DefaultRedisScript<Long> initReleaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
        script.setResultType(Long.class);
        return script;
    }
}
