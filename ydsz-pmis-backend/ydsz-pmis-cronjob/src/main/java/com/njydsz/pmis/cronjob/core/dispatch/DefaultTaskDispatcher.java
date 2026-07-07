package com.njydsz.pmis.cronjob.core.dispatch;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.common.job.ShardingContext;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.alert.AlertContext;
import com.njydsz.pmis.cronjob.core.alert.AlertTrigger;
import com.njydsz.pmis.cronjob.core.alert.AlertType;
import com.njydsz.pmis.cronjob.core.dag.TaskCompletedEvent;
import com.njydsz.pmis.cronjob.core.executor.JobNodeHeartbeat;
import com.njydsz.pmis.cronjob.core.sharding.ShardAssignment;
import com.njydsz.pmis.cronjob.core.sharding.ShardingStrategy;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.entity.JobNodeDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.mapper.JobNodeMapper;
import com.njydsz.pmis.cronjob.metrics.CronjobMetrics;
import com.njydsz.pmis.cronjob.service.TenantQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.Map;
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
    private final ApplicationEventPublisher eventPublisher;
    /** P5: 告警触发器（可选注入，未配置时不触发告警） */
    private final ObjectProvider<AlertTrigger> alertTriggerProvider;
    /** P6-2: Prometheus 指标收集器（可选注入，未配置时不记录指标） */
    private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;
    /** P7-3: 租户级配额服务（可选注入，未配置时跳过配额检查与计数） */
    private final ObjectProvider<TenantQuotaService> tenantQuotaServiceProvider;
    /** P1-5: HTTP 任务处理器（可选注入，未配置时 HTTP 类型任务降级到 BEAN 模式） */
    private final ObjectProvider<com.njydsz.pmis.cronjob.core.handler.HttpJobHandler> httpJobHandlerProvider;
    /** P1-4: 远程派发客户端（可选注入，未配置时分片任务仅本地执行） */
    private final ObjectProvider<RemoteTaskClient> remoteTaskClientProvider;

    /** 任务锁 key 前缀 */
    private static final String JOB_LOCK_PREFIX = "pmis:job:lock:";

    /** 当前实例标识（hostname:pid），用于锁值和安全释放 */
    private static final String INSTANCE_ID = initInstanceId();

    /** Lua 脚本: 安全释放锁（仅当 value 匹配时才 delete） */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = initReleaseScript();

    /** P0-2: 执行节点 ID（hostname:port），用于故障转移时定位任务所在节点 */
    private String nodeId;

    /** P0-5: 服务端口（通过 @Value 注入，修正 JobNodeHeartbeat 之前返回 PID 的问题） */
    @org.springframework.beans.factory.annotation.Value("${server.port:0}")
    private int serverPort;

    /** P1-1: 重试调度线程池（延迟调度失败重试） */
    private java.util.concurrent.ScheduledExecutorService retryScheduler;

    /** P1-7: 任务执行线程池（隔离调度线程与执行线程，限制并发） */
    private java.util.concurrent.ThreadPoolExecutor taskExecutorPool;

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
        // P1-2: CONCURRENT 策略不加锁
        if ("CONCURRENT".equals(job.getBlockStrategy())) {
            holdLock = false;
        }
        // P7-3: 对非 MANUAL 触发的任务进行配额检查（手动触发的任务不限制）
        if (holdLock) {
            checkExecutionQuota(job);
        }
        // P3: 分片任务走分片执行路径
        if (isShardedJob(job)) {
            return executeShardedJob(job, holdLock, triggerType);
        }
        // P1-7: MANUAL 触发同步执行（API 调用方需要 logId），其他触发类型异步执行（隔离调度线程）
        if (TRIGGER_MANUAL.equals(triggerType)) {
            return executeJob(job, holdLock, triggerType, 0);
        }
        return dispatchAsync(job, holdLock, triggerType, 0);
    }

    /**
     * P1-4: 在本地执行任务（远程派发接收端）。
     *
     * <p>由 {@code InternalJobController} 调用，接收 Leader 节点的远程派发请求后在本地执行。
     * 不经过 dispatch 路由（无配额检查、无异步派发），直接调用 executeJob/executeShard。
     *
     * @param job         任务定义
     * @param triggerType 触发类型
     * @param shardIndex  分片索引（-1 表示非分片任务）
     * @param shardTotal  分片总数（1 表示非分片任务）
     * @return 执行日志 ID；锁被持有或执行失败返回 null
     */
    @Override
    public String executeLocally(JobDO job, String triggerType, int shardIndex, int shardTotal) {
        boolean holdLock = !TRIGGER_MANUAL.equals(triggerType);
        if ("CONCURRENT".equals(job.getBlockStrategy())) {
            holdLock = false;
        }
        if (shardIndex >= 0 && shardTotal > 1) {
            return executeShard(job, shardIndex, shardTotal, holdLock, triggerType);
        }
        return executeJob(job, holdLock, triggerType, 0);
    }

    /**
     * P1-7: 异步派发任务到执行线程池。
     *
     * <p>将 {@link #executeJob} 提交到 {@link #taskExecutorPool}，调度线程立即返回。
     * 线程池满时使用 CallerRunsPolicy（在调度线程中同步执行），提供自然背压。
     *
     * @return null（异步执行，logId 在执行完成后写入日志）
     */
    private String dispatchAsync(JobDO job, boolean holdLock, String triggerType, int retryCount) {
        try {
            taskExecutorPool.submit(() -> {
                try {
                    executeJob(job, holdLock, triggerType, retryCount);
                } catch (Exception e) {
                    log.error("[Dispatcher] 异步执行异常: key={} triggerType={} reason={}",
                            job.getJobKey(), triggerType, e.getMessage(), e);
                }
            });
            log.debug("[Dispatcher] 任务异步派发: key={} triggerType={} poolSize={} active={} queue={}",
                    job.getJobKey(), triggerType,
                    taskExecutorPool.getPoolSize(),
                    taskExecutorPool.getActiveCount(),
                    taskExecutorPool.getQueue().size());
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // CallerRunsPolicy 已配置，理论上不会走到这里；防御性处理
            log.warn("[Dispatcher] 线程池拒绝提交, 降级同步执行: key={} reason={}",
                    job.getJobKey(), e.getMessage());
            return executeJob(job, holdLock, triggerType, retryCount);
        }
        return null;
    }

    /**
     * P7-3: 检查租户并发配额 + 日执行配额。
     *
     * <p>仅对 CRON/RETRY/DEPENDENT/MISFIRED 触发类型调用（MANUAL 不检查）。
     * 配额超限时抛 {@link BizException}，任务不会被派发。
     * 配额服务不可用时降级放行（不影响任务执行）。
     */
    private void checkExecutionQuota(JobDO job) {
        TenantQuotaService quotaService = tenantQuotaServiceProvider.getIfAvailable();
        if (quotaService == null) {
            return;
        }
        String tenantId = job.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }
        try {
            quotaService.checkConcurrentQuota(tenantId);
            quotaService.checkDailyExecutionQuota(tenantId);
        } catch (BizException e) {
            // 配额超限，记录日志后重新抛出
            log.warn("[Dispatcher] 租户配额超限, 拒绝派发: key={} tenant={} code={}",
                    job.getJobKey(), tenantId, e.getCode());
            throw e;
        } catch (Exception e) {
            // 配额服务异常，降级放行
            log.warn("[Dispatcher] 配额检查异常, 降级放行: key={} tenant={} reason={}",
                    job.getJobKey(), tenantId, e.getMessage());
        }
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
     * 分片任务执行入口（P3 + P1-4 远程派发）。
     *
     * <p>流程：
     * <ol>
     *   <li>查询在线节点列表（按 nodeId 升序保证确定性）</li>
     *   <li>通过 ShardingStrategy 计算分片分配方案</li>
     *   <li>本地分片：Leader 直接调用 {@link #executeShard}</li>
     *   <li>远程分片：通过 {@link RemoteTaskClient} HTTP 派发到执行器节点（P1-4）</li>
     *   <li>远程派发失败时根据 {@code remote.fallbackToLocal} 决定是否降级本地执行</li>
     * </ol>
     *
     * @return 第一个成功创建日志的分片 logId；全部被锁或无本地分片返回 null
     */
    private String executeShardedJob(JobDO job, boolean holdLock, String triggerType) {
        int shardTotal = job.getShardTotal();
        ShardingStrategy strategy = shardingStrategyProvider.getIfAvailable();
        if (strategy == null) {
            // 理论上不会走到这里（isShardedJob 已判定），防御性处理
            log.warn("[Dispatcher] ShardingStrategy 不可用, fallback 到非分片模式: key={}", job.getJobKey());
            return executeJob(job, holdLock, triggerType, 0);
        }

        List<JobNodeDO> onlineNodes = getOnlineNodeList();
        String localNodeId = jobNodeHeartbeat != null ? jobNodeHeartbeat.getNodeId() : null;

        List<ShardAssignment> assignments;
        if (onlineNodes.isEmpty() || localNodeId == null) {
            // fallback: 无在线节点信息或本地 nodeId 未知, 本地执行全部分片
            assignments = buildLocalOnlyAssignments(shardTotal, localNodeId);
        } else {
            List<String> nodeIds = onlineNodes.stream()
                    .map(JobNodeDO::getNodeId).collect(Collectors.toList());
            assignments = strategy.assign(shardTotal, nodeIds);
        }

        // 构建 nodeId → JobNodeDO 映射，供远程派发查询节点地址
        Map<String, JobNodeDO> nodeMap = onlineNodes.stream()
                .collect(Collectors.toMap(JobNodeDO::getNodeId, n -> n, (a, b) -> a));

        log.info("[Dispatcher] 分片任务派发: key={} shardTotal={} assignments={} localNode={}",
                job.getJobKey(), shardTotal, assignments.size(), localNodeId);

        String firstLogId = null;
        for (ShardAssignment assignment : assignments) {
            String assignedNodeId = assignment.nodeId();
            String logId;
            if (localNodeId != null && localNodeId.equals(assignedNodeId)) {
                // 本地分片：直接执行
                logId = executeShard(job, assignment.shardIndex(), shardTotal, holdLock, triggerType);
            } else {
                // P1-4: 远程分片：通过 HTTP 派发到执行器节点
                logId = dispatchShardRemotely(job, assignment, shardTotal, holdLock, triggerType, nodeMap);
            }
            if (firstLogId == null && logId != null) {
                firstLogId = logId;
            }
        }
        return firstLogId;
    }

    /**
     * P1-4: 远程派发分片到执行器节点。
     *
     * <p>通过 {@link RemoteTaskClient} 将分片任务 HTTP 派发到选定的执行器节点。
     * 执行器节点收到请求后调用 {@link #executeLocally} 在本地执行。
     *
     * <p>降级策略：当 {@code remote.fallbackToLocal=true} 且远程派发失败时
     * （连接拒绝、超时、HTTP 错误），Leader 在本地执行该分片。
     * 由 Redis 分布式锁保证不会重复执行（远程节点已持锁时本地执行也会跳过）。
     *
     * @param job        任务定义
     * @param assignment 分片分配结果
     * @param shardTotal 分片总数
     * @param holdLock   是否需要抢占分布式锁（MANUAL 触发为 false，其他触发为 true）
     * @param triggerType 触发类型
     * @param nodeMap    在线节点映射（nodeId → JobNodeDO）
     * @return 执行日志 ID；派发失败且未降级返回 null
     */
    private String dispatchShardRemotely(JobDO job, ShardAssignment assignment, int shardTotal,
                                          boolean holdLock, String triggerType,
                                          Map<String, JobNodeDO> nodeMap) {
        CronjobProperties.Remote remoteConfig = cronjobProperties.getRemote();
        if (!remoteConfig.isEnabled()) {
            // 远程派发未启用：本地执行该分片（兼容旧行为）
            return executeShard(job, assignment.shardIndex(), shardTotal, holdLock, triggerType);
        }
        RemoteTaskClient client = remoteTaskClientProvider.getIfAvailable();
        if (client == null) {
            log.debug("[Dispatcher] RemoteTaskClient 不可用, 本地执行: key={} shard={}",
                    job.getJobKey(), assignment.shardIndex());
            return executeShard(job, assignment.shardIndex(), shardTotal, holdLock, triggerType);
        }
        JobNodeDO node = nodeMap.get(assignment.nodeId());
        if (node == null || node.getHost() == null || node.getPort() == null) {
            log.warn("[Dispatcher] 节点信息缺失, 降级本地执行: key={} shard={} nodeId={}",
                    job.getJobKey(), assignment.shardIndex(), assignment.nodeId());
            return executeShard(job, assignment.shardIndex(), shardTotal, holdLock, triggerType);
        }
        RemoteTaskRequest request = new RemoteTaskRequest(
                job, triggerType, assignment.shardIndex(), shardTotal, TraceIdUtil.get());
        String logId = client.dispatch(node, request);
        if (logId == null && remoteConfig.isFallbackToLocal()) {
            log.info("[Dispatcher] 远程派发失败, 降级本地执行: key={} shard={} nodeId={}",
                    job.getJobKey(), assignment.shardIndex(), assignment.nodeId());
            return executeShard(job, assignment.shardIndex(), shardTotal, holdLock, triggerType);
        }
        if (logId != null) {
            log.debug("[Dispatcher] 远程分片派发成功: key={} shard={} nodeId={} logId={}",
                    job.getJobKey(), assignment.shardIndex(), assignment.nodeId(), logId);
        }
        return logId;
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
        // P0-1: 记录持锁者标识，供 TimeoutMonitor 用 Lua 脚本安全释放锁
        if (lockKey != null) {
            log0.setLockHolder(INSTANCE_ID);
        }
        // P0-2: 记录执行节点 ID 和线程 ID，供故障转移和超时清理定位
        log0.setExecNodeId(nodeId);
        log0.setExecThreadId(Thread.currentThread().threadId());
        log0.setCreatedAt(LocalDateTime.now());
        log0.setDeleted(0);
        jobLogMapper.insert(log0);

        // P7-3: 记录执行开始（INCR 并发计数器 + 日执行计数器）
        recordExecutionStart(job.getTenantId());

        boolean success = false;
        Object result = null;
        try {
            JobHandler handler = resolveHandler(job);
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

            // P7-3: 记录执行结束（DECR 并发计数器）
            recordExecutionEnd(job.getTenantId());

            if (jobNodeHeartbeat != null) {
                jobNodeHeartbeat.onTaskComplete();
            }
        }
        // P6-2: 记录分片执行指标
        recordJobMetrics(job, triggerType, success, log0);
        // P5: 触发告警（分片级别，失败告警 + 慢任务告警）
        triggerAlerts(job, success, log0);
        return log0.getId();
    }

    /**
     * 查询在线节点列表（按 nodeId 升序，保证分片分配确定性）。
     *
     * <p>P1-4: 返回完整的 {@link JobNodeDO} 列表，供远程派发获取 host/port。
     */
    private List<JobNodeDO> getOnlineNodeList() {
        try {
            long threshold = cronjobProperties.getExecutor().getOfflineThresholdSeconds();
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(threshold);
            LambdaQueryWrapper<JobNodeDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(JobNodeDO::getStatus, "ONLINE")
                    .ge(JobNodeDO::getLastHeartbeat, cutoff)
                    .orderByAsc(JobNodeDO::getNodeId);
            return jobNodeMapper.selectList(wrapper);
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
     * P1-5: 根据任务类型解析处理器。
     *
     * <p>路由规则：
     * <ul>
     *   <li>{@code jobType=HTTP}: 返回 {@link com.njydsz.pmis.cronjob.core.handler.HttpJobHandler}</li>
     *   <li>{@code jobType=BEAN} 或 null: 按 handler 字段查找 Spring Bean（默认行为）</li>
     *   <li>其他类型: 暂不支持，降级到 BEAN 模式查找</li>
     * </ul>
     *
     * <p>HTTP 处理器不可用时降级到 BEAN 模式（记录警告），保证启动兼容性。
     *
     * @param job 任务定义
     * @return 任务处理器
     * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException BEAN 模式下找不到对应 Bean
     */
    private JobHandler resolveHandler(JobDO job) {
        String jobType = job.getJobType();
        if ("HTTP".equals(jobType)) {
            com.njydsz.pmis.cronjob.core.handler.HttpJobHandler httpHandler =
                    httpJobHandlerProvider.getIfAvailable();
            if (httpHandler != null) {
                return httpHandler;
            }
            log.warn("[Dispatcher] HTTP 处理器未注册, 降级到 BEAN 模式: key={} handler={}",
                    job.getJobKey(), job.getHandler());
        }
        return applicationContext.getBean(job.getHandler(), JobHandler.class);
    }

    /**
     * 执行任务（核心逻辑，从 JobServiceImpl 抽取）。
     *
     * @param job         任务定义
     * @param holdLock    是否抢占分布式锁
     * @param triggerType 触发类型
     * @param retryCount  当前重试次数（0=首次执行）
     * @return 执行日志 ID；锁被持有时返回 null
     */
    private String executeJob(JobDO job, boolean holdLock, String triggerType, int retryCount) {
        String lockKey = null;
        if (holdLock) {
            lockKey = JOB_LOCK_PREFIX + job.getJobKey();
            Duration ttl = resolveLockTtl(job);
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, INSTANCE_ID, ttl);
            if (!Boolean.TRUE.equals(acquired)) {
                // P1-2: 锁被持有时根据阻塞策略决定行为
                String strategy = job.getBlockStrategy();
                if ("DISCARD".equals(strategy)) {
                    log.info("[Dispatcher] DISCARD 策略, 丢弃新触发: key={}", job.getJobKey());
                    return null;
                }
                // SERIAL（默认）和其他策略都视为跳过（无法中断正在执行的任务）
                log.info("[Dispatcher] 任务已被其他实例持有锁, 跳过: key={} triggerType={} strategy={}",
                        job.getJobKey(), triggerType, strategy);
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
        // P0-1: 记录持锁者标识，供 TimeoutMonitor 用 Lua 脚本安全释放锁
        if (lockKey != null) {
            log0.setLockHolder(INSTANCE_ID);
        }
        // P0-2: 记录执行节点 ID 和线程 ID，供故障转移和超时清理定位
        log0.setExecNodeId(nodeId);
        log0.setExecThreadId(Thread.currentThread().threadId());
        log0.setCreatedAt(LocalDateTime.now());
        log0.setDeleted(0);
        jobLogMapper.insert(log0);

        // P7-3: 记录执行开始（INCR 并发计数器 + 日执行计数器）
        recordExecutionStart(job.getTenantId());

        boolean success = false;
        Object result = null;
        try {
            JobHandler handler = resolveHandler(job);
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
            // P1-6: 熔断逻辑 - 成功时不改 status（保持 NORMAL），失败时只在非重试场景改 ERROR
            String statusOnError = success ? null : "ERROR";
            jobMapper.updateStats(job.getId(), log0.getStartTime(), next, incFire, incSucc, incFail,
                    statusOnError);

            // P1-6: 熔断计数（成功归零，失败递增 + 达到阈值自动暂停）
            updateCircuitBreaker(job, success);

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

            // P7-3: 记录执行结束（DECR 并发计数器）
            recordExecutionEnd(job.getTenantId());

            // 通知心跳组件：任务结束
            if (jobNodeHeartbeat != null) {
                jobNodeHeartbeat.onTaskComplete();
            }
        }
        // P6-2: 记录任务执行指标
        recordJobMetrics(job, triggerType, success, log0);
        // P4: 发布任务完成事件，触发后继依赖任务（DagExecutor 异步监听）
        publishTaskCompleted(job, success, log0.getId());
        // P5: 触发告警（失败告警 + 慢任务告警）
        triggerAlerts(job, success, log0);
        // P1-1: 失败重试（非 RETRY 触发且 maxRetries > 0 且 retryCount < maxRetries）
        if (!success) {
            scheduleRetryIfNeeded(job, holdLock, triggerType, retryCount);
        }
        return log0.getId();
    }

    /**
     * P4: 发布任务完成事件，触发后继依赖任务。
     *
     * <p>使用 try-catch 包裹，确保事件发布失败不影响主流程。
     */
    private void publishTaskCompleted(JobDO job, boolean success, String logId) {
        try {
            TaskCompletedEvent event = new TaskCompletedEvent(
                    job.getId(), job.getJobKey(), success, logId);
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("[Dispatcher] 发布任务完成事件失败(不影响主流程): key={} reason={}",
                    job.getJobKey(), e.getMessage());
        }
    }

    /**
     * P5: 触发告警。
     *
     * <p>根据任务执行结果触发相应告警：
     * <ul>
     *   <li>失败时触发 {@link AlertType#FAIL} 告警</li>
     *   <li>成功时触发 {@link AlertType#SLOW} 告警（triggerValue=耗时毫秒，由规则阈值判定是否实际告警）</li>
     * </ul>
     *
     * <p>使用 try-catch 包裹，确保告警触发失败不影响主流程。
     *
     * @param job     任务定义
     * @param success 是否执行成功
     * @param log0    任务日志（含耗时信息）
     */
    private void triggerAlerts(JobDO job, boolean success, JobLogDO log0) {
        AlertTrigger alertTrigger = alertTriggerProvider.getIfAvailable();
        if (alertTrigger == null) {
            return;
        }
        try {
            String triggerValue = log0.getDurationMs() != null
                    ? String.valueOf(log0.getDurationMs())
                    : null;
            AlertContext context = new AlertContext(
                    success ? AlertType.SLOW : AlertType.FAIL,
                    job.getId(),
                    job.getJobKey(),
                    job.getJobName(),
                    log0.getId(),
                    triggerValue,
                    log0.getErrorMessage(),
                    log0.getTraceId(),
                    job.getTenantId()
            );
            alertTrigger.trigger(context);
        } catch (Exception e) {
            log.warn("[Dispatcher] 触发告警失败(不影响主流程): key={} reason={}",
                    job.getJobKey(), e.getMessage());
        }
    }

    /**
     * P7-3: 记录任务执行开始（INCR 并发计数器 + 日执行计数器）。
     *
     * <p>TenantQuotaService 不可用时跳过；内部有容错，不会抛异常。
     *
     * @param tenantId 租户 ID
     */
    private void recordExecutionStart(String tenantId) {
        TenantQuotaService quotaService = tenantQuotaServiceProvider.getIfAvailable();
        if (quotaService == null || tenantId == null || tenantId.isBlank()) {
            return;
        }
        try {
            quotaService.recordExecutionStart(tenantId);
        } catch (Exception e) {
            log.debug("[Dispatcher] recordExecutionStart 失败(不影响主流程): tenant={} reason={}",
                    tenantId, e.getMessage());
        }
    }

    /**
     * P7-3: 记录任务执行结束（DECR 并发计数器）。
     *
     * <p>TenantQuotaService 不可用时跳过；内部有容错，不会抛异常。
     *
     * @param tenantId 租户 ID
     */
    private void recordExecutionEnd(String tenantId) {
        TenantQuotaService quotaService = tenantQuotaServiceProvider.getIfAvailable();
        if (quotaService == null || tenantId == null || tenantId.isBlank()) {
            return;
        }
        try {
            quotaService.recordExecutionEnd(tenantId);
        } catch (Exception e) {
            log.debug("[Dispatcher] recordExecutionEnd 失败(不影响主流程): tenant={} reason={}",
                    tenantId, e.getMessage());
        }
    }

    /**
     * P6-2: 记录任务执行指标。
     *
     * <p>使用 try-catch 包裹，确保指标记录失败不影响主流程。
     *
     * @param job         任务定义
     * @param triggerType 触发类型
     * @param success     是否执行成功
     * @param log0        任务日志（含耗时信息）
     */
    private void recordJobMetrics(JobDO job, String triggerType, boolean success, JobLogDO log0) {
        CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
        if (metrics == null) {
            return;
        }
        try {
            String status = success ? "SUCCESS" : "FAILED";
            metrics.incJobDispatched(triggerType, status);
            metrics.recordJobDuration(job.getJobKey(), status,
                    log0.getDurationMs() != null ? log0.getDurationMs() : 0L);
            if (!success) {
                metrics.incJobFailed(job.getJobKey());
            }
        } catch (Exception e) {
            log.debug("[Dispatcher] 指标记录失败(不影响主流程): key={} reason={}",
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
            Instant next = trigger.nextExecution(ctx);
            return next == null ? null : LocalDateTime.ofInstant(next,
                    ZoneId.systemDefault());
        } catch (IllegalArgumentException e) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_5d0044ca", e.getMessage());
        }
    }

    private static String initInstanceId() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name != null ? name : "unknown:" + ProcessHandle.current().pid();
    }

    /**
     * P1-1: 失败重试调度。
     *
     * <p>当任务执行失败且 maxRetries > 0 且 retryCount < maxRetries 时，
     * 通过 ScheduledExecutorService 延迟调度重试。
     * 重试延迟根据 retryBackoff 计算：
     * <ul>
     *   <li>FIXED: 固定 retryIntervalMs</li>
     *   <li>EXPONENTIAL: retryIntervalMs * 2^retryCount</li>
     * </ul>
     *
     * @param job         任务定义
     * @param holdLock    是否持锁
     * @param triggerType 原始触发类型
     * @param retryCount  当前重试次数
     */
    private void scheduleRetryIfNeeded(JobDO job, boolean holdLock, String triggerType, int retryCount) {
        Integer maxRetries = job.getMaxRetries();
        if (maxRetries == null || maxRetries <= 0 || retryCount >= maxRetries) {
            return;
        }
        // 计算重试延迟
        long delayMs = calculateRetryDelayMs(job, retryCount);
        int nextRetry = retryCount + 1;
        log.info("[Dispatcher] 调度失败重试: key={} retry={}/{} delay={}ms backoff={}",
                job.getJobKey(), nextRetry, maxRetries, delayMs, job.getRetryBackoff());
        try {
            retryScheduler.schedule(() -> {
                try {
                    executeJob(job, holdLock, TRIGGER_RETRY, nextRetry);
                } catch (Exception e) {
                    log.error("[Dispatcher] 重试执行异常: key={} retry={} reason={}",
                            job.getJobKey(), nextRetry, e.getMessage(), e);
                }
            }, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("[Dispatcher] 调度重试失败: key={} retry={} reason={}",
                    job.getJobKey(), nextRetry, e.getMessage(), e);
        }
    }

    /**
     * P1-1: 计算重试延迟（毫秒）。
     */
    private long calculateRetryDelayMs(JobDO job, int retryCount) {
        Long interval = job.getRetryIntervalMs();
        if (interval == null || interval <= 0) {
            return 0; // 立即重试
        }
        String backoff = job.getRetryBackoff();
        if ("EXPONENTIAL".equals(backoff)) {
            // 指数退避: interval * 2^retryCount，上限 5 分钟避免过长延迟
            long delay = interval * (1L << Math.min(retryCount, 10));
            return Math.min(delay, 300_000L);
        }
        // FIXED: 固定间隔
        return interval;
    }

    /**
     * P1-6: 熔断计数（成功归零，失败递增 + 达到阈值自动暂停）。
     *
     * @param job     任务定义
     * @param success 是否执行成功
     */
    private void updateCircuitBreaker(JobDO job, boolean success) {
        try {
            if (success) {
                jobMapper.resetConsecutiveFail(job.getId());
            } else {
                jobMapper.incrementConsecutiveFail(job.getId());
                Integer maxFails = job.getMaxConsecutiveFails();
                if (maxFails != null && maxFails > 0) {
                    Integer current = jobMapper.selectConsecutiveFailCount(job.getId());
                    if (current != null && current >= maxFails) {
                        jobMapper.markAutoPaused(job.getId());
                        log.warn("[Dispatcher] 任务熔断, 自动暂停: key={} consecutiveFails={}/{}",
                                job.getJobKey(), current, maxFails);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Dispatcher] 熔断计数更新失败(不影响主流程): key={} reason={}",
                    job.getJobKey(), e.getMessage());
        }
    }

    /**
     * P0-2/P0-5: 初始化执行节点 ID（hostname:port）。
     *
     * <p>在 @PostConstruct 中调用，确保 serverPort 已通过 @Value 注入。
     */
    @jakarta.annotation.PostConstruct
    private void initNodeId() {
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            this.nodeId = hostname + ":" + serverPort;
        } catch (Exception e) {
            this.nodeId = INSTANCE_ID;
        }
        // P1-1: 初始化重试调度线程池
        this.retryScheduler = java.util.concurrent.Executors.newScheduledThreadPool(
                2, r -> {
                    Thread t = new Thread(r, "pmis-job-retry");
                    t.setDaemon(true);
                    return t;
                });
        // P1-7: 初始化任务执行线程池（隔离调度线程与执行线程）
        CronjobProperties.Executor execConfig = cronjobProperties.getExecutor();
        int corePoolSize = Math.max(1, execConfig.getMaxConcurrent());
        int maxPoolSize = Math.max(corePoolSize, execConfig.getMaxConcurrent());
        int queueCapacity = Math.max(0, execConfig.getQueueCapacity());
        java.util.concurrent.BlockingQueue<Runnable> workQueue =
                queueCapacity == 0
                        ? new java.util.concurrent.SynchronousQueue<>()
                        : new java.util.concurrent.LinkedBlockingQueue<>(queueCapacity);
        java.util.concurrent.atomic.AtomicInteger threadCounter = new java.util.concurrent.atomic.AtomicInteger(0);
        this.taskExecutorPool = new java.util.concurrent.ThreadPoolExecutor(
                corePoolSize, maxPoolSize, 60L, java.util.concurrent.TimeUnit.SECONDS,
                workQueue,
                r -> {
                    Thread t = new Thread(r, execConfig.getThreadNamePrefix() + threadCounter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        log.info("[Dispatcher] 节点 ID 初始化完成: nodeId={} instanceId={} serverPort={}",
                nodeId, INSTANCE_ID, serverPort);
        log.info("[Dispatcher] P1-7 执行线程池初始化: core={} max={} queue={} policy=CallerRunsPolicy",
                corePoolSize, maxPoolSize, queueCapacity);
    }

    @jakarta.annotation.PreDestroy
    private void shutdownRetryScheduler() {
        if (retryScheduler != null) {
            retryScheduler.shutdown();
            try {
                if (!retryScheduler.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    retryScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                retryScheduler.shutdownNow();
            }
        }
        // P1-7: 关闭任务执行线程池
        if (taskExecutorPool != null) {
            taskExecutorPool.shutdown();
            try {
                if (!taskExecutorPool.awaitTermination(
                        cronjobProperties.getExecutor().getDrainTimeoutSeconds(),
                        java.util.concurrent.TimeUnit.SECONDS)) {
                    taskExecutorPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                taskExecutorPool.shutdownNow();
            }
        }
    }

    private static DefaultRedisScript<Long> initReleaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
        script.setResultType(Long.class);
        return script;
    }
}
