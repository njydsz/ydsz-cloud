package com.njydsz.cronjob.server.service.impl.job;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.cronjob.domain.dto.BatchResult;
import com.njydsz.cronjob.domain.dto.post.JobPostDTO;
import com.njydsz.cronjob.domain.dto.put.JobPutDTO;
import com.njydsz.cronjob.domain.job.JobHandler;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.JobLockManager;
import com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher;
import com.njydsz.cronjob.server.core.dispatch.TaskDispatcher;
import com.njydsz.cronjob.server.core.scheduler.NextFireTimeCalculator;
import com.njydsz.cronjob.server.core.scheduler.ScheduleType;
import com.njydsz.cronjob.server.service.job.JobHistoryService;
import com.njydsz.cronjob.server.service.job.JobService;
import com.njydsz.cronjob.server.service.job.TenantQuotaService;

/**
 * 任务调度服务实现
 *
 * <p>P1-7 重构：支持 Leader 模式与 Leaderless 模式双轨运行。
 *
 * <ul>
 *   <li>{@code ydsz.cronjob.leader.enabled=false}（默认）：每节点独立 TaskScheduler 注册 CronTrigger， 通过 Redis
 *       SET NX EX 锁防止重复执行（P0 行为保持不变）
 *   <li>{@code ydsz.cronjob.leader.enabled=true}：仅 Leader 节点扫描 ydsz_job 并派发任务， Follower 节点只注册心跳、不注册
 *       CronTrigger，避免重复扫描
 * </ul>
 *
 * <p>手动触发（{@link #trigger(String, boolean)}）始终走 {@link TaskDispatcher}（如果可用）， 否则回退到内部 {@link
 * #executeJob(JobVO, boolean)} 旧路径。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService, ApplicationRunner {

  /** 任务定义 Repository */
  private final JobRepository jobRepository;

  /** 任务日志 Repository */
  private final JobLogRepository jobLogRepository;

  /** Spring 应用上下文（用于按 Bean 名称获取 JobHandler） */
  private final ApplicationContext applicationContext;

  /** Redis 模板（用于分布式锁） */
  private final RedisTemplate<String, Object> redisTemplate;

  /** 调度配置属性（P0-4: 锁 TTL 等可配置项） */
  private final CronjobProperties cronjobProperties;

  /** 任务锁管理器（委托 ydsz-common-lock 公共模块，复用 WatchDog / 指标等能力） */
  private final JobLockManager jobLockManager;

  /** P1-3: 下次触发时间计算器（统一 cron 解析与时区处理） */
  private final NextFireTimeCalculator nextFireTimeCalculator;

  /**
   * 任务派发器（P1-7 可选注入）。
   *
   * <p>Leader 模式启用时由 {@link DefaultTaskDispatcher} 提供； Leaderless 模式下若未注册 Dispatcher 则回退到内部 {@link
   * #executeJob(JobVO, boolean)} 旧路径。
   */
  private final ObjectProvider<TaskDispatcher> taskDispatcherProvider;

  /**
   * 租户级配额服务（P7-2 新增）。
   *
   * <p>用于在任务创建时检查租户任务数配额，防止 noisy neighbor 问题。 配额检查默认禁用（{@code
   * ydsz.cronjob.quota.enabled=false}），启用后生效。
   */
  private final TenantQuotaService tenantQuotaService;

  /**
   * 任务历史版本服务（P1-6 可选注入）。
   *
   * <p>用于在任务配置更新前自动保存历史快照，支持版本对比和一键回滚。 同时记录版本变更快照， 统一版本管理入口。 通过 ObjectProvider 可选注入，避免循环依赖且便于测试。
   */
  private final ObjectProvider<JobHistoryService> jobHistoryServiceProvider;

  /**
   * 搜索索引事件桥接器（可选注入）。
   *
   * <p>用于在任务数据变更时异步同步到 ydsz-common-search 统一搜索索引。 通过 ObjectProvider 可选注入，当搜索模块未引入时安全降级为无操作。
   */
  private final ObjectProvider<SearchIndexEventBridge> searchIndexEventBridgeProvider;

  /** 调度器 */
  private TaskScheduler taskScheduler;

  /** 已调度的任务: jobKey -> Future */
  private final Map<String, ScheduledFuture<?>> scheduledMap = new ConcurrentHashMap<>();

  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  // ==================== 分布式锁常量 ====================

  /** 调度时区（多时区部署时统一为 Asia/Shanghai，避免触发时间漂移） */
  private static final TimeZone SCHEDULE_TIMEZONE = TimeZone.getTimeZone("Asia/Shanghai");

  /** 当前实例标识（hostname:pid），用于锁值和安全释放 */
  private static final String INSTANCE_ID = initInstanceId();

  /** Lua 脚本: 安全释放锁（仅当 value 匹配时才 delete） */
  private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = initReleaseScript();

  /**
   * 初始化当前实例标识
   *
   * @return 实例标识（hostname:pid）
   */
  private static String initInstanceId() {
    String name = ManagementFactory.getRuntimeMXBean().getName();
    return name != null ? name : "unknown:" + ProcessHandle.current().pid();
  }

  /**
   * 初始化安全释放锁的 Lua 脚本（仅当 value 匹配时才 delete）
   *
   * @return Redis Lua 脚本
   */
  private static DefaultRedisScript<Long> initReleaseScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
    script.setResultType(Long.class);
    return script;
  }

  // ==================== VO/DTO <-> Entity 转换 ====================

  private JobVO voToJob(JobVO vo) {
    JobVO j = new JobVO();
    j.setId(vo.getId());
    j.setJobKey(vo.getJobKey());
    j.setJobName(vo.getJobName());
    j.setJobGroup(vo.getJobGroup());
    j.setCronExpression(vo.getCronExpression());
    j.setHandler(vo.getHandler());
    j.setParamsJson(vo.getParamsJson());
    j.setStatus(vo.getStatus());
    j.setJobRemark(vo.getJobRemark());
    j.setScheduleType(vo.getScheduleType());
    j.setFixedRateMs(vo.getFixedRateMs());
    j.setFixedDelayMs(vo.getFixedDelayMs());
    j.setNextFireTime(vo.getNextFireTime());
    j.setLockTtlMs(vo.getLockTtlMs());
    j.setTimeoutMs(vo.getTimeoutMs());
    j.setMisfirePolicy(vo.getMisfirePolicy());
    j.setShardTotal(vo.getShardTotal());
    j.setSlowThresholdMs(vo.getSlowThresholdMs());
    j.setCluster(vo.getCluster());
    j.setTimezone(vo.getTimezone());
    j.setFireCount(vo.getFireCount());
    j.setSuccessCount(vo.getSuccessCount());
    j.setFailCount(vo.getFailCount());
    j.setVersion(vo.getVersion());
    j.setTenantId(vo.getTenantId());
    j.setCreatedAt(vo.getCreatedAt());
    j.setUpdatedAt(vo.getUpdatedAt());
    j.setCreatedBy(vo.getCreatedBy());
    j.setUpdatedBy(vo.getUpdatedBy());
    return j;
  }

  private JobVO dtoToJob(JobPostDTO dto) {
    JobVO j = new JobVO();
    j.setId(dto.getId());
    j.setJobKey(dto.getJobKey());
    j.setJobName(dto.getJobName());
    j.setJobGroup(dto.getJobGroup());
    j.setCronExpression(dto.getCronExpression());
    j.setHandler(dto.getHandler());
    j.setParamsJson(dto.getParamsJson());
    j.setStatus(dto.getStatus());
    j.setJobRemark(dto.getRemark());
    j.setScheduleType(dto.getScheduleType());
    j.setFixedRateMs(dto.getFixedRateMs());
    j.setFixedDelayMs(dto.getFixedDelayMs());
    j.setLockTtlMs(dto.getLockTtlMs());
    j.setTimeoutMs(dto.getTimeoutMs());
    j.setMisfirePolicy(dto.getMisfirePolicy());
    j.setShardTotal(dto.getShardTotal());
    j.setSlowThresholdMs(dto.getSlowThresholdMs());
    j.setCluster(dto.getCluster());
    j.setTimezone(dto.getTimezone());
    j.setTenantId(dto.getTenantId());
    j.setMaxRetries(dto.getMaxRetries());
    j.setRetryIntervalMs(dto.getRetryIntervalMs());
    return j;
  }

  private JobVO dtoToJob(JobPutDTO dto) {
    JobVO j = new JobVO();
    j.setId(dto.getId());
    j.setJobKey(dto.getJobKey());
    j.setJobName(dto.getJobName());
    j.setJobGroup(dto.getJobGroup());
    j.setCronExpression(dto.getCronExpression());
    j.setHandler(dto.getHandler());
    j.setParamsJson(dto.getParamsJson());
    j.setStatus(dto.getStatus());
    j.setJobRemark(dto.getJobRemark());
    j.setScheduleType(dto.getScheduleType());
    j.setFixedRateMs(dto.getFixedRateMs());
    j.setFixedDelayMs(dto.getFixedDelayMs());
    j.setLockTtlMs(dto.getLockTtlMs());
    j.setTimeoutMs(dto.getTimeoutMs());
    j.setMisfirePolicy(dto.getMisfirePolicy());
    j.setShardTotal(dto.getShardTotal());
    j.setSlowThresholdMs(dto.getSlowThresholdMs());
    j.setCluster(dto.getCluster());
    j.setTimezone(dto.getTimezone());
    j.setTenantId(dto.getTenantId());
    return j;
  }

  private JobVO jobToVo(JobVO j) {
    JobVO vo = new JobVO();
    vo.setId(j.getId());
    vo.setJobKey(j.getJobKey());
    vo.setJobName(j.getJobName());
    vo.setJobGroup(j.getJobGroup());
    vo.setCronExpression(j.getCronExpression());
    vo.setHandler(j.getHandler());
    vo.setParamsJson(j.getParamsJson());
    vo.setStatus(j.getStatus());
    vo.setJobRemark(j.getJobRemark());
    vo.setScheduleType(j.getScheduleType());
    vo.setFixedRateMs(j.getFixedRateMs());
    vo.setFixedDelayMs(j.getFixedDelayMs());
    vo.setNextFireTime(j.getNextFireTime());
    vo.setLockTtlMs(j.getLockTtlMs());
    vo.setTimeoutMs(j.getTimeoutMs());
    vo.setMisfirePolicy(j.getMisfirePolicy());
    vo.setShardTotal(j.getShardTotal());
    vo.setSlowThresholdMs(j.getSlowThresholdMs());
    vo.setCluster(j.getCluster());
    vo.setTimezone(j.getTimezone());
    vo.setFireCount(j.getFireCount());
    vo.setSuccessCount(j.getSuccessCount());
    vo.setFailCount(j.getFailCount());
    vo.setVersion(j.getVersion());
    vo.setTenantId(j.getTenantId());
    vo.setCreatedAt(j.getCreatedAt());
    vo.setUpdatedAt(j.getUpdatedAt());
    vo.setCreatedBy(j.getCreatedBy());
    vo.setUpdatedBy(j.getUpdatedBy());
    return vo;
  }

  /** 初始化任务调度器（线程池大小可配置，关闭时等待任务完成） */
  @PostConstruct
  public void initScheduler() {
    ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
    s.setPoolSize(cronjobProperties.getSchedulerPoolSize());
    s.setThreadNamePrefix("ydsz-job-");
    s.setWaitForTasksToCompleteOnShutdown(true);
    s.setAwaitTerminationSeconds(cronjobProperties.getSchedulerAwaitTerminationSeconds());
    s.initialize();
    this.taskScheduler = s;
    log.info("[Cronjob] 任务调度器初始化完成, poolSize={}", cronjobProperties.getSchedulerPoolSize());
  }

  /** 销毁调度器，取消所有已调度任务 */
  @PreDestroy
  public void destroy() {
    scheduledMap.values().forEach(f -> f.cancel(true));
    scheduledMap.clear();
    log.info("[Cronjob] 任务调度器已关闭");
  }

  /**
   * 应用启动回调。
   *
   * <p>P1-7 双轨：
   *
   * <ul>
   *   <li>Leaderless 模式：调用 {@link #loadOnStartup()} 加载所有 NORMAL 任务到 TaskScheduler
   *   <li>Leader 模式：跳过本地注册（由 {@link com.njydsz.cronjob.server.core.dispatch.JobScanner} 接管扫描）
   * </ul>
   *
   * @param args 启动参数
   */
  @Override
  public void run(ApplicationArguments args) {
    if (cronjobProperties.getLeader().isEnabled()) {
      log.info(
          "[Cronjob] Leader 模式启用, 跳过本地 CronTrigger 注册（由 JobScanner 接管）: role={}",
          cronjobProperties.getLeader().getRole());
      return;
    }
    try {
      loadOnStartup();
    } catch (Exception e) {
      log.error("[Cronjob] 启动加载任务失败: {}", e.getMessage(), e);
    }
  }

  /** 应用启动时加载所有 NORMAL 任务 */
  @Override
  @Transactional(readOnly = true)
  public void loadOnStartup() {
    List<JobVO> list = jobRepository.findAllNormal();
    log.info("[Cronjob] 启动加载任务数量: {}", list.size());
    for (JobVO vo : list) {
      try {
        register(voToJob(vo));
      } catch (Exception e) {
        log.warn("[Cronjob] 注册任务失败: key={} reason={}", vo.getJobKey(), e.getMessage());
      }
    }
  }

  /**
   * 新增任务
   *
   * <p>根据 {@code scheduleType} 决定是否计算 nextFireTime：
   *
   * <ul>
   *   <li>CRON: 计算 nextFireTime（由 JobScanner 扫描）
   *   <li>FIXED_RATE / FIXED_DELAY: 不计算 nextFireTime（由本地 TaskScheduler 管理）
   *   <li>API: 不计算 nextFireTime（仅手动触发）
   * </ul>
   *
   * @param dto 任务创建请求
   * @return 新增任务 ID
   * @throws SysException 当 jobKey 已存在或参数非法时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String create(JobPostDTO dto) {
    // P0-3: scheduleType 默认为 CRON（向后兼容）
    if (!StringUtils.hasText(dto.getScheduleType())) {
      dto.setScheduleType(ScheduleType.CRON.name());
    }
    validate(dto);
    if (jobRepository.findByJobKey(dto.getJobKey()).isPresent()) {
      throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_7e5ef640")
          .params(dto.getJobKey())
          .build();
    }
    if (dto.getStatus() == null) {
      dto.setStatus("NORMAL");
    }
    if (dto.getJobGroup() == null) {
      dto.setJobGroup("DEFAULT");
    }
    if (dto.getTenantId() == null) {
      dto.setTenantId(TenantContextHolder.getTenantId());
    }
    // P7-2: 租户级配额检查（在 insert 之前调用，避免任务计数提前增加导致误判）
    tenantQuotaService.checkJobQuota(dto.getTenantId());
    // P3 收尾: 分片/misfire 默认值规整
    if (dto.getShardTotal() == null || dto.getShardTotal() < 1) {
      dto.setShardTotal(1);
    }
    if (!StringUtils.hasText(dto.getMisfirePolicy())) {
      dto.setMisfirePolicy("FIRE_NOW");
    }
    // 先插入 DB 获取 ID
    String id = jobRepository.insert(dto);
    // 读取完整 VO 用于后续调度器注册
    JobVO vo = jobRepository.findById(id).orElseThrow(() -> SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
        .message("error.cronjob.msg_create_readback")
        .build());
    // P1-2: CRON / FIXED_RATE / FIXED_DELAY 计算 next_fire_time
    JobVO j = voToJob(vo);
    ScheduleType type = ScheduleType.parse(j.getScheduleType());
    if (type != ScheduleType.API) {
      j.setNextFireTime(nextFireTime(j));
      jobRepository.updateById(j);
    }
    if ("NORMAL".equals(j.getStatus())) {
      register(j);
    }
    log.info(
        "[Cronjob] 创建任务: key={} scheduleType={} cron={} handler={} shardTotal={}",
        j.getJobKey(), j.getScheduleType(), j.getCronExpression(), j.getHandler(),
        j.getShardTotal());
    // P1-6: 记录版本变更快照（统一走 JobHistoryService）
    JobHistoryService historyService = jobHistoryServiceProvider.getIfAvailable();
    if (historyService != null) {
      historyService.recordVersionChange(null, j, "CREATE", j.getCreatedBy(), "任务创建");
    }
    // 同步到统一搜索索引（ydsz-common-search）
    syncSearchIndex("job", j);
    return id;
  }

  /**
   * 更新任务
   *
   * <p>P0-3: 同步 scheduleType/fixedRateMs/fixedDelayMs 字段，并按新调度类型重新注册。
   *
   * @param dto 任务更新请求
   * @throws SysException 当任务不存在或 cron 表达式非法时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void update(JobPutDTO dto) {
    if (dto.getId() == null) {
      throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_ce91ca69")
          .build();
    }
    JobVO existsVo = jobRepository.findById(dto.getId()).orElseThrow(() -> SysException.builder()
        .resultCode(YdszResultCode.NOT_FOUND)
        .message("error.cronjob.msg_c0d8369f")
        .build());
    JobVO exists = voToJob(existsVo);
    // P1-6: 保存历史版本（在更新之前保存当前快照）
    JobHistoryService historyService = jobHistoryServiceProvider.getIfAvailable();
    if (historyService != null) {
      historyService.saveHistory(exists, dto.getUpdatedBy());
    }
    // P0-3: 同步 scheduleType（空值不覆盖，保持原值）
    if (StringUtils.hasText(dto.getScheduleType())) {
      exists.setScheduleType(dto.getScheduleType());
    }
    // P0-3: 同步 fixedRateMs/fixedDelayMs（允许清空为 null）
    exists.setFixedRateMs(dto.getFixedRateMs());
    exists.setFixedDelayMs(dto.getFixedDelayMs());
    // P2-8: 同步时区（允许清空为 null，使用默认时区）
    exists.setTimezone(dto.getTimezone());
    // 按新调度类型校验
    ScheduleType type = ScheduleType.parse(exists.getScheduleType());
    if (type == ScheduleType.CRON) {
      if (StringUtils.hasText(dto.getCronExpression())) {
        validateCron(dto.getCronExpression());
      }
      if (StringUtils.hasText(dto.getCronExpression())) {
        exists.setNextFireTime(nextFireTime(exists));
      }
    } else if (type == ScheduleType.FIXED_RATE) {
      if (exists.getFixedRateMs() == null || exists.getFixedRateMs() <= 0) {
        throw SysException.builder()
              .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.cronjob.msg_5d0044ca")
            .params("fixedRateMs 必须为正数")
            .build();
      }
      exists.setNextFireTime(nextFireTime(exists));
    } else if (type == ScheduleType.FIXED_DELAY) {
      if (exists.getFixedDelayMs() == null || exists.getFixedDelayMs() <= 0) {
        throw SysException.builder()
              .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.cronjob.msg_5d0044ca")
            .params("fixedDelayMs 必须为正数")
            .build();
      }
      exists.setNextFireTime(nextFireTime(exists));
    }
    if (StringUtils.hasText(dto.getCronExpression())) {
      exists.setCronExpression(dto.getCronExpression());
    }
    if (StringUtils.hasText(dto.getHandler())) {
      exists.setHandler(dto.getHandler());
    }
    if (StringUtils.hasText(dto.getJobName())) {
      exists.setJobName(dto.getJobName());
    }
    if (StringUtils.hasText(dto.getJobGroup())) {
      exists.setJobGroup(dto.getJobGroup());
    }
    if (dto.getParamsJson() != null) {
      exists.setParamsJson(dto.getParamsJson());
    }
    if (StringUtils.hasText(dto.getStatus())) {
      exists.setStatus(dto.getStatus());
    }
    if (dto.getJobRemark() != null) {
      exists.setJobRemark(dto.getJobRemark());
    }
    // P0/P2/P3 收尾: 同步 lockTtlMs/timeoutMs/misfirePolicy/shardTotal
    if (dto.getLockTtlMs() != null) {
      exists.setLockTtlMs(dto.getLockTtlMs());
    }
    if (dto.getTimeoutMs() != null) {
      exists.setTimeoutMs(dto.getTimeoutMs());
    }
    if (StringUtils.hasText(dto.getMisfirePolicy())) {
      exists.setMisfirePolicy(dto.getMisfirePolicy());
    }
    if (dto.getShardTotal() != null && dto.getShardTotal() >= 1) {
      exists.setShardTotal(dto.getShardTotal());
    }
    // P6-3: 同步慢任务阈值（null 表示不检测，允许清空）
    exists.setSlowThresholdMs(dto.getSlowThresholdMs());
    // P3-12: 同步目标集群（允许清空为 null，使用本地集群）
    exists.setCluster(dto.getCluster());
    // 写入 DB（版本号由 domain repository 内部 +1）
    jobRepository.update(dto);

    // 重新调度：先注销旧的本地调度（CRON/FIXED_RATE/FIXED_DELAY 共用 scheduledMap）
    unregister(exists.getJobKey());
    if ("NORMAL".equals(exists.getStatus())) {
      register(exists);
    }
    log.info(
        "[Cronjob] 更新任务: key={} scheduleType={}", exists.getJobKey(), exists.getScheduleType());
    // P1-6: 记录版本变更快照（统一走 JobHistoryService）
    JobHistoryService historyService2 = jobHistoryServiceProvider.getIfAvailable();
    if (historyService2 != null) {
      historyService2.recordVersionChange(exists, exists, "UPDATE", dto.getUpdatedBy(), "任务更新");
    }
    // 同步到统一搜索索引（ydsz-common-search）
    syncSearchIndex("job", exists);
  }

  /**
   * 删除任务
   *
   * @param id 任务 ID
   * @throws SysException 当任务不存在时抛出
   */
  @Override
  public void delete(String id) {
    JobVO vo = jobRepository.findById(id).orElseThrow(() -> SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
        .message("error.cronjob.msg_c0d8369f")
        .build());
    JobVO j = voToJob(vo);
    unregister(j.getJobKey());
    jobRepository.deleteById(id);
    log.info("[Cronjob] 删除任务: key={}", j.getJobKey());
    // P1-6: 记录版本变更快照（统一走 JobHistoryService）
    JobHistoryService historyService3 = jobHistoryServiceProvider.getIfAvailable();
    if (historyService3 != null) {
      historyService3.recordVersionChange(j, null, "DELETE", j.getUpdatedBy(), "任务删除");
    }
    // 从统一搜索索引移除（ydsz-common-search）
    deleteSearchIndex("job", id);
  }

  /**
   * 暂停任务
   *
   * @param id 任务 ID
   * @throws SysException 当任务不存在时抛出
   */
  @Override
  public void pause(String id) {
    JobVO vo = jobRepository.findById(id).orElseThrow(() -> SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
        .message("error.cronjob.msg_c0d8369f")
        .build());
    JobVO j = voToJob(vo);
    if (!"NORMAL".equals(j.getStatus())) {
      throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_job_status_invalid")
          .params(j.getStatus())
          .build();
    }
    unregister(j.getJobKey());
    j.setStatus("PAUSED");
    jobRepository.updateById(j);
    log.info("[Cronjob] 暂停任务: key={}", j.getJobKey());
  }

  /**
   * 恢复任务
   *
   * @param id 任务 ID
   * @throws SysException 当任务不存在时抛出
   */
  @Override
  public void resume(String id) {
    JobVO vo = jobRepository.findById(id).orElseThrow(() -> SysException.builder()
        .resultCode(YdszResultCode.NOT_FOUND)
        .message("error.cronjob.msg_c0d8369f")
        .build());
    JobVO j = voToJob(vo);
    if ("NORMAL".equals(j.getStatus())) {
      if (!scheduledMap.containsKey(j.getJobKey())) {
        register(j);
      }
    } else if ("PAUSED".equals(j.getStatus())) {
      j.setStatus("NORMAL");
      jobRepository.updateById(j);
      register(j);
    } else {
      throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_job_status_invalid")
          .params(j.getStatus())
          .build();
    }
    log.info("[Cronjob] 恢复任务: key={}", j.getJobKey());
  }

  /**
   * 立即执行一次
   *
   * <p>P0-5: 默认不抢占锁（与历史行为兼容）。
   *
   * @param id 任务 ID
   * @return 执行日志 ID
   * @throws SysException 当任务不存在时抛出
   */
  @Override
  public String trigger(String id) {
    return trigger(id, false);
  }

  /**
   * 立即执行一次（可选是否抢占分布式锁）。
   *
   * <p>P0-5: 修复手动触发绕过锁的问题。 P1-7: Leader 模式下优先走 {@link TaskDispatcher}（若可用），否则回退到内部 executeJob 旧路径。
   *
   * @param id 任务 ID
   * @param holdLock 是否抢占分布式锁
   * @return 执行日志 ID；当 holdLock=true 且锁被持有时返回 null
   * @throws SysException 当任务不存在时抛出
   */
  @Override
  public String trigger(String id, boolean holdLock) {
    JobVO vo = jobRepository.findById(id).orElseThrow(() -> SysException.builder()
        .resultCode(YdszResultCode.NOT_FOUND)
        .message("error.cronjob.msg_c0d8369f")
        .build());
    JobVO j = voToJob(vo);
    TaskDispatcher dispatcher =
        taskDispatcherProvider != null ? taskDispatcherProvider.getIfAvailable() : null;
    if (dispatcher != null) {
      // P1-7: 走 Dispatcher 派发路径
      // holdLock=true → triggerType=CRON（Dispatcher 内部会抢锁）
      // holdLock=false → triggerType=MANUAL（Dispatcher 内部不抢锁）
      String triggerType =
          holdLock ? DefaultTaskDispatcher.TRIGGER_CRON : DefaultTaskDispatcher.TRIGGER_MANUAL;
      return dispatcher.dispatch(j, null, triggerType);
    }
    // Leaderless 回退路径（保留 P0 行为）
    return executeJob(j, !holdLock);
  }

  /**
   * 批量暂停任务
   *
   * <p>逐个调用 {@link #pause(String)}，单条失败记录 warn 日志并继续处理后续任务， 不影响其他任务的暂停操作。不使用整体事务，避免单条失败回滚所有操作。
   *
   * @param jobIds 任务 ID 列表
   * @return 批量操作结果（含成功/失败明细）
   */
  @Override
  public BatchResult<String> batchPause(List<String> jobIds) {
    List<BatchResult.ItemResult<String>> details = new ArrayList<>();
    int success = 0;
    for (String jobId : jobIds) {
      try {
        pause(jobId);
        details.add(BatchResult.ItemResult.success(jobId));
        success++;
      } catch (Exception e) {
        log.warn("[Cronjob] 批量暂停失败: jobId={} reason={}", jobId, e.getMessage());
        details.add(BatchResult.ItemResult.failure(jobId, e.getMessage()));
      }
    }
    log.info("[Cronjob] 批量暂停完成: total={} success={}", jobIds.size(), success);
    return new BatchResult<>(jobIds.size(), success, jobIds.size() - success, details);
  }

  /**
   * 批量恢复任务
   *
   * <p>逐个调用 {@link #resume(String)}，单条失败记录 warn 日志并继续处理后续任务， 不影响其他任务的恢复操作。不使用整体事务，避免单条失败回滚所有操作。
   *
   * @param jobIds 任务 ID 列表
   * @return 批量操作结果（含成功/失败明细）
   */
  @Override
  public BatchResult<String> batchResume(List<String> jobIds) {
    List<BatchResult.ItemResult<String>> details = new ArrayList<>();
    int success = 0;
    for (String jobId : jobIds) {
      try {
        resume(jobId);
        details.add(BatchResult.ItemResult.success(jobId));
        success++;
      } catch (Exception e) {
        log.warn("[Cronjob] 批量恢复失败: jobId={} reason={}", jobId, e.getMessage());
        details.add(BatchResult.ItemResult.failure(jobId, e.getMessage()));
      }
    }
    log.info("[Cronjob] 批量恢复完成: total={} success={}", jobIds.size(), success);
    return new BatchResult<>(jobIds.size(), success, jobIds.size() - success, details);
  }

  /**
   * 批量触发任务
   *
   * <p>逐个调用 {@link #trigger(String)}，单条失败记录 warn 日志并继续处理后续任务， 不影响其他任务的触发操作。不使用整体事务，避免单条失败回滚所有操作。
   *
   * @param jobIds 任务 ID 列表
   * @return 批量操作结果（含成功/失败明细）
   */
  @Override
  public BatchResult<String> batchTrigger(List<String> jobIds) {
    List<BatchResult.ItemResult<String>> details = new ArrayList<>();
    int success = 0;
    for (String jobId : jobIds) {
      try {
        trigger(jobId);
        details.add(BatchResult.ItemResult.success(jobId));
        success++;
      } catch (Exception e) {
        log.warn("[Cronjob] 批量触发失败: jobId={} reason={}", jobId, e.getMessage());
        details.add(BatchResult.ItemResult.failure(jobId, e.getMessage()));
      }
    }
    log.info("[Cronjob] 批量触发完成: total={} success={}", jobIds.size(), success);
    return new BatchResult<>(jobIds.size(), success, jobIds.size() - success, details);
  }

  /**
   * 批量删除任务
   *
   * <p>逐个调用 {@link #delete(String)}，单条失败记录 warn 日志并继续处理后续任务， 不影响其他任务的删除操作。不使用整体事务，避免单条失败回滚所有操作。
   *
   * @param jobIds 任务 ID 列表
   * @return 批量操作结果（含成功/失败明细）
   */
  @Override
  public BatchResult<String> batchDelete(List<String> jobIds) {
    List<BatchResult.ItemResult<String>> details = new ArrayList<>();
    int success = 0;
    for (String jobId : jobIds) {
      try {
        delete(jobId);
        details.add(BatchResult.ItemResult.success(jobId));
        success++;
      } catch (Exception e) {
        log.warn("[Cronjob] 批量删除失败: jobId={} reason={}", jobId, e.getMessage());
        details.add(BatchResult.ItemResult.failure(jobId, e.getMessage()));
      }
    }
    log.info("[Cronjob] 批量删除完成: total={} success={}", jobIds.size(), success);
    return new BatchResult<>(jobIds.size(), success, jobIds.size() - success, details);
  }

  /**
   * 注册到调度器（从 DB 加载/动态新增）。
   *
   * <p>根据 {@code scheduleType} 分发到不同调度器：
   *
   * <ul>
   *   <li>CRON: 注册到 CronTrigger（Leaderless 模式）或由 JobScanner 扫描（Leader 模式）
   *   <li>FIXED_RATE / FIXED_DELAY: 注册到本地 TaskScheduler 的 scheduleAtFixedRate/scheduleWithFixedDelay
   *   <li>API: 不注册任何调度（仅手动触发）
   * </ul>
   *
   * @param dto 任务注册请求
   * @return 注册成功返回 true，否则返回 false
   */
  @Override
  public boolean register(JobPostDTO dto) {
    return registerInternal(dtoToJob(dto));
  }

  /**
   * 注册任务到调度器（核心逻辑，基于 JobVO）。
   *
   * <p>供 {@link #register(JobPostDTO)} 与 {@link #reschedule(JobPutDTO)} 复用，避免 DTO 类型不一致导致重复实现。
   */
  private boolean registerInternal(JobVO jobVo) {
    if (!"NORMAL".equals(jobVo.getStatus())) {
      return false;
    }
    ScheduleType type = ScheduleType.parse(jobVo.getScheduleType());
    // P0-3: API 类型不注册任何调度
    if (type == ScheduleType.API) {
      log.info("[Cronjob] API 类型任务不注册调度: key={}", jobVo.getJobKey());
      return true;
    }
    // FIXED_RATE / FIXED_DELAY：Leader 模式由 JobScanner 统一扫描推进（跳过本地注册，避免双触发）；
    // Leaderless 模式回退本地 TaskScheduler（向后兼容）
    if (type == ScheduleType.FIXED_RATE || type == ScheduleType.FIXED_DELAY) {
      if (cronjobProperties.getLeader().isEnabled()) {
        if (jobVo.getNextFireTime() == null) {
          jobVo.setNextFireTime(nextFireTime(jobVo));
          jobRepository.updateById(jobVo);
        }
        log.debug(
            "[Cronjob] Leader 模式跳过固定频率本地注册: key={}（由 JobScanner 扫描派发）", jobVo.getJobKey());
        return true;
      }
      return registerFixedRateJob(jobVo, type);
    }
    // CRON 类型走原有逻辑
    if (!StringUtils.hasText(jobVo.getCronExpression())) {
      log.warn("[Cronjob] 注册失败: 任务 {} cron 表达式为空", jobVo.getJobKey());
      return false;
    }
    // P1-7: Leader 模式下跳过本地 CronTrigger 注册，仅确保 next_fire_time 已计算
    if (cronjobProperties.getLeader().isEnabled()) {
      if (jobVo.getNextFireTime() == null) {
        jobVo.setNextFireTime(nextFireTime(jobVo));
        jobRepository.updateById(jobVo);
      }
      log.debug("[Cronjob] Leader 模式跳过本地注册: key={}（由 JobScanner 扫描派发）", jobVo.getJobKey());
      return true;
    }
    if (scheduledMap.containsKey(jobVo.getJobKey())) {
      unregister(jobVo.getJobKey());
    }
    try {
      CronTrigger trigger = buildTrigger(jobVo);
      ScheduledFuture<?> f = taskScheduler.schedule(() -> executeJob(jobVo, false), trigger);
      scheduledMap.put(jobVo.getJobKey(), f);
      log.info("[Cronjob] 注册任务成功: key={} cron={}", jobVo.getJobKey(), jobVo.getCronExpression());
      return true;
    } catch (Exception e) {
      log.error("[Cronjob] 注册任务失败: key={} reason={}", jobVo.getJobKey(), e.getMessage());
      return false;
    }
  }

  /**
   * 注册 FIXED_RATE / FIXED_DELAY 类型任务。
   *
   * <p>使用本地 {@link TaskScheduler} 的 scheduleAtFixedRate / scheduleWithFixedDelay，
   * 通过 Redis 分布式锁防止多实例重复执行。
   *
   * @param job 任务定义
   * @param type 调度类型（FIXED_RATE / FIXED_DELAY）
   * @return 注册成功返回 true，否则返回 false
   */
  private boolean registerFixedRateJob(JobVO jobVo, ScheduleType type) {
    // 注册到本地 TaskScheduler（Leader 和 Leaderless 模式统一处理）
    long intervalMs;
    if (type == ScheduleType.FIXED_RATE) {
      intervalMs = jobVo.getFixedRateMs() == null ? 0 : jobVo.getFixedRateMs();
    } else {
      intervalMs = jobVo.getFixedDelayMs() == null ? 0 : jobVo.getFixedDelayMs();
    }
    if (intervalMs <= 0) {
      log.warn(
          "[Cronjob] 注册失败: 任务 {} 间隔非法, type={} fixedRateMs={} fixedDelayMs={}",
          jobVo.getJobKey(),
          type,
          jobVo.getFixedRateMs(),
          jobVo.getFixedDelayMs());
      return false;
    }
    if (scheduledMap.containsKey(jobVo.getJobKey())) {
      unregister(jobVo.getJobKey());
    }
    try {
      ScheduledFuture<?> f;
      if (type == ScheduleType.FIXED_RATE) {
        f =
            taskScheduler.scheduleAtFixedRate(
                () -> executeJob(jobVo, false), Duration.ofMillis(intervalMs));
      } else {
        f =
            taskScheduler.scheduleWithFixedDelay(
                () -> executeJob(jobVo, false), Duration.ofMillis(intervalMs));
      }
      scheduledMap.put(jobVo.getJobKey(), f);
      log.info("[Cronjob] 注册 {} 任务成功: key={} intervalMs={}", type, jobVo.getJobKey(), intervalMs);
      return true;
    } catch (Exception e) {
      log.error("[Cronjob] 注册 {} 任务失败: key={} reason={}", type, jobVo.getJobKey(), e.getMessage());
      return false;
    }
  }

  /**
   * 取消注册
   *
   * @param jobKey 任务 KEY
   * @return 取消成功返回 true，任务未注册返回 false
   */
  @Override
  public boolean unregister(String jobKey) {
    ScheduledFuture<?> f = scheduledMap.remove(jobKey);
    if (f != null) {
      f.cancel(false);
      log.info("[Cronjob] 注销任务: key={}", jobKey);
      return true;
    }
    return false;
  }

  /**
   * 重新注册（用于更新 Cron）
   *
   * @param dto 任务更新请求
   * @return 重新注册成功返回 true，否则返回 false
   */
  @Override
  public boolean reschedule(JobPutDTO dto) {
    JobVO jobVo = dtoToJob(dto);
    unregister(jobVo.getJobKey());
    return registerInternal(jobVo);
  }

  /**
   * 详情
   *
   * @param id 任务 ID
   * @return 任务定义
   * @throws SysException 当任务不存在时抛出
   */
  @Override
  @Transactional(readOnly = true)
  public JobVO getById(String id) {
    return jobRepository.findById(id).orElseThrow(() -> SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
        .message("error.cronjob.msg_c0d8369f")
        .build());
  }

  /**
   * 分页查询任务
   *
   * @param page 页码
   * @param size 每页条数
   * @param keyword 关键字（任务名/KEY/处理器，可选）
   * @param status 状态过滤（可选）
   * @param group 分组过滤（可选）
   * @return 任务分页数据
   */
  @Override
  @Transactional(readOnly = true)
  public PageResponse<List<JobVO>> page(int page, int size, String keyword, String status, String group) {
    return jobRepository.page(keyword, status, group, page, size).toPageResponse();
  }

  /**
   * 分页查询执行日志
   *
   * @param page 页码
   * @param size 每页条数
   * @param jobKey 任务 KEY 过滤（可选）
   * @param status 状态过滤（可选）
   * @return 执行日志分页数据
   */
  @Override
  @Transactional(readOnly = true)
  public PageResponse<List<JobLogVO>> pageLog(int page, int size, String jobKey, String status) {
    return jobLogRepository.pageByJobKeyAndStatus(jobKey, status, page, size).toPageResponse();
  }

  // ==================== 内部执行逻辑 ====================

  /**
   * 执行任务内部逻辑
   *
   * <p>定时触发（非手动）时通过 Redis 分布式锁防止多实例重复执行； 锁的获取与释放委托 {@link JobLockManager}，复用 ydsz-common-lock 公共模块的
   * WatchDog 续期、锁监控指标等能力。 记录执行日志（开始/结束/耗时/状态/结果）并更新任务统计字段。
   *
   * @param job 任务定义
   * @param manual 是否手动触发（手动触发不加分布式锁）
   * @return 执行日志 ID；定时触发且锁已被持有时返回 null
   */
  private String executeJob(JobVO jobVo, boolean manual) {
    // 定时触发（非手动）时获取分布式锁，防止多实例重复执行
    // P0-4: TTL 支持任务级 override + 全局配置 + 上下限规整
    String lockValue = null;
    if (!manual) {
      Duration ttl = resolveLockTtl(jobVo);
      lockValue = jobLockManager.tryAcquireLock(jobVo.getJobKey(), null, ttl.toMillis());
      if (lockValue == null) {
        log.info("[Cronjob] 任务已被其他实例持有锁, 跳过本次执行: key={}", jobVo.getJobKey());
        return null;
      }
      log.debug(
          "[Cronjob] 获取分布式锁成功: key={} holder={} ttl={}ms",
          jobVo.getJobKey(),
          lockValue,
          ttl.toMillis());
    }

    // 写开始日志
    JobLogVO log0 = new JobLogVO();
    log0.setJobId(jobVo.getId());
    log0.setJobKey(jobVo.getJobKey());
    log0.setStartTime(LocalDateTime.now());
    log0.setStatus("RUNNING");
    log0.setParamsJson(jobVo.getParamsJson());
    log0.setTraceId(TracerUtils.getTraceId());
    String logId = jobLogRepository.insert(log0);

    boolean success = false;
    String error = null;
    Object result = null;
    try {
      JobHandler handler = applicationContext.getBean(jobVo.getHandler(), JobHandler.class);
      result = handler.execute(jobVo.getParamsJson());
      success = true;
      log0.setResultJson(result == null ? null : YdszJson.toJson(result));
    } catch (Exception e) {
      log.error(
          "[Cronjob] 任务执行失败: key={} handler={} reason={}",
          jobVo.getJobKey(),
          jobVo.getHandler(),
          e.getMessage(),
          e);
      error = e.getClass().getSimpleName() + ": " + e.getMessage();
      log0.setErrorMessage(error);
    } finally {
      log0.setId(logId);
      log0.setEndTime(LocalDateTime.now());
      log0.setDurationMs(Duration.between(log0.getStartTime(), log0.getEndTime()).toMillis());
      log0.setStatus(success ? "SUCCESS" : "FAILED");
      jobLogRepository.update(log0);

      // 更新任务统计
      Long incFire = 1L;
      Long incSucc = success ? 1L : 0L;
      Long incFail = success ? 0L : 1L;
      LocalDateTime next = null;
      if (!manual) {
        next = nextFireTime(jobVo);
      }
      jobRepository.updateStats(
          jobVo.getId(),
          log0.getStartTime(),
          next,
          incFire,
          incSucc,
          incFail,
          success ? null : "ERROR");

      // 释放分布式锁（JobLockManager 安全释放: 仅持有者可释放）
      if (lockValue != null) {
        try {
          jobLockManager.releaseLock(jobVo.getJobKey(), null, lockValue);
        } catch (Exception e) {
          log.warn(
              "[Cronjob] 释放分布式锁失败(将等待 TTL 自动过期): key={} reason={}",
              jobVo.getJobKey(),
              e.getMessage());
        }
      }
    }
    return log0.getId();
  }

  /**
   * 解析任务实际使用的锁 TTL。
   *
   * <p>P0-4: 优先使用任务级 {@code lockTtlMs}（如果配置且合法）， 否则回退到全局 {@link CronjobProperties#getJobLockTtl()}。
   * 最终经 {@link CronjobProperties#normalizeTtl(Duration)} 规整到 [min, max] 区间。
   *
   * @param job 任务定义
   * @return 规整化后的锁 TTL
   */
  private Duration resolveLockTtl(JobVO jobVo) {
    Duration taskLevel = null;
    if (jobVo.getLockTtlMs() != null && jobVo.getLockTtlMs() > 0) {
      taskLevel = Duration.ofMillis(jobVo.getLockTtlMs());
    }
    return cronjobProperties.normalizeTtl(taskLevel);
  }

  /**
   * 校验任务必填字段
   *
   * <p>P0-3: 根据 {@code scheduleType} 校验：
   *
   * <ul>
   *   <li>CRON: 必须有 cronExpression
   *   <li>FIXED_RATE: 必须有 fixedRateMs &gt; 0
   *   <li>FIXED_DELAY: 必须有 fixedDelayMs &gt; 0
   *   <li>API: 无额外必填字段
   * </ul>
   *
   * @param job 任务定义
   * @throws SysException 当 jobKey/handler 为空或调度参数非法时抛出
   */
  private void validate(JobPostDTO dto) {
    if (!StringUtils.hasText(dto.getJobKey())) {
      throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_884214e7")
          .build();
    }
    if (!StringUtils.hasText(dto.getHandler())) {
      throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_04ebee77")
          .build();
    }
    if (StringUtils.hasText(dto.getTimezone())) {
      try {
        ZoneId.of(dto.getTimezone());
      } catch (Exception e) {
        throw SysException.builder()
              .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.cronjob.msg_5d0044ca")
            .params("无效的时区 ID: " + dto.getTimezone())
            .build();
      }
    }
    ScheduleType type = ScheduleType.parse(dto.getScheduleType());
    switch (type) {
      case CRON:
        validateCron(dto.getCronExpression());
        break;
      case FIXED_RATE:
        if (dto.getFixedRateMs() == null || dto.getFixedRateMs() <= 0) {
          throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
              .key("error.cronjob.msg_5d0044ca")
              .params("fixedRateMs 必须为正数")
              .build();
        }
        break;
      case FIXED_DELAY:
        if (dto.getFixedDelayMs() == null || dto.getFixedDelayMs() <= 0) {
          throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
              .key("error.cronjob.msg_5d0044ca")
              .params("fixedDelayMs 必须为正数")
              .build();
        }
        break;
      case API:
        break;
      default:
        validateCron(dto.getCronExpression());
    }
  }

  private void validate(JobVO jobVo) {
    if (!StringUtils.hasText(jobVo.getJobKey())) {
      throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_884214e7")
          .build();
    }
    if (!StringUtils.hasText(jobVo.getHandler())) {
      throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_04ebee77")
          .build();
    }
    // P2-8: 校验任务级时区（非空时必须为有效时区 ID）
    if (StringUtils.hasText(jobVo.getTimezone())) {
      try {
        ZoneId.of(jobVo.getTimezone());
      } catch (Exception e) {
        throw SysException.builder()
              .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.cronjob.msg_5d0044ca")
            .params("无效的时区 ID: " + jobVo.getTimezone())
            .build();
      }
    }
    ScheduleType type = ScheduleType.parse(jobVo.getScheduleType());
    switch (type) {
      case CRON:
        validateCron(jobVo.getCronExpression());
        break;
      case FIXED_RATE:
        if (jobVo.getFixedRateMs() == null || jobVo.getFixedRateMs() <= 0) {
          throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
              .key("error.cronjob.msg_5d0044ca")
              .params("fixedRateMs 必须为正数")
              .build();
        }
        break;
      case FIXED_DELAY:
        if (jobVo.getFixedDelayMs() == null || jobVo.getFixedDelayMs() <= 0) {
          throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
              .key("error.cronjob.msg_5d0044ca")
              .params("fixedDelayMs 必须为正数")
              .build();
        }
        break;
      case API:
        // API 类型仅手动触发，无额外必填字段
        break;
      default:
        // 不会到达此处（parse 方法已兜底）
        validateCron(jobVo.getCronExpression());
    }
  }

  /**
   * 校验 cron 表达式合法性
   *
   * @param cron cron 表达式
   * @throws SysException 当 cron 为空或非法时抛出
   */
  private void validateCron(String cron) {
    if (!StringUtils.hasText(cron)) {
      throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_35ac148f")
          .build();
    }
    try {
      new CronTrigger(cron);
    } catch (Exception e) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_5d0044ca")
          .params(e.getMessage())
          .build();
    }
  }

  /**
   * 构造 CronTrigger（P2-8: 支持任务级时区）。
   *
   * <p>P0-3 修复: 不再使用系统默认时区，避免多时区部署时触发时间漂移。 P2-8: 优先使用任务级时区，为空时回退到 {@link #SCHEDULE_TIMEZONE}。
   *
   * @param job 任务定义（含 cron 表达式和时区）
   * @return CronTrigger 实例
   */
  private CronTrigger buildTrigger(JobVO jobVo) {
    String tz =
        StringUtils.hasText(jobVo.getTimezone()) ? jobVo.getTimezone() : SCHEDULE_TIMEZONE.getID();
    return new CronTrigger(jobVo.getCronExpression(), TimeZone.getTimeZone(tz));
  }

  /**
   * 计算下次触发时间（P1-3: 统一委托 {@link NextFireTimeCalculator}，支持任务级时区）。
   *
   * <p>与 {@code JobScanner} / {@code DefaultTaskDispatcher} 共用同一计算入口，保证
   * cron 解析与时区语义一致。
   *
   * @param job 任务定义（含 cron 表达式和时区）
   * @return 下次触发时间；表达式非法时返回 null
   */
  private LocalDateTime nextFireTime(JobVO jobVo) {
    return nextFireTimeCalculator.calculate(jobVo);
  }

  /** 发布领域事件到 Outbox（DomainEventPublisher 不可用时静默跳过）。 */
  private void publishEvent(
      String aggregateType, String aggregateId, String eventType, String payload) {
    DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
    if (publisher != null) {
      publisher.publish(
          DomainEvent.builder()
                .aggregateType(aggregateType)
              .aggregateId(aggregateId)
              .eventType(eventType)
              .metadata("payload", payload)
              .build());
    }
  }

  /**
   * 同步实体到统一搜索索引。
   *
   * <p>委托 ydsz-common-search 的 {@link SearchIndexEventBridge} 异步写入索引， 搜索模块未引入时静默降级为无操作。
   *
   * @param type 实体类型标识（如 "job"）
   * @param entity 业务实体
   */
  private void syncSearchIndex(String type, JobVO entity) {
    SearchIndexEventBridge bridge = searchIndexEventBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexUpsert(type, entity);
    }
  }

  /**
   * 从统一搜索索引移除实体。
   *
   * <p>委托 ydsz-common-search 的 {@link SearchIndexEventBridge} 异步删除索引， 搜索模块未引入时静默降级为无操作。
   *
   * @param type 实体类型标识（如 "job"）
   * @param id 实体 ID
   */
  private void deleteSearchIndex(String type, String id) {
    SearchIndexEventBridge bridge = searchIndexEventBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexDelete(type, id);
    }
  }
}
