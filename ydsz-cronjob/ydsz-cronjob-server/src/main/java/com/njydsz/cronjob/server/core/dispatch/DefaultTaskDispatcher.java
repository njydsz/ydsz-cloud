package com.njydsz.cronjob.server.core.dispatch;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.alibaba.ttl.TtlRunnable;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.id.RandomUtils;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.domain.entity.job.JobNode;
import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.domain.job.JobExecutionContext;
import com.njydsz.cronjob.domain.job.JobHandler;
import com.njydsz.cronjob.domain.job.ProcessResult;
import com.njydsz.cronjob.domain.job.ShardingContext;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.infra.mapper.job.JobNodeMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.LockKeyUtil;
import com.njydsz.cronjob.server.core.TaskCompletedEvent;
import com.njydsz.cronjob.server.core.alert.AlertContext;
import com.njydsz.cronjob.server.core.alert.AlertTrigger;
import com.njydsz.cronjob.server.core.alert.AlertType;
import com.njydsz.cronjob.server.core.discovery.NodeDiscoveryStrategy;
import com.njydsz.cronjob.server.core.executor.GlobalConcurrencyController;
import com.njydsz.cronjob.server.core.executor.JobNodeHeartbeat;
import com.njydsz.cronjob.server.core.executor.TenantAwareExecutorPool;
import com.njydsz.cronjob.server.core.handler.GlueJobHandler;
import com.njydsz.cronjob.server.core.handler.HttpJobHandler;
import com.njydsz.cronjob.server.core.handler.ScriptJobHandler;
import com.njydsz.cronjob.server.core.logger.JobLoggerImpl;
import com.njydsz.cronjob.server.core.logger.LogStreamManager;
import com.njydsz.cronjob.server.core.map.MapTaskExecutor;
import com.njydsz.cronjob.server.core.sharding.ShardAssignment;
import com.njydsz.cronjob.server.core.sharding.ShardingStrategy;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;
import com.njydsz.cronjob.server.service.job.TenantQuotaService;
import com.njydsz.cronjob.server.service.log.JobLogContentService;

/**
 * 默认任务派发器：本地执行 + 分布式锁。
 *
 * <p>P1 阶段实现：Leader 节点扫描到待触发任务后，通过本派发器在本地执行。 远程派发（HTTP/Feign）留作 P3 阶段扩展。
 *
 * <h3>执行流程</h3>
 *
 * <ol>
 *   <li>抢占分布式锁（任务级 TTL，可选）
 *   <li>写开始日志（ydsz_job_log, status=RUNNING）
 *   <li>调用 {@link JobHandler#execute(String)} 执行业务逻辑
 *   <li>更新日志为 SUCCESS/FAILED + 任务统计
 *   <li>释放锁（Lua 脚本安全释放）
 * </ol>
 *
 * <p>与 {@link JobNodeHeartbeat} 联动：执行前后递增/递减 running_count， 用于负载均衡选择。
 *
 * @author ydsz-team
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
  private final RedisTemplate<String, Object> redisTemplate;
  private final CronjobProperties cronjobProperties;

  /** P1-1: 心跳组件（可选注入，type=nacos 时不注册） */
  private final ObjectProvider<JobNodeHeartbeat> jobNodeHeartbeatProvider;

  /** P3: 节点 Mapper，用于查询在线节点列表做分片分配 */
  private final JobNodeMapper jobNodeMapper;

  /** P1-1: 节点发现策略（可选注入，优先使用；不可用时回退到 DB 查询） */
  private final ObjectProvider<NodeDiscoveryStrategy> nodeDiscoveryStrategyProvider;

  /** P3: 分片策略（可选注入，未配置时 fallback 到非分片模式） */
  private final ObjectProvider<ShardingStrategy> shardingStrategyProvider;

  /** P4: 事件发布器，任务完成后发布事件触发后继依赖 */
  private final ApplicationEventPublisher eventPublisher;

  /** P5: 告警触发器（可选注入，未配置时不触发告警） */
  private final ObjectProvider<AlertTrigger> alertTriggerProvider;

  /** P6-2: Prometheus 指标收集器（可选注入，未配置时不记录指标） */
  private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

  /** P1-1: 失败重试调度器（可选注入，未配置时不支持重试） */
  private final ObjectProvider<RetryScheduler> retrySchedulerProvider;

  /** P7-3: 租户级配额服务（可选注入，未配置时跳过配额检查与计数） */
  private final ObjectProvider<TenantQuotaService> tenantQuotaServiceProvider;

  /** P1-5: HTTP 任务处理器（可选注入，未配置时 HTTP 类型任务降级到 BEAN 模式） */
  private final ObjectProvider<HttpJobHandler> httpJobHandlerProvider;

  /** P1-2: GLUE 在线编码处理器（可选注入，未配置时 GLUE 类型任务降级到 BEAN 模式） */
  private final ObjectProvider<GlueJobHandler> glueJobHandlerProvider;

  /** P1-3: SHELL/Python 脚本处理器（可选注入，未配置时 SHELL 类型任务降级到 BEAN 模式） */
  private final ObjectProvider<ScriptJobHandler> scriptJobHandlerProvider;

  /** P1-4: 远程派发客户端（可选注入，未配置时分片任务仅本地执行） */
  private final ObjectProvider<RemoteTaskClient> remoteTaskClientProvider;

  /** P0-2: 在线日志内容 Service（可选注入，未配置时日志写入降级丢弃） */
  private final ObjectProvider<JobLogContentService> jobLogContentServiceProvider;

  /** P0-4: MapReduce 任务执行器（可选注入，未配置时 MAP/MAP_REDUCE 类型降级到 BEAN 模式） */
  private final ObjectProvider<MapTaskExecutor> mapTaskExecutorProvider;

  /** P2-5: 租户感知线程池（可选注入，未配置或 isolation-strategy=none 时使用全局池） */
  private final ObjectProvider<TenantAwareExecutorPool> tenantAwareExecutorPoolProvider;

  /** P3-13: WebHook 事件分发器（可选注入，未配置时不推送事件通知） */
  private final ObjectProvider<WebhookEventDispatcher> webhookEventDispatcherProvider;

  /** P0-1: Worker 节点选择器（调度器-执行器分离模式，可选注入） */
  private final ObjectProvider<WorkerNodeSelector> workerNodeSelectorProvider;

  /** P0-2: SSE 实时日志推送管理器（可选注入，未配置时仅写 DB） */
  private final ObjectProvider<LogStreamManager> logStreamManagerProvider;

  /** 统一领域事件发布门面（可选依赖，未配置时安全降级） */
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  /** P0-2: 全局并发控制器（可选注入，用于限制集群总并发） */
  private final ObjectProvider<GlobalConcurrencyController> globalConcurrencyControllerProvider;

  /** P1-A2: 线程池注册表（集中管理线程池生命周期，消除反射强耦合） */
  private final ObjectProvider<CronjobThreadPoolRegistry> threadPoolRegistryProvider;

  /** 当前实例标识（hostname:pid），用于锁值和安全释放 */
  private static final String INSTANCE_ID = initInstanceId();

  /** P0-A4: Lua 脚本统一引用 LockKeyUtil 常量，消除内联 Lua 字符串 */
  private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = initReleaseScript();

  private static DefaultRedisScript<Long> initReleaseScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText(LockKeyUtil.RELEASE_LOCK_SCRIPT);
    script.setResultType(Long.class);
    return script;
  }

  /** P0-2: 执行节点 ID（hostname:port），用于故障转移时定位任务所在节点 */
  private String nodeId;

  /** P0-5: 服务端口（通过 @Value 注入，修正 JobNodeHeartbeat 之前返回 PID 的问题） */
  @Value("${server.port:0}")
  private int serverPort;

  /**
   * P1-7: 任务执行线程池（隔离调度线程与执行线程，限制并发）。
   *
   * <p>保留手动创建原因：使用 {@link PriorityBlockingQueue} 实现优先级调度， common-thread 的 {@code
   * ThreadPoolTaskExecutor} 默认使用 {@code LinkedBlockingQueue} 不支持优先级队列。线程池参数通过 {@link
   * CronjobProperties.Executor} 配置化。
   */
  private ThreadPoolExecutor taskExecutorPool;

  /** P2-6: COVER 策略防递归标记（ThreadLocal），避免重新派发时锁仍被持有导致无限递归 */
  private static final ThreadLocal<Boolean> COVER_REDISPATCHING =
      ThreadLocal.withInitial(() -> false);

  /** 触发类型常量 */
  public static final String TRIGGER_CRON = "CRON";

  public static final String TRIGGER_MANUAL = "MANUAL";
  public static final String TRIGGER_RETRY = "RETRY";
  public static final String TRIGGER_DEPENDENT = "DEPENDENT";

  /** P2-2: Misfire 触发（合并执行时使用，日志可识别） */
  public static final String TRIGGER_MISFIRED = "MISFIRED";

  /** P0-3: API 手动触发（schedule_type=API 的任务通过接口手动触发时使用） */
  public static final String TRIGGER_API = "API";

  /** P0-2: 故障转移触发（JobNodeReaper 重建派发时使用） */
  public static final String TRIGGER_FAILOVER = "FAILOVER";

  /** P0-4: 事件驱动触发（MQ 消息触发时使用） */
  public static final String TRIGGER_EVENT = "EVENT";

  /**
   * P0-4: 暴露全局执行线程池引用，供 ThreadPoolHotUpdateListener 动态调整参数。
   *
   * @return 全局执行线程池；未初始化时返回 null
   */
  public ThreadPoolExecutor getTaskExecutorPool() {
    return taskExecutorPool;
  }

  /**
   * 派发任务执行
   *
   * <p>根据任务类型和配置选择执行路径：
   *
   * <ol>
   *   <li>跨集群任务：通过 CrossClusterDispatcher 派发到目标集群
   *   <li>分片任务：通过 ShardingStrategy 分配到多个节点执行
   *   <li>调度器-执行器分离模式：通过 WorkerNodeSelector 选择 Worker 节点远程派发
   *   <li>默认：本地执行（MANUAL 同步，其他异步）
   * </ol>
   *
   * <p>配额检查：非 MANUAL 触发时检查租户并发配额和日执行配额，超限拒绝派发。 分布式锁：非 MANUAL/CONCURRENT 触发时抢占任务级锁，防止重复执行。
   *
   * @param job 任务定义
   * @param executorNode 指定执行节点（当前版本忽略，始终本地执行）
   * @param triggerType 触发类型（CRON/MANUAL/RETRY/DEPENDENT/MISFIRED/API/FAILOVER/EVENT）
   * @return 执行日志 ID；锁被持有或配额超限时返回 null
   */
  @Override
  public String dispatch(Job job, String executorNode, String triggerType) {
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
    // P0-1: 调度器-执行器分离 — 非分片任务也走远程派发到 Worker 节点
    if (isSchedulerExecutorSeparationEnabled()) {
      String logId = dispatchToWorker(job, triggerType);
      if (logId != null) {
        return logId;
      }
      // 无可用 Worker 时降级本地执行
      log.debug("[Dispatcher] 无可用 Worker, 降级本地执行: key={}", job.getJobKey());
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
   * <p>由 {@code InternalJobController} 调用，接收 Leader 节点的远程派发请求后在本地执行。 不经过 dispatch
   * 路由（无配额检查、无异步派发），直接调用 executeJob/executeShard。
   *
   * @param job 任务定义
   * @param triggerType 触发类型
   * @param shardIndex 分片索引（-1 表示非分片任务）
   * @param shardTotal 分片总数（1 表示非分片任务）
   * @return 执行日志 ID；锁被持有或执行失败返回 null
   */
  @Override
  public String executeLocally(Job job, String triggerType, int shardIndex, int shardTotal) {
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
   * <p>将 {@link #executeJob} 提交到 {@link #taskExecutorPool}，调度线程立即返回。 线程池满时使用
   * CallerRunsPolicy（在调度线程中同步执行），提供自然背压。
   *
   * <p>P2-5: 启用租户隔离（isolation-strategy != none）时，按 tenantId/jobGroup 选择 隔离线程池，避免单租户大任务饿死其他租户。
   *
   * @return null（异步执行，logId 在执行完成后写入日志）
   */
  private String dispatchAsync(Job job, boolean holdLock, String triggerType, int retryCount) {
    try {
      // P2-5: 线程池租户隔离（isolation-strategy=none 时返回 null，使用全局池）
      TenantAwareExecutorPool pool = tenantAwareExecutorPoolProvider.getIfAvailable();
      ExecutorService executor =
          (pool != null) ? pool.getExecutor(job.getTenantId(), job.getJobGroup()) : null;
      if (executor == null) {
        executor = taskExecutorPool;
      }
      final ExecutorService finalExecutor = executor;
      // P0-3: 包装为 PriorityRunnable 实现优先级调度
      PriorityRunnable priorityTask =
          new PriorityRunnable(
              job.getPriority(),
              () -> {
                try {
                  executeJob(job, holdLock, triggerType, retryCount);
                } catch (Exception e) {
                  log.error(
                      "[Dispatcher] 异步执行异常: key={} triggerType={} reason={}",
                      job.getJobKey(),
                      triggerType,
                      e.getMessage(),
                      e);
                }
              });
      // P0-FIX: TTL 线程池传播，确保 JobExecutionContext 从调度线程传递到工作线程
      Runnable ttlTask = TtlRunnable.get(priorityTask);
      executor.execute(ttlTask);
      if (finalExecutor instanceof ThreadPoolExecutor tpe) {
        log.debug(
            "[Dispatcher] 任务异步派发: key={} triggerType={} pool={} active={} queue={}",
            job.getJobKey(),
            triggerType,
            tpe.getPoolSize(),
            tpe.getActiveCount(),
            tpe.getQueue().size());
      }
    } catch (RejectedExecutionException e) {
      // CallerRunsPolicy 已配置，理论上不会走到这里；防御性处理
      log.warn("[Dispatcher] 线程池拒绝提交, 降级同步执行: key={} reason={}", job.getJobKey(), e.getMessage());
      return executeJob(job, holdLock, triggerType, retryCount);
    }
    return null;
  }

  /**
   * P7-3: 检查租户并发配额 + 日执行配额。
   *
   * <p>仅对 CRON/RETRY/DEPENDENT/MISFIRED 触发类型调用（MANUAL 不检查）。 配额超限时抛 {@link SysException}，任务不会被派发。
   * 配额服务不可用时降级放行（不影响任务执行）。
   */
  private void checkExecutionQuota(Job job) {
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
    } catch (SysException e) {
      // 配额超限，记录日志后重新抛出
      log.warn(
          "[Dispatcher] 租户配额超限, 拒绝派发: key={} tenant={} code={}",
          job.getJobKey(),
          tenantId,
          e.getCode());
      throw e;
    } catch (Exception e) {
      // 配额服务异常，降级放行
      log.warn(
          "[Dispatcher] 配额检查异常, 降级放行: key={} tenant={} reason={}",
          job.getJobKey(),
          tenantId,
          e.getMessage());
    }
  }

  /**
   * 判定是否为分片任务（P3）。
   *
   * <p>需同时满足：shardTotal > 1 且 ShardingStrategy Bean 可用。 否则 fallback 到非分片模式，保证向后兼容。
   */
  private boolean isShardedJob(Job job) {
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
   *
   * <ol>
   *   <li>查询在线节点列表（按 nodeId 升序保证确定性）
   *   <li>通过 ShardingStrategy 计算分片分配方案
   *   <li>本地分片：Leader 直接调用 {@link #executeShard}
   *   <li>远程分片：通过 {@link RemoteTaskClient} HTTP 派发到执行器节点（P1-4）
   *   <li>远程派发失败时根据 {@code remote.fallbackToLocal} 决定是否降级本地执行
   * </ol>
   *
   * @return 第一个成功创建日志的分片 logId；全部被锁或无本地分片返回 null
   */
  private String executeShardedJob(Job job, boolean holdLock, String triggerType) {
    int shardTotal = job.getShardTotal();
    ShardingStrategy strategy = shardingStrategyProvider.getIfAvailable();
    if (strategy == null) {
      // 理论上不会走到这里（isShardedJob 已判定），防御性处理
      log.warn("[Dispatcher] ShardingStrategy 不可用, fallback 到非分片模式: key={}", job.getJobKey());
      return executeJob(job, holdLock, triggerType, 0);
    }

    List<JobNode> onlineNodes = getOnlineNodeList();
    String localNodeId = resolveLocalNodeId();

    List<ShardAssignment> assignments;
    if (onlineNodes.isEmpty() || localNodeId == null) {
      // fallback: 无在线节点信息或本地 nodeId 未知, 本地执行全部分片
      assignments = buildLocalOnlyAssignments(shardTotal, localNodeId);
    } else {
      List<String> nodeIds =
          onlineNodes.stream().map(JobNode::getNodeId).collect(Collectors.toList());
      assignments = strategy.assign(shardTotal, nodeIds);
    }

    // 构建 nodeId → JobNode 映射，供远程派发查询节点地址
    Map<String, JobNode> nodeMap =
        onlineNodes.stream().collect(Collectors.toMap(JobNode::getNodeId, n -> n, (a, b) -> a));

    log.info(
        "[Dispatcher] 分片任务派发: key={} shardTotal={} assignments={} localNode={}",
        job.getJobKey(),
        shardTotal,
        assignments.size(),
        localNodeId);

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
   * <p>通过 {@link RemoteTaskClient} 将分片任务 HTTP 派发到选定的执行器节点。 执行器节点收到请求后调用 {@link #executeLocally}
   * 在本地执行。
   *
   * <p>降级策略：当 {@code remote.fallbackToLocal=true} 且远程派发失败时 （连接拒绝、超时、HTTP 错误），Leader 在本地执行该分片。 由
   * Redis 分布式锁保证不会重复执行（远程节点已持锁时本地执行也会跳过）。
   *
   * @param job 任务定义
   * @param assignment 分片分配结果
   * @param shardTotal 分片总数
   * @param holdLock 是否需要抢占分布式锁（MANUAL 触发为 false，其他触发为 true）
   * @param triggerType 触发类型
   * @param nodeMap 在线节点映射（nodeId → JobNode）
   * @return 执行日志 ID；派发失败且未降级返回 null
   */
  private String dispatchShardRemotely(
      Job job,
      ShardAssignment assignment,
      int shardTotal,
      boolean holdLock,
      String triggerType,
      Map<String, JobNode> nodeMap) {
    CronjobProperties.Remote remoteConfig = cronjobProperties.getRemote();
    if (!remoteConfig.isEnabled()) {
      // 远程派发未启用：本地执行该分片（兼容旧行为）
      return executeShard(job, assignment.shardIndex(), shardTotal, holdLock, triggerType);
    }
    RemoteTaskClient client = remoteTaskClientProvider.getIfAvailable();
    if (client == null) {
      log.debug(
          "[Dispatcher] RemoteTaskClient 不可用, 本地执行: key={} shard={}",
          job.getJobKey(),
          assignment.shardIndex());
      return executeShard(job, assignment.shardIndex(), shardTotal, holdLock, triggerType);
    }
    JobNode node = nodeMap.get(assignment.nodeId());
    if (node == null || node.getHost() == null || node.getPort() == null) {
      log.warn(
          "[Dispatcher] 节点信息缺失, 降级本地执行: key={} shard={} nodeId={}",
          job.getJobKey(),
          assignment.shardIndex(),
          assignment.nodeId());
      return executeShard(job, assignment.shardIndex(), shardTotal, holdLock, triggerType);
    }
    RemoteTaskRequest request =
        new RemoteTaskRequest(
            job, triggerType, assignment.shardIndex(), shardTotal, TracerUtils.getTraceId());
    String logId = client.dispatch(node, request);
    if (logId == null && remoteConfig.isFallbackToLocal()) {
      log.info(
          "[Dispatcher] 远程派发失败, 降级本地执行: key={} shard={} nodeId={}",
          job.getJobKey(),
          assignment.shardIndex(),
          assignment.nodeId());
      return executeShard(job, assignment.shardIndex(), shardTotal, holdLock, triggerType);
    }
    if (logId != null) {
      log.debug(
          "[Dispatcher] 远程分片派发成功: key={} shard={} nodeId={} logId={}",
          job.getJobKey(),
          assignment.shardIndex(),
          assignment.nodeId(),
          logId);
    }
    return logId;
  }

  /**
   * 执行单个分片（P3）。
   *
   * <p>与 {@link #executeJob} 类似，区别：
   *
   * <ul>
   *   <li>锁 key 含分片索引: {@code ydsz:job:lock:{jobKey}:shard:{shardIndex}}
   *   <li>构造 {@link ShardingContext} 传入 handler
   *   <li>不推进 next_fire_time（由 JobScanner 统一推进）
   * </ul>
   */
  private String executeShard(
      Job job, int shardIndex, int shardTotal, boolean holdLock, String triggerType) {
    String lockKey = null;
    if (holdLock) {
      lockKey = LockKeyUtil.buildJobLockKey(job.getJobKey(), shardIndex);
      Duration ttl = resolveLockTtl(job);
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, INSTANCE_ID, ttl);
      if (!Boolean.TRUE.equals(acquired)) {
        log.info("[Dispatcher] 分片锁被其他实例持有, 跳过: key={} shard={}", job.getJobKey(), shardIndex);
        return null;
      }
    }

    // P0-2: 全局并发控制 — 分片任务执行前获取全局并发配额
    boolean globalConcurrencyAcquired = tryAcquireGlobalConcurrency();
    if (!globalConcurrencyAcquired) {
      log.warn(
          "[Dispatcher] 全局并发已满, 拒绝执行分片: key={} shard={} triggerType={}",
          job.getJobKey(),
          shardIndex,
          triggerType);
      releaseJobLock(lockKey);
      return null;
    }

    notifyTaskStart();

    JobLog log0 = new JobLog();
    log0.setJobId(job.getId());
    log0.setJobKey(job.getJobKey());
    log0.setStartTime(LocalDateTime.now());
    log0.setStatus("RUNNING");
    log0.setParamsJson(job.getParamsJson());
    log0.setTraceId(TracerUtils.getTraceId());
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

    // P0-2: 初始化在线日志器（在 handler.execute 之前设置 ThreadLocal）
    JobLoggerImpl jobLogger =
        new JobLoggerImpl(
            log0.getId(),
            job.getJobKey(),
            jobLogContentServiceProvider.getIfAvailable(),
            logStreamManagerProvider.getIfAvailable());
    // P1-2: 设置任务上下文（jobId/jobKey），供 GlueJobHandler 等 handler 读取
    ShardingContext shardingCtx = new ShardingContext();
    shardingCtx.setJobId(job.getId());
    shardingCtx.setJobKey(job.getJobKey());
    shardingCtx.setLogId(log0.getId());

    // P0-P1: 使用 try-with-resources 确保 ThreadLocal 必定清理，杜绝上下文串扰
    try (ExecutionContextScope scope = ExecutionContextScope.of(jobLogger, shardingCtx)) {
      // P7-3: 记录执行开始（INCR 并发计数器 + 日执行计数器）
      recordExecutionStart(job.getTenantId());

      executeShardCore(job, shardIndex, shardTotal, lockKey, triggerType, log0, jobLogger);
    }
    return log0.getId();
  }

  /**
   * 执行分片核心逻辑（在线程上下文作用域内）。
   *
   * <p>从 {@link #executeShard} 拆分出来，使 try-with-resources 作用域清晰。
   */
  private void executeShardCore(
      Job job,
      int shardIndex,
      int shardTotal,
      String lockKey,
      String triggerType,
      JobLog log0,
      JobLoggerImpl jobLogger) {
    boolean success = false;
    Object result = null;
    try {
      JobHandler handler = resolveHandler(job);
      ShardingContext ctx = new ShardingContext();
      ctx.setShardTotal(shardTotal);
      ctx.setShardIndex(shardIndex);
      ctx.setJobId(job.getId());
      ctx.setJobKey(job.getJobKey());
      ctx.setLogId(log0.getId());
      result = handler.execute(job.getParamsJson(), ctx);
      success = true;
      log0.setResultJson(result == null ? null : YdszJson.toJson(result));
    } catch (Exception e) {
      log.error(
          "[Dispatcher] 分片任务执行失败: key={} shard={} reason={}",
          job.getJobKey(),
          shardIndex,
          e.getMessage(),
          e);
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
      jobMapper.updateStats(
          job.getId(), null, null, incFire, incSucc, incFail, success ? null : "ERROR");

      if (lockKey != null) {
        try {
          redisTemplate.execute(
              RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), INSTANCE_ID);
        } catch (Exception e) {
          log.warn("[Dispatcher] 释放分片锁失败(将等待 TTL 自动过期): key={} reason={}", lockKey, e.getMessage());
        }
      }

      // P0-2: 释放全局并发配额
      releaseGlobalConcurrency();

      // P7-3: 记录执行结束（DECR 并发计数器）
      recordExecutionEnd(job.getTenantId());

      notifyTaskComplete();
    }
    // P0-2: 刷新并清理在线日志器（在作用域退出后、ThreadLocal 已清理）
    try {
      jobLogger.flush();
    } catch (Exception e) {
      log.warn(
          "[Dispatcher] 刷新在线日志失败(不影响主流程): key={} shard={} reason={}",
          job.getJobKey(),
          shardIndex,
          e.getMessage());
    }
    // P6-2: 记录分片执行指标
    recordJobMetrics(job, triggerType, success, log0);
    // P5: 触发告警（分片级别，失败告警 + 慢任务告警）
    triggerAlerts(job, success, log0);
  }

  /**
   * 查询在线节点列表（按 nodeId 升序，保证分片分配确定性）。
   *
   * <p>P1-1: 优先使用 {@link NodeDiscoveryStrategy}（Nacos/DB），不可用时回退到 DB 查询。 P1-4: 返回完整的 {@link
   * JobNode} 列表，供远程派发获取 host/port。
   */
  private List<JobNode> getOnlineNodeList() {
    // P1-1: 优先使用节点发现策略
    NodeDiscoveryStrategy strategy = nodeDiscoveryStrategyProvider.getIfAvailable();
    if (strategy != null) {
      return strategy.getOnlineNodes();
    }
    // 回退到 DB 查询（现有逻辑，向后兼容）
    try {
      long threshold = cronjobProperties.getExecutor().getOfflineThresholdSeconds();
      LocalDateTime cutoff = LocalDateTime.now().minusSeconds(threshold);
      LambdaQueryWrapper<JobNode> wrapper = new LambdaQueryWrapper<>();
      wrapper
          .eq(JobNode::getStatus, "ONLINE")
          .ge(JobNode::getLastHeartbeat, cutoff)
          .orderByAsc(JobNode::getNodeId);
      return jobNodeMapper.selectList(wrapper);
    } catch (Exception e) {
      log.warn("[Dispatcher] 查询在线节点失败, fallback 到本地执行全部分片: reason={}", e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * P1-1: 解析当前节点 ID。
   *
   * <p>优先使用 {@link NodeDiscoveryStrategy#getLocalNodeId()}， 不可用时回退到 {@link
   * JobNodeHeartbeat#getNodeId()}。
   *
   * @return 当前节点 ID；均不可用时返回 null
   */
  private String resolveLocalNodeId() {
    NodeDiscoveryStrategy strategy = nodeDiscoveryStrategyProvider.getIfAvailable();
    if (strategy != null) {
      return strategy.getLocalNodeId();
    }
    JobNodeHeartbeat heartbeat = jobNodeHeartbeatProvider.getIfAvailable();
    return heartbeat != null ? heartbeat.getNodeId() : null;
  }

  /** P1-1: 通知心跳组件任务开始（type=nacos 时心跳组件不注册，安全跳过）。 */
  private void notifyTaskStart() {
    JobNodeHeartbeat heartbeat = jobNodeHeartbeatProvider.getIfAvailable();
    if (heartbeat != null) {
      heartbeat.onTaskStart();
    }
  }

  /** P1-1: 通知心跳组件任务完成（type=nacos 时心跳组件不注册，安全跳过）。 */
  private void notifyTaskComplete() {
    JobNodeHeartbeat heartbeat = jobNodeHeartbeatProvider.getIfAvailable();
    if (heartbeat != null) {
      heartbeat.onTaskComplete();
    }
  }

  /** 构建"本地执行全部分片"的分配方案（fallback）。 */
  private List<ShardAssignment> buildLocalOnlyAssignments(int shardTotal, String localNodeId) {
    List<ShardAssignment> result = new ArrayList<>(shardTotal);
    for (int i = 0; i < shardTotal; i++) {
      result.add(new ShardAssignment(localNodeId, i));
    }
    return result;
  }

  /**
   * P0-1: 判断调度器-执行器分离模式是否启用。
   *
   * <p>需同时满足：
   *
   * <ul>
   *   <li>{@code schedulerExecutorSeparation.enabled=true}
   *   <li>{@code remote.enabled=true}（远程派发必须可用）
   *   <li>WorkerNodeSelector Bean 可用
   * </ul>
   *
   * @return true 启用分离模式
   */
  private boolean isSchedulerExecutorSeparationEnabled() {
    return cronjobProperties.getSchedulerExecutorSeparation().isEnabled()
        && cronjobProperties.getRemote().isEnabled()
        && workerNodeSelectorProvider.getIfAvailable() != null;
  }

  /**
   * P0-1: 将非分片任务远程派发到 Worker 节点执行。
   *
   * <p>调度器-执行器分离模式下，Leader 节点通过 WorkerNodeSelector 选定 Worker 节点， 通过 RemoteTaskClient HTTP
   * 派发任务。Worker 节点收到请求后调用 {@link #executeLocally} 在本地执行。
   *
   * <p>降级策略：无可用 Worker 或远程派发失败时返回 null，调用方降级为 Leader 本地执行。
   *
   * @param job 任务定义
   * @param triggerType 触发类型
   * @return 执行日志 ID；派发失败返回 null
   */
  private String dispatchToWorker(Job job, String triggerType) {
    WorkerNodeSelector selector = workerNodeSelectorProvider.getIfAvailable();
    if (selector == null) {
      return null;
    }
    JobNode worker = selector.selectWorker();
    if (worker == null) {
      return null;
    }
    RemoteTaskClient client = remoteTaskClientProvider.getIfAvailable();
    if (client == null) {
      return null;
    }
    RemoteTaskRequest request =
        new RemoteTaskRequest(job, triggerType, -1, 1, TracerUtils.getTraceId());
    String logId = client.dispatch(worker, request);
    if (logId != null) {
      log.debug(
          "[Dispatcher] 调度器-执行器分离: 任务已派发到 Worker: key={} worker={} logId={}",
          job.getJobKey(),
          worker.getNodeId(),
          logId);
    } else {
      log.warn(
          "[Dispatcher] 调度器-执行器分离: 远程派发失败, 降级本地执行: key={} worker={}",
          job.getJobKey(),
          worker.getNodeId());
    }
    return logId;
  }

  /**
   * P1-5/P1-2/P1-3: 根据任务类型解析处理器。
   *
   * <p>路由规则：
   *
   * <ul>
   *   <li>{@code jobType=HTTP}: 返回 {@link HttpJobHandler}
   *   <li>{@code jobType=GLUE}: 返回 {@link GlueJobHandler} （通过 {@link JobExecutionContext} 获取当前
   *       jobId 加载 GLUE 代码）
   *   <li>{@code jobType=SHELL}: 返回 {@link ScriptJobHandler} （从 paramsJson 解析 language/script/args）
   *   <li>{@code jobType=BEAN} 或 null: 按 handler 字段查找 Spring Bean（默认行为）
   *   <li>其他类型: 暂不支持，降级到 BEAN 模式查找
   * </ul>
   *
   * <p>HTTP/GLUE/SHELL 处理器不可用时降级到 BEAN 模式（记录警告），保证启动兼容性。
   *
   * @param job 任务定义
   * @return 任务处理器
   * @throws NoSuchBeanDefinitionException BEAN 模式下找不到对应 Bean
   */
  private JobHandler resolveHandler(Job job) {
    String jobType = job.getJobType();
    if ("HTTP".equals(jobType)) {
      HttpJobHandler httpHandler = httpJobHandlerProvider.getIfAvailable();
      if (httpHandler != null) {
        return httpHandler;
      }
      log.warn(
          "[Dispatcher] HTTP 处理器未注册, 降级到 BEAN 模式: key={} handler={}",
          job.getJobKey(),
          job.getHandler());
    }
    if ("GLUE".equals(jobType)) {
      GlueJobHandler glueHandler = glueJobHandlerProvider.getIfAvailable();
      if (glueHandler != null) {
        // GLUE 任务从 JobExecutionContext 读取 jobId 以加载对应代码
        return glueHandler;
      }
      log.warn(
          "[Dispatcher] GLUE 处理器未注册, 降级到 BEAN 模式: key={} handler={}",
          job.getJobKey(),
          job.getHandler());
    }
    if ("SHELL".equals(jobType)) {
      ScriptJobHandler scriptHandler = scriptJobHandlerProvider.getIfAvailable();
      if (scriptHandler != null) {
        return scriptHandler;
      }
      log.warn(
          "[Dispatcher] SHELL 处理器未注册, 降级到 BEAN 模式: key={} handler={}",
          job.getJobKey(),
          job.getHandler());
    }
    // P1-6: 灰度发布路由
    String effectiveHandler = resolveCanaryHandler(job);
    return applicationContext.getBean(effectiveHandler, JobHandler.class);
  }

  /**
   * P1-6: 灰度处理器路由。
   *
   * <p>当任务配置了 canaryRatio (>0) 且 canaryHandler 非空时， 按 canaryRatio% 概率返回 canaryHandler，否则返回原
   * handler。
   *
   * @param job 任务定义
   * @return 实际使用的 handler Bean 名称
   */
  private String resolveCanaryHandler(Job job) {
    if (job.getCanaryRatio() != null
        && job.getCanaryRatio() > 0
        && job.getCanaryHandler() != null
        && !job.getCanaryHandler().isBlank()) {
      int ratio = Math.min(100, Math.max(0, job.getCanaryRatio()));
      if (RandomUtils.randomInt(100) < ratio) {
        log.info(
            "[Dispatcher] 灰度路由命中: jobKey={} canaryHandler={} ratio={}%",
            job.getJobKey(), job.getCanaryHandler(), ratio);
        return job.getCanaryHandler();
      }
    }
    return job.getHandler();
  }

  /**
   * 执行任务（核心逻辑，从 JobServiceImpl 抽取）。
   *
   * @param job 任务定义
   * @param holdLock 是否抢占分布式锁
   * @param triggerType 触发类型
   * @param retryCount 当前重试次数（0=首次执行）
   * @return 执行日志 ID；锁被持有时返回 null
   */
  private String executeJob(Job job, boolean holdLock, String triggerType, int retryCount) {
    // P1-3: DISCARD_OVERLAPPING 策略 — 存在 RUNNING 日志时直接丢弃新触发
    if ("DISCARD_OVERLAPPING".equals(job.getBlockStrategy())) {
      if (hasRunningLog(job.getId())) {
        log.info("[Dispatcher] DISCARD_OVERLAPPING 策略, 存在运行中实例, 丢弃: key={}", job.getJobKey());
        return null;
      }
    }

    String lockKey = null;
    if (holdLock) {
      lockKey = LockKeyUtil.buildJobLockKey(job.getJobKey());
      Duration ttl = resolveLockTtl(job);
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, INSTANCE_ID, ttl);
      if (!Boolean.TRUE.equals(acquired)) {
        // P1-2: 锁被持有时根据阻塞策略决定行为
        String strategy = job.getBlockStrategy();
        if ("DISCARD".equals(strategy)) {
          log.info("[Dispatcher] DISCARD 策略, 丢弃新触发: key={}", job.getJobKey());
          return null;
        }
        if ("COVER".equals(strategy)) {
          // P2-6: COVER 策略 - 中断当前执行 + 释放锁 + 派发新任务
          return executeWithCoverStrategy(job, lockKey, ttl, triggerType, retryCount);
        }
        // SERIAL（默认）和其他策略都视为跳过（无法中断正在执行的任务）
        log.info(
            "[Dispatcher] 任务已被其他实例持有锁, 跳过: key={} triggerType={} strategy={}",
            job.getJobKey(),
            triggerType,
            strategy);
        return null;
      }
      log.debug(
          "[Dispatcher] 获取分布式锁成功: key={} holder={} ttl={}ms", lockKey, INSTANCE_ID, ttl.toMillis());
    }

    // P0-2: 全局并发控制 — 任务执行前获取全局并发配额
    boolean globalConcurrencyAcquired = tryAcquireGlobalConcurrency();
    if (!globalConcurrencyAcquired) {
      log.warn("[Dispatcher] 全局并发已满, 拒绝执行: key={} triggerType={}", job.getJobKey(), triggerType);
      releaseJobLock(lockKey);
      return null;
    }

    // 通知心跳组件：任务开始
    notifyTaskStart();

    // 写开始日志
    JobLog log0 = new JobLog();
    log0.setJobId(job.getId());
    log0.setJobKey(job.getJobKey());
    log0.setStartTime(LocalDateTime.now());
    log0.setStatus("RUNNING");
    log0.setParamsJson(job.getParamsJson());
    log0.setTraceId(TracerUtils.getTraceId());
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

    // P0-2: 初始化在线日志器（在 handler.execute 之前设置 ThreadLocal）
    JobLoggerImpl jobLogger =
        new JobLoggerImpl(
            log0.getId(),
            job.getJobKey(),
            jobLogContentServiceProvider.getIfAvailable(),
            logStreamManagerProvider.getIfAvailable());
    // P1-2: 设置任务上下文（jobId/jobKey），供 GlueJobHandler 等 handler 读取
    ShardingContext shardingCtx = new ShardingContext();
    shardingCtx.setJobId(job.getId());
    shardingCtx.setJobKey(job.getJobKey());
    shardingCtx.setLogId(log0.getId());

    // P0-P1: 使用 try-with-resources 确保 ThreadLocal 必定清理，杜绝上下文串扰
    try (ExecutionContextScope scope = ExecutionContextScope.of(jobLogger, shardingCtx)) {
      // P7-3: 记录执行开始（INCR 并发计数器 + 日执行计数器）
      recordExecutionStart(job.getTenantId());

      // P3-13: 推送 TASK_STARTED WebHook 事件
      dispatchWebhookEvent("TASK_STARTED", job, log0);

      executeAndFinalize(job, lockKey, triggerType, retryCount, log0, jobLogger, shardingCtx);
    }
    return log0.getId();
  }

  /**
   * 执行任务核心逻辑并在作用域内完成收尾。
   *
   * <p>从 {@link #executeJob} 拆分出来，使 try-with-resources 作用域清晰， 同时保持原有执行语义（handler 调用 + 状态更新 + 事件发布）。
   */
  private void executeAndFinalize(
      Job job,
      String lockKey,
      String triggerType,
      int retryCount,
      JobLog log0,
      JobLoggerImpl jobLogger,
      ShardingContext shardingCtx) {
    boolean success = false;
    Object result = null;
    try {
      // P0-4: MAP/MAP_REDUCE 类型走 MapTaskExecutor
      String jobType = job.getJobType();
      if ("MAP".equals(jobType) || "MAP_REDUCE".equals(jobType)) {
        MapTaskExecutor mapExecutor = mapTaskExecutorProvider.getIfAvailable();
        if (mapExecutor != null) {
          ProcessResult mapResult = mapExecutor.executeMapJob(job, log0, triggerType);
          success = mapResult.isSuccess();
          result = mapResult.isSuccess() ? mapResult.getResult() : null;
          log0.setResultJson(result == null ? null : YdszJson.toJson(result));
          if (!success) {
            log0.setErrorMessage(mapResult.getErrorMessage());
          }
        } else {
          // MapTaskExecutor 不可用时降级到普通 BEAN 模式
          log.warn("[Dispatcher] MapTaskExecutor 未注册, 降级到 BEAN 模式: key={}", job.getJobKey());
          JobHandler handler = resolveHandler(job);
          result = handler.execute(job.getParamsJson());
          success = true;
          log0.setResultJson(result == null ? null : YdszJson.toJson(result));
        }
      } else {
        JobHandler handler = resolveHandler(job);
        result = handler.execute(job.getParamsJson());
        success = true;
        log0.setResultJson(result == null ? null : YdszJson.toJson(result));
      }
    } catch (Exception e) {
      log.error(
          "[Dispatcher] 任务执行失败: key={} handler={} reason={}",
          job.getJobKey(),
          job.getHandler(),
          e.getMessage(),
          e);
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
      LocalDateTime next = TRIGGER_CRON.equals(triggerType) ? nextFireTime(job) : null;
      // P1-6: 熔断逻辑 - 成功时不改 status（保持 NORMAL），失败时只在非重试场景改 ERROR
      String statusOnError = success ? null : "ERROR";
      jobMapper.updateStats(
          job.getId(), log0.getStartTime(), next, incFire, incSucc, incFail, statusOnError);

      // P1-6: 熔断计数（成功归零，失败递增 + 达到阈值自动暂停）
      updateCircuitBreaker(job, success);

      // 释放分布式锁（Lua 脚本安全释放）
      if (lockKey != null) {
        try {
          redisTemplate.execute(
              RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), INSTANCE_ID);
        } catch (Exception e) {
          log.warn(
              "[Dispatcher] 释放分布式锁失败(将等待 TTL 自动过期): key={} reason={}", lockKey, e.getMessage());
        }
      }

      // P0-2: 释放全局并发配额
      releaseGlobalConcurrency();

      // P7-3: 记录执行结束（DECR 并发计数器）
      recordExecutionEnd(job.getTenantId());

      // 通知心跳组件：任务结束
      notifyTaskComplete();
    }
    // P0-2: 刷新并清理在线日志器（在作用域退出后、ThreadLocal 已清理）
    try {
      jobLogger.flush();
    } catch (Exception e) {
      log.warn("[Dispatcher] 刷新在线日志失败(不影响主流程): key={} reason={}", job.getJobKey(), e.getMessage());
    }
    // P6-2: 记录任务执行指标
    recordJobMetrics(job, triggerType, success, log0);
    // P4: 发布任务完成事件，触发后继依赖任务（DagInstanceExecutor 异步监听）
    publishTaskCompleted(job, success, log0.getId());
    // P5: 触发告警（失败告警 + 慢任务告警）
    triggerAlerts(job, success, log0);
    // P3-13: 推送 WebHook 事件通知
    dispatchWebhookEvent(success ? "TASK_SUCCESS" : "TASK_FAILED", job, log0);
    // P0-2: 发布 Outbox 事件（跨模块可靠投递，消息中心据此发送告警通知）
    if (!success) {
      publishJobFailureOutboxEvent(job, log0);
    }
    // P1-1: 失败重试（非 RETRY 触发且 maxRetries > 0 且 retryCount < maxRetries）
    if (!success) {
      RetryScheduler scheduler = retrySchedulerProvider.getIfAvailable();
      if (scheduler != null) {
        scheduler.scheduleRetry(
            job, holdLock, triggerType, retryCount, this::executeJobForRetry);
      }
    }
  }

  /**
   * P3-13: 推送 WebHook 事件通知。
   *
   * <p>使用 try-catch 包裹，确保事件推送失败不影响主流程。
   *
   * @param eventType 事件类型: TASK_STARTED / TASK_SUCCESS / TASK_FAILED / TASK_TIMEOUT
   * @param job 任务定义
   * @param log0 任务日志
   */
  private void dispatchWebhookEvent(String eventType, Job job, JobLog log0) {
    WebhookEventDispatcher dispatcher = webhookEventDispatcherProvider.getIfAvailable();
    if (dispatcher == null) {
      return;
    }
    try {
      Map<String, Object> payload = new HashMap<>();
      payload.put("jobKey", job.getJobKey());
      payload.put("jobName", job.getJobName());
      payload.put("logId", log0.getId());
      payload.put("status", log0.getStatus());
      payload.put("duration", log0.getDurationMs());
      payload.put("triggerType", log0.getTriggerType());
      if (log0.getErrorMessage() != null) {
        payload.put("errorMessage", log0.getErrorMessage());
      }
      dispatcher.dispatchEvent(eventType, job.getJobKey(), payload);
    } catch (Exception e) {
      log.warn(
          "[Dispatcher] WebHook 事件推送失败(不影响主流程): eventType={} key={} reason={}",
          eventType,
          job.getJobKey(),
          e.getMessage());
    }
  }

  /**
   * P4: 发布任务完成事件，触发后继依赖任务。
   *
   * <p>使用 try-catch 包裹，确保事件发布失败不影响主流程。
   */
  private void publishTaskCompleted(Job job, boolean success, String logId) {
    try {
      TaskCompletedEvent event =
          new TaskCompletedEvent(job.getId(), job.getJobKey(), success, logId);
      eventPublisher.publishEvent(event);
    } catch (Exception e) {
      log.warn(
          "[Dispatcher] 发布任务完成事件失败(不影响主流程): key={} reason={}", job.getJobKey(), e.getMessage());
    }
  }

  /**
   * P0-2: 发布任务执行失败 Outbox 事件（跨模块可靠投递）。
   *
   * <p>消息中心订阅 {@link DomainEventTypes#JOB_EXECUTION_FAILED} 后据此发送告警通知。 OutboxService
   * 为可选依赖，未配置时安全降级（仅 DEBUG 日志）。 异常不影响主流程：投递失败仅记录 WARN。
   *
   * @param job 任务定义
   * @param log0 任务执行日志（携带 errorMessage / durationMs 等）
   */
  private void publishJobFailureOutboxEvent(Job job, JobLog log0) {
    DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      log.debug(
          "[Dispatcher] DomainEventPublisher 未配置，跳过 JOB_EXECUTION_FAILED 事件: jobKey={}",
          job.getJobKey());
      return;
    }
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("jobId", job.getId());
    metadata.put("jobKey", job.getJobKey());
    metadata.put("jobName", job.getJobName() != null ? job.getJobName() : "");
    metadata.put("logId", log0.getId());
    metadata.put("errorMessage", log0.getErrorMessage() != null ? log0.getErrorMessage() : "");
    metadata.put("triggerType", log0.getTriggerType() != null ? log0.getTriggerType() : "");
    metadata.put("durationMs", log0.getDurationMs());
    metadata.put("tenantId", job.getTenantId() != null ? job.getTenantId() : "");
    publisher.publish(
        DomainEvent.builder()
            .aggregateType("JobLog")
            .aggregateId(log0.getId())
            .eventType(DomainEventTypes.JOB_EXECUTION_FAILED)
            .metadata(metadata)
            .build());
  }

  /**
   * P2-6: COVER 阻塞策略实现。
   *
   * <p>当新任务触发时锁被持有，COVER 策略执行以下流程：
   *
   * <ol>
   *   <li>从 ydsz_job_log 查询当前 RUNNING 的日志（jobKey + status=RUNNING）
   *   <li>获取 execThreadId 和 execNodeId
   *   <li>如果 execNodeId 是当前节点：通过 Thread.interrupt() 中断执行线程，最多等待 1s
   *   <li>通过 Lua 脚本安全释放锁（仅当 lockHolder 匹配时才 delete）
   *   <li>重新获取锁并执行新任务（递归调用 executeJob）
   *   <li>中断失败（线程不响应）或远程节点任务时降级为 DISCARD，记录 warn 日志
   * </ol>
   *
   * <p><b>重要约束</b>：COVER 只能中断本节点的线程。远程节点的任务无法中断， 降级为 DISCARD（避免误删其他节点持有的锁或无谓重试）。
   *
   * <p><b>防递归</b>：使用 {@link #COVER_REDISPATCHING} ThreadLocal 标记"重新派发中"， 避免重新派发时锁仍被持有导致无限递归 COVER
   * 策略。
   *
   * @param job 任务定义
   * @param lockKey 锁 key
   * @param ttl 锁 TTL
   * @param triggerType 触发类型
   * @param retryCount 重试次数
   * @return 执行日志 ID；中断失败时返回 null
   */
  private String executeWithCoverStrategy(
      Job job, String lockKey, Duration ttl, String triggerType, int retryCount) {
    // 防递归保护：重新派发中如果锁仍被持有，直接降级 DISCARD
    if (Boolean.TRUE.equals(COVER_REDISPATCHING.get())) {
      log.warn("[Dispatcher] COVER 策略: 重新派发时锁仍被持有, 降级 DISCARD: key={}", job.getJobKey());
      return null;
    }
    log.info(
        "[Dispatcher] COVER 策略: 尝试中断当前执行: key={} triggerType={}", job.getJobKey(), triggerType);
    // 1. 查询当前 RUNNING 的日志
    JobLog runningLog = findRunningLog(job.getJobKey());
    if (runningLog == null) {
      // 无 RUNNING 日志，可能锁是异常残留（如节点崩溃未释放），尝试释放并重新执行
      log.warn("[Dispatcher] COVER 策略未找到 RUNNING 日志, 尝试释放残留锁: key={}", job.getJobKey());
      safeReleaseLock(lockKey, INSTANCE_ID);
      // 短暂 sleep 后重新派发（让锁释放生效）
      sleepBriefly(50);
      COVER_REDISPATCHING.set(true);
      try {
        return executeJob(job, true, triggerType, retryCount);
      } finally {
        COVER_REDISPATCHING.remove();
      }
    }
    // 2. 判断执行节点
    String execNodeId = runningLog.getExecNodeId();
    Long execThreadId = runningLog.getExecThreadId();
    if (execNodeId == null || !execNodeId.equals(nodeId)) {
      // 远程节点任务无法中断，降级 DISCARD
      log.warn(
          "[Dispatcher] COVER 策略降级 DISCARD: 任务在远程节点执行无法中断 key={} execNode={} currentNode={}",
          job.getJobKey(),
          execNodeId,
          nodeId);
      return null;
    }
    // 3. 中断本节点的执行线程
    boolean interrupted = interruptThread(execThreadId);
    if (!interrupted) {
      log.warn(
          "[Dispatcher] COVER 策略降级 DISCARD: 中断线程失败 key={} threadId={}",
          job.getJobKey(),
          execThreadId);
      return null;
    }
    // 4. 通过 Lua 脚本安全释放锁（仅当 lockHolder 匹配时才 delete）
    String lockHolder = runningLog.getLockHolder();
    String releaseHolder = (lockHolder != null) ? lockHolder : INSTANCE_ID;
    safeReleaseLock(lockKey, releaseHolder);
    // 5. 短暂 sleep 避免竞态（让被中断的线程有机会释放资源），然后重新派发新任务
    sleepBriefly(50);
    log.info("[Dispatcher] COVER 策略: 已中断旧任务, 重新派发新任务: key={}", job.getJobKey());
    // 锁已释放，executeJob 会重新走 setIfAbsent 流程获取锁
    COVER_REDISPATCHING.set(true);
    try {
      return executeJob(job, true, triggerType, retryCount);
    } finally {
      COVER_REDISPATCHING.remove();
    }
  }

  /**
   * P2-6: 查询当前 RUNNING 状态的日志（按 jobKey）。
   *
   * @param jobKey 任务 KEY
   * @return RUNNING 日志；无记录时返回 null
   */
  private JobLog findRunningLog(String jobKey) {
    try {
      LambdaQueryWrapper<JobLog> wrapper = new LambdaQueryWrapper<>();
      wrapper
          .eq(JobLog::getJobKey, jobKey)
          .eq(JobLog::getStatus, "RUNNING")
          .eq(JobLog::getDeleted, 0)
          .orderByDesc(JobLog::getCreatedAt)
          .last("LIMIT 1");
      return jobLogMapper.selectOne(wrapper);
    } catch (Exception e) {
      log.warn(
          "[Dispatcher] 查询 RUNNING 日志失败(降级 DISCARD): key={} reason={}", jobKey, e.getMessage());
      return null;
    }
  }

  /**
   * P2-6: 中断指定线程。
   *
   * <p>通过 {@link Thread#getAllStackTraces()} 遍历所有线程，按 threadId 匹配。 找到后调用 {@link
   * Thread#interrupt()}，等待最多 1s 让线程响应中断。
   *
   * @param threadId 线程 ID
   * @return true 中断成功；false 线程未找到或未在 1s 内响应
   */
  private boolean interruptThread(Long threadId) {
    if (threadId == null) {
      return false;
    }
    Thread target = null;
    for (Thread t : Thread.getAllStackTraces().keySet()) {
      if (t.threadId() == threadId) {
        target = t;
        break;
      }
    }
    if (target == null) {
      log.warn("[Dispatcher] COVER 中断: 线程不存在(可能已结束): threadId={}", threadId);
      return false;
    }
    target.interrupt();
    // 等待最多 1s 让线程响应中断
    long deadline = System.currentTimeMillis() + 1000;
    while (System.currentTimeMillis() < deadline) {
      if (!target.isAlive()) {
        return true;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    // 线程仍在运行（未响应中断），返回 false 让调用方降级
    return !target.isAlive();
  }

  /**
   * P2-6: 通过 Lua 脚本安全释放锁（仅当 lockHolder 匹配时才 delete）。
   *
   * @param lockKey 锁 key
   * @param lockHolder 持锁者标识
   */
  private void safeReleaseLock(String lockKey, String lockHolder) {
    try {
      redisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), lockHolder);
    } catch (Exception e) {
      log.warn("[Dispatcher] COVER 释放锁失败(将等待 TTL 自动过期): key={} reason={}", lockKey, e.getMessage());
    }
  }

  /**
   * P2-6: 短暂 sleep（避免竞态，让被中断的线程有机会释放资源）。
   *
   * @param millis 毫秒数
   */
  private void sleepBriefly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * P5: 触发告警。
   *
   * <p>根据任务执行结果触发相应告警：
   *
   * <ul>
   *   <li>失败时触发 {@link AlertType#FAIL} 告警
   *   <li>成功时触发 {@link AlertType#SLOW} 告警（triggerValue=耗时毫秒，由规则阈值判定是否实际告警）
   * </ul>
   *
   * <p>使用 try-catch 包裹，确保告警触发失败不影响主流程。
   *
   * @param job 任务定义
   * @param success 是否执行成功
   * @param log0 任务日志（含耗时信息）
   */
  private void triggerAlerts(Job job, boolean success, JobLog log0) {
    AlertTrigger alertTrigger = alertTriggerProvider.getIfAvailable();
    if (alertTrigger == null) {
      return;
    }
    try {
      String triggerValue =
          log0.getDurationMs() != null ? String.valueOf(log0.getDurationMs()) : null;
      AlertContext context =
          AlertContext.of(
              success ? AlertType.SLOW : AlertType.FAIL,
              job.getId(),
              job.getJobKey(),
              job.getJobName(),
              log0.getId(),
              triggerValue,
              log0.getErrorMessage(),
              log0.getTraceId(),
              job.getTenantId());
      alertTrigger.trigger(context);
    } catch (Exception e) {
      log.warn("[Dispatcher] 触发告警失败(不影响主流程): key={} reason={}", job.getJobKey(), e.getMessage());
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
      log.debug(
          "[Dispatcher] recordExecutionStart 失败(不影响主流程): tenant={} reason={}",
          tenantId,
          e.getMessage());
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
      log.debug(
          "[Dispatcher] recordExecutionEnd 失败(不影响主流程): tenant={} reason={}",
          tenantId,
          e.getMessage());
    }
  }

  /**
   * P0-2: 尝试获取全局并发配额。
   *
   * <p>通过 {@link GlobalConcurrencyController#tryAcquire()} 原子递增全局并发计数器，
   * 限制整个集群同时执行的任务总数。GlobalConcurrencyController 不可用时降级放行。
   *
   * @return true 获取成功或控制器不可用（降级放行）；false 全局并发已满
   */
  private boolean tryAcquireGlobalConcurrency() {
    GlobalConcurrencyController controller = globalConcurrencyControllerProvider.getIfAvailable();
    if (controller == null) {
      return true;
    }
    return controller.tryAcquire();
  }

  /**
   * P0-2: 释放全局并发配额。
   *
   * <p>通过 {@link GlobalConcurrencyController#release()} 原子递减全局并发计数器。 GlobalConcurrencyController
   * 不可用时安全跳过。
   */
  private void releaseGlobalConcurrency() {
    GlobalConcurrencyController controller = globalConcurrencyControllerProvider.getIfAvailable();
    if (controller == null) {
      return;
    }
    controller.release();
  }

  /**
   * 安全释放任务持有的分布式锁（Lua 脚本原子释放）。
   *
   * <p>在全局并发控制拒绝执行或需要提前退出时调用，避免锁泄漏。
   *
   * @param lockKey 锁 key（为 null 时跳过）
   */
  private void releaseJobLock(String lockKey) {
    if (lockKey == null) {
      return;
    }
    try {
      redisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), INSTANCE_ID);
    } catch (Exception e) {
      log.warn("[Dispatcher] 释放分布式锁失败(将等待 TTL 自动过期): key={} reason={}", lockKey, e.getMessage());
    }
  }

  /**
   * P6-2: 记录任务执行指标。
   *
   * <p>使用 try-catch 包裹，确保指标记录失败不影响主流程。
   *
   * @param job 任务定义
   * @param triggerType 触发类型
   * @param success 是否执行成功
   * @param log0 任务日志（含耗时信息）
   */
  private void recordJobMetrics(Job job, String triggerType, boolean success, JobLog log0) {
    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    if (metrics == null) {
      return;
    }
    try {
      String status = success ? "SUCCESS" : "FAILED";
      metrics.incJobDispatched(triggerType, status);
      metrics.recordJobDuration(
          job.getJobKey(), status, log0.getDurationMs() != null ? log0.getDurationMs() : 0L);
      if (!success) {
        metrics.incJobFailed(job.getJobKey());
      }
    } catch (Exception e) {
      log.debug("[Dispatcher] 指标记录失败(不影响主流程): key={} reason={}", job.getJobKey(), e.getMessage());
    }
  }

  /**
   * 定时上报线程池活跃度指标（替代原 AdaptiveBatchScheduler 的 updatePoolActive 调用）。
   *
   * <p>每 10 秒采集一次任务执行线程池的活跃率，供系统负载评分计算使用。
   */
  @Scheduled(fixedDelay = 10_000L)
  public void reportThreadPoolMetrics() {
    if (taskExecutorPool == null) {
      return;
    }
    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    if (metrics == null) {
      return;
    }
    metrics.updatePoolActive(taskExecutorPool.getActiveCount(), taskExecutorPool.getMaximumPoolSize());
  }

  /** 解析任务实际使用的锁 TTL。 */
  private Duration resolveLockTtl(Job job) {
    Duration taskLevel = null;
    if (job.getLockTtlMs() != null && job.getLockTtlMs() > 0) {
      taskLevel = Duration.ofMillis(job.getLockTtlMs());
    }
    return cronjobProperties.normalizeTtl(taskLevel);
  }

  /**
   * 计算下次触发时间（P2-8: 支持任务级时区）。
   *
   * <p>优先使用 {@link Job#getTimezone()}，为空时回退到默认时区 Asia/Shanghai。
   *
   * @param job 任务定义（含 cron 表达式和时区）
   * @return 下次触发时间；表达式非法时抛 SysException
   */
  private LocalDateTime nextFireTime(Job job) {
    try {
      // P2-8: 任务级时区，null 使用默认 Asia/Shanghai
      String tz = job.getTimezone() != null ? job.getTimezone() : "Asia/Shanghai";
      CronTrigger trigger = new CronTrigger(job.getCronExpression(), TimeZone.getTimeZone(tz));
      TriggerContext ctx = new SimpleTriggerContext();
      Instant next = trigger.nextExecution(ctx);
      return next == null ? null : LocalDateTime.ofInstant(next, ZoneId.systemDefault());
    } catch (IllegalArgumentException e) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_5d0044ca")
          .params(e.getMessage())
          .build();
    }
  }

  /**
   * 判断任务是否存在 RUNNING 状态的执行日志。
   *
   * <p>用于 DISCARD_OVERLAPPING 策略：上一次执行未完成时丢弃新触发。
   *
   * @param jobId 任务 ID
   * @return true 表示存在 RUNNING 日志
   */
  private boolean hasRunningLog(Long jobId) {
    Long count =
        jobLogMapper.selectCount(
            new LambdaQueryWrapper<JobLog>()
                .eq(JobLog::getJobId, jobId)
                .eq(JobLog::getStatus, "RUNNING")
                .eq(JobLog::getDeleted, 0));
    return count != null && count > 0;
  }

  private static String initInstanceId() {
    String name = ManagementFactory.getRuntimeMXBean().getName();
    return name != null ? name : "unknown:" + ProcessHandle.current().pid();
  }

  /**
   * 重试执行入口（供 RetryScheduler 回调）。
   *
   * @param job 任务定义
   * @param holdLock 是否持锁
   * @param retryCount 当前重试次数
   */
  private void executeJobForRetry(Job job, boolean holdLock, int retryCount) {
    executeJob(job, holdLock, TRIGGER_RETRY, retryCount);
  }

  /**
   * P1-6: 熔断计数（成功归零，失败递增 + 达到阈值自动暂停）。
   *
   * @param job 任务定义
   * @param success 是否执行成功
   */
  private void updateCircuitBreaker(Job job, boolean success) {
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
            log.warn(
                "[Dispatcher] 任务熔断, 自动暂停: key={} consecutiveFails={}/{}",
                job.getJobKey(),
                current,
                maxFails);
          }
        }
      }
    } catch (Exception e) {
      log.warn("[Dispatcher] 熔断计数更新失败(不影响主流程): key={} reason={}", job.getJobKey(), e.getMessage());
    }
  }

  /**
   * P0-2/P0-5: 初始化执行节点 ID（hostname:port）。
   *
   * <p>在 @PostConstruct 中调用，确保 serverPort 已通过 @Value 注入。
   */
  @PostConstruct
  private void initNodeId() {
    try {
      String hostname = InetAddress.getLocalHost().getHostName();
      this.nodeId = hostname + ":" + serverPort;
    } catch (Exception e) {
      this.nodeId = INSTANCE_ID;
    }
    // P1-7: 初始化任务执行线程池（隔离调度线程与执行线程）
    // P0-3: 使用 PriorityBlockingQueue 实现优先级调度
    CronjobProperties.Executor execConfig = cronjobProperties.getExecutor();
    int corePoolSize = Math.max(1, execConfig.getMaxConcurrent());
    int maxPoolSize = Math.max(corePoolSize, execConfig.getMaxConcurrent());
    int queueCapacity = Math.max(0, execConfig.getExecutorQueueCapacity());
    BlockingQueue<Runnable> workQueue =
        queueCapacity == 0 ? new SynchronousQueue<>() : new PriorityBlockingQueue<>();
    AtomicInteger threadCounter = new AtomicInteger(0);
    this.taskExecutorPool =
        new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L,
            TimeUnit.SECONDS,
            workQueue,
            r -> {
              Thread t =
                  new Thread(r, execConfig.getThreadNamePrefix() + threadCounter.incrementAndGet());
              t.setDaemon(true);
              return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());
    // P1-A2: 注册线程池到注册表，供热更新监听器统一访问
    registerPoolsToRegistry();
    log.info(
        "[Dispatcher] 节点 ID 初始化完成: nodeId={} instanceId={} serverPort={}",
        nodeId,
        INSTANCE_ID,
        serverPort);
    log.info(
        "[Dispatcher] P1-7 执行线程池初始化: core={} max={} queue={} policy=CallerRunsPolicy",
        corePoolSize,
        maxPoolSize,
        queueCapacity);
  }

  /**
   * P1-A2: 将本地创建的线程池注册到 {@link CronjobThreadPoolRegistry}。
   *
   * <p>仅在注册表可用时执行（standalone 模式下可能未注册该 Bean）。 注册后 ThreadPoolHotUpdateListener 可直接通过注册表获取线程池引用，
   * 消除反射耦合。
   */
  private void registerPoolsToRegistry() {
    CronjobThreadPoolRegistry registry = threadPoolRegistryProvider.getIfAvailable();
    if (registry == null) {
      log.debug("[ThreadPoolRegistry] 注册表不可用, 跳过线程池注册");
      return;
    }
    registry.register(CronjobThreadPoolRegistry.GLOBAL_EXECUTOR, taskExecutorPool);
    log.info(
        "[ThreadPoolRegistry] 线程池已注册到注册表: global={}",
        CronjobThreadPoolRegistry.GLOBAL_EXECUTOR);
  }

  @PreDestroy
  private void shutdownRetryScheduler() {
    // P1-7: 关闭任务执行线程池
    if (taskExecutorPool != null) {
      taskExecutorPool.shutdown();
      try {
        if (!taskExecutorPool.awaitTermination(
            cronjobProperties.getExecutor().getDrainTimeoutSeconds(), TimeUnit.SECONDS)) {
          taskExecutorPool.shutdownNow();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        taskExecutorPool.shutdownNow();
      }
    }
  }
}
