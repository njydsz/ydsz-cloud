package com.njydsz.cronjob.server.service.impl.job;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import com.njydsz.common.json.YdszJson;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import com.njydsz.common.redis.service.RedisService;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.core.job.JobHandler;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.security.TenantContext;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.LockKeyUtil;
import com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher;
import com.njydsz.cronjob.server.core.dispatch.TaskDispatcher;
import com.njydsz.cronjob.server.core.scheduler.ScheduleType;
import com.njydsz.cronjob.server.core.scheduler.SecondLevelScheduler;
import com.njydsz.cronjob.server.service.job.JobHistoryService;
import com.njydsz.cronjob.server.service.job.JobService;
import com.njydsz.cronjob.server.service.job.TenantQuotaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务调度服务实现
 *
 * <p>P1-7 重构：支持 Leader 模式与 Leaderless 模式双轨运行。
 * <ul>
 *   <li>{@code ydsz.cronjob.leader.enabled=false}（默认）：每节点独立 TaskScheduler 注册 CronTrigger，
 *       通过 Redis SET NX EX 锁防止重复执行（P0 行为保持不变）</li>
 *   <li>{@code ydsz.cronjob.leader.enabled=true}：仅 Leader 节点扫描 ydsz_job 并派发任务，
 *       Follower 节点只注册心跳、不注册 CronTrigger，避免重复扫描</li>
 * </ul>
 *
 * <p>手动触发（{@link #trigger(String, boolean)}）始终走 {@link TaskDispatcher}（如果可用），
 * 否则回退到内部 {@link #executeJob(Job, boolean)} 旧路径。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService, ApplicationRunner {

    /** 任务定义 Mapper */
    private final JobMapper jobMapper;
    /** 任务日志 Mapper */
    private final JobLogMapper jobLogMapper;
    /** Spring 应用上下文（用于按 Bean 名称获取 JobHandler） */
    private final ApplicationContext applicationContext;
    /** Redis 模板（用于分布式锁） */
    private final RedisService redisService;
    /** 调度配置属性（P0-4: 锁 TTL 等可配置项） */
    private final CronjobProperties cronjobProperties;

    /**
     * 任务派发器（P1-7 可选注入）。
     *
     * <p>Leader 模式启用时由 {@link DefaultTaskDispatcher} 提供；
     * Leaderless 模式下若未注册 Dispatcher 则回退到内部 {@link #executeJob(Job, boolean)} 旧路径。
     */
    private final ObjectProvider<TaskDispatcher> taskDispatcherProvider;

    /**
     * 租户级配额服务（P7-2 新增）。
     *
     * <p>用于在任务创建时检查租户任务数配额，防止 noisy neighbor 问题。
     * 配额检查默认禁用（{@code ydsz.cronjob.quota.enabled=false}），启用后生效。
     */
    private final TenantQuotaService tenantQuotaService;

    /**
     * 秒级调度器（P0-3 可选注入）。
     *
     * <p>仅在 Leader 模式启用（{@code @ConditionalOnBean(LeaderElector.class)}），
     * 用于管理 FIXED_RATE / FIXED_DELAY 类型任务的调度。
     * Leaderless 模式下为 null，由 {@link #register} 回退到本地 TaskScheduler 处理。
     */
    private final ObjectProvider<SecondLevelScheduler> secondLevelSchedulerProvider;

    /**
     * 任务历史版本服务（P1-6 可选注入）。
     *
     * <p>用于在任务配置更新前自动保存历史快照，支持版本对比和一键回滚。
     * 同时合并了原 JobVersionService 的版本变更记录能力（recordVersionChange），
     * 统一版本管理入口。
     * 通过 ObjectProvider 可选注入，避免循环依赖且便于测试。
     */
    private final ObjectProvider<JobHistoryService> jobHistoryServiceProvider;

    /** 调度器 */
    private TaskScheduler taskScheduler;

    /** 已调度的任务: jobKey -> Future */
    private final Map<String, ScheduledFuture<?>> scheduledMap = new ConcurrentHashMap<>();

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
        script.setScriptText("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 初始化任务调度器（线程池大小可配置，关闭时等待任务完成）
     */
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

    /**
     * 销毁调度器，取消所有已调度任务
     */
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
     * <ul>
     *   <li>Leaderless 模式：调用 {@link #loadOnStartup()} 加载所有 NORMAL 任务到 TaskScheduler</li>
     *   <li>Leader 模式：跳过本地注册（由 {@link com.njydsz.cronjob.server.core.dispatch.JobScanner} 接管扫描）</li>
     * </ul>
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (cronjobProperties.getLeader().isEnabled()) {
            log.info("[Cronjob] Leader 模式启用, 跳过本地 CronTrigger 注册（由 JobScanner 接管）: role={}",
                    cronjobProperties.getLeader().getRole());
            return;
        }
        try {
            loadOnStartup();
        } catch (Exception e) {
            log.error("[Cronjob] 启动加载任务失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 应用启动时加载所有 NORMAL 任务
     */
    @Override
    @Transactional(readOnly = true)
    public void loadOnStartup() {
        List<Job> list = jobMapper.selectAllNormal();
        log.info("[Cronjob] 启动加载任务数量: {}", list.size());
        for (Job j : list) {
            try {
                register(j);
            } catch (Exception e) {
                log.warn("[Cronjob] 注册任务失败: key={} reason={}", j.getJobKey(), e.getMessage());
            }
        }
    }

    /**
     * 新增任务
     *
     * <p>P0-3: 根据 {@code scheduleType} 决定是否计算 nextFireTime：
     * <ul>
     *   <li>CRON: 计算 nextFireTime（由 JobScanner 扫描）</li>
     *   <li>FIXED_RATE / FIXED_DELAY: 不计算 nextFireTime（由 SecondLevelScheduler 管理）</li>
     *   <li>API: 不计算 nextFireTime（仅手动触发）</li>
     * </ul>
     *
     * @param job 任务定义
     * @return 新增任务 ID
     * @throws SysException 当 jobKey 已存在或参数非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(Job job) {
        // P0-3: scheduleType 默认为 CRON（向后兼容）
        if (!StringUtils.hasText(job.getScheduleType())) {
            job.setScheduleType(ScheduleType.CRON.name());
        }
        validate(job);
        if (jobMapper.selectByJobKey(job.getJobKey()) != null) {
            throw new SysException(BaseResultCode.DUPLICATE_KEY, "error.cronjob.msg_7e5ef640", job.getJobKey());
        }
        if (job.getStatus() == null) {
            job.setStatus("NORMAL");
        }
        if (job.getJobGroup() == null) {
            job.setJobGroup("DEFAULT");
        }
        if (job.getTenantId() == null) {
            job.setTenantId(TenantContext.getTenantId());
        }
        // P7-2: 租户级配额检查（在 insert 之前调用，避免任务计数提前增加导致误判）
        tenantQuotaService.checkJobQuota(job.getTenantId());
        // P3 收尾: 分片/misfire 默认值规整
        if (job.getShardTotal() == null || job.getShardTotal() < 1) {
            job.setShardTotal(1);
        }
        if (!StringUtils.hasText(job.getMisfirePolicy())) {
            job.setMisfirePolicy("FIRE_NOW");
        }
        // P0-3: 仅 CRON 类型计算 nextFireTime（FIXED_RATE/FIXED_DELAY 由 SecondLevelScheduler 管理）
        ScheduleType type = ScheduleType.parse(job.getScheduleType());
        if (type == ScheduleType.CRON) {
            LocalDateTime next = nextFireTime(job);
            job.setNextFireTime(next);
        }
        jobMapper.insert(job);
        if ("NORMAL".equals(job.getStatus())) {
            register(job);
        }
        log.info("[Cronjob] 创建任务: key={} scheduleType={} cron={} handler={} shardTotal={}",
                job.getJobKey(), job.getScheduleType(), job.getCronExpression(),
                job.getHandler(), job.getShardTotal());
        // P1-6: 记录版本变更快照（统一走 JobHistoryService）
        JobHistoryService historyService = jobHistoryServiceProvider.getIfAvailable();
        if (historyService != null) {
            historyService.recordVersionChange(null, job, "CREATE",
                    job.getCreatedBy(), "任务创建");
        }
        return job.getId();
    }

    /**
     * 更新任务
     *
     * <p>P0-3: 同步 scheduleType/fixedRateMs/fixedDelayMs 字段，并按新调度类型重新注册。
     *
     * @param job 任务定义
     * @throws SysException 当任务不存在或 cron 表达式非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Job job) {
        if (job.getId() == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_ce91ca69");
        }
        Job exists = jobMapper.selectById(job.getId());
        if (exists == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.cronjob.msg_c0d8369f");
        }
        // P1-6: 保存历史版本（在更新之前保存当前快照）
        JobHistoryService historyService = jobHistoryServiceProvider.getIfAvailable();
        if (historyService != null) {
            historyService.saveHistory(exists, job.getUpdatedBy());
        }
        // P0-3: 同步 scheduleType（空值不覆盖，保持原值）
        if (StringUtils.hasText(job.getScheduleType())) {
            exists.setScheduleType(job.getScheduleType());
        }
        // P0-3: 同步 fixedRateMs/fixedDelayMs（允许清空为 null）
        exists.setFixedRateMs(job.getFixedRateMs());
        exists.setFixedDelayMs(job.getFixedDelayMs());
        // P2-8: 同步时区（允许清空为 null，使用默认时区）
        exists.setTimezone(job.getTimezone());
        // 按新调度类型校验
        ScheduleType type = ScheduleType.parse(exists.getScheduleType());
        if (type == ScheduleType.CRON) {
            if (StringUtils.hasText(job.getCronExpression())) {
                validateCron(job.getCronExpression());
            }
            // 重新计算 nextFireTime（CRON 类型）
            if (StringUtils.hasText(job.getCronExpression())) {
                exists.setNextFireTime(nextFireTime(exists));
            }
        } else if (type == ScheduleType.FIXED_RATE) {
            if (exists.getFixedRateMs() == null || exists.getFixedRateMs() <= 0) {
                throw new SysException(BaseResultCode.BAD_REQUEST,
                        "error.cronjob.msg_5d0044ca", "fixedRateMs 必须为正数");
            }
            // FIXED_RATE 类型清空 nextFireTime（由 SecondLevelScheduler 管理）
            exists.setNextFireTime(null);
        } else if (type == ScheduleType.FIXED_DELAY) {
            if (exists.getFixedDelayMs() == null || exists.getFixedDelayMs() <= 0) {
                throw new SysException(BaseResultCode.BAD_REQUEST,
                        "error.cronjob.msg_5d0044ca", "fixedDelayMs 必须为正数");
            }
            // FIXED_DELAY 类型清空 nextFireTime（由 SecondLevelScheduler 管理）
            exists.setNextFireTime(null);
        }
        if (StringUtils.hasText(job.getCronExpression())) exists.setCronExpression(job.getCronExpression());
        if (StringUtils.hasText(job.getHandler())) exists.setHandler(job.getHandler());
        if (StringUtils.hasText(job.getJobName())) exists.setJobName(job.getJobName());
        if (StringUtils.hasText(job.getJobGroup())) exists.setJobGroup(job.getJobGroup());
        if (job.getParamsJson() != null) exists.setParamsJson(job.getParamsJson());
        if (StringUtils.hasText(job.getStatus())) exists.setStatus(job.getStatus());
        if (job.getJobRemark() != null) exists.setJobRemark(job.getJobRemark());
        // P0/P2/P3 收尾: 同步 lockTtlMs/timeoutMs/misfirePolicy/shardTotal
        if (job.getLockTtlMs() != null) exists.setLockTtlMs(job.getLockTtlMs());
        if (job.getTimeoutMs() != null) exists.setTimeoutMs(job.getTimeoutMs());
        if (StringUtils.hasText(job.getMisfirePolicy())) exists.setMisfirePolicy(job.getMisfirePolicy());
        if (job.getShardTotal() != null && job.getShardTotal() >= 1) exists.setShardTotal(job.getShardTotal());
        // P6-3: 同步慢任务阈值（null 表示不检测，允许清空）
        exists.setSlowThresholdMs(job.getSlowThresholdMs());
        // P3-12: 同步目标集群（允许清空为 null，使用本地集群）
        exists.setCluster(job.getCluster());
        // P4-8: 版本号 +1
        int newVersion = (exists.getVersion() != null ? exists.getVersion() : 1) + 1;
        exists.setVersion(newVersion);
        jobMapper.updateById(exists);

        // 重新调度：先注销旧的本地调度（CRON/FIXED_RATE/FIXED_DELAY 共用 scheduledMap）
        unregister(exists.getJobKey());
        // P0-3: 注销 SecondLevelScheduler 中的调度（FIXED_RATE/FIXED_DELAY）
        unregisterFromSecondLevel(exists.getId());
        if ("NORMAL".equals(exists.getStatus())) {
            register(exists);
        }
        log.info("[Cronjob] 更新任务: key={} scheduleType={}", exists.getJobKey(), exists.getScheduleType());
        // P1-6: 记录版本变更快照（统一走 JobHistoryService）
        JobHistoryService historyService2 = jobHistoryServiceProvider.getIfAvailable();
        if (historyService2 != null) {
            historyService2.recordVersionChange(exists, exists, "UPDATE",
                    job.getUpdatedBy(), "任务更新");
        }
    }

    /**
     * 删除任务
     *
     * @param id 任务 ID
     * @throws SysException 当任务不存在时抛出
     */
    @Override
    public void delete(String id) {
        Job j = jobMapper.selectById(id);
        if (j == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.cronjob.msg_c0d8369f");
        }
        unregister(j.getJobKey());
        // P0-3: 注销 SecondLevelScheduler 中的调度（FIXED_RATE/FIXED_DELAY）
        unregisterFromSecondLevel(j.getId());
        jobMapper.deleteById(id);
        log.info("[Cronjob] 删除任务: key={}", j.getJobKey());
        // P1-6: 记录版本变更快照（统一走 JobHistoryService）
        JobHistoryService historyService3 = jobHistoryServiceProvider.getIfAvailable();
        if (historyService3 != null) {
            historyService3.recordVersionChange(j, null, "DELETE",
                    j.getUpdatedBy(), "任务删除");
        }
    }

    /**
     * 暂停任务
     *
     * @param id 任务 ID
     * @throws SysException 当任务不存在时抛出
     */
    @Override
    public void pause(String id) {
        Job j = getById(id);
        if (!"NORMAL".equals(j.getStatus())) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.cronjob.msg_job_status_invalid", j.getStatus());
        }
        unregister(j.getJobKey());
        // P0-3: 注销 SecondLevelScheduler 中的调度（FIXED_RATE/FIXED_DELAY）
        unregisterFromSecondLevel(j.getId());
        j.setStatus("PAUSED");
        jobMapper.updateById(j);
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
        Job j = getById(id);
        if ("NORMAL".equals(j.getStatus())) {
            if (!scheduledMap.containsKey(j.getJobKey())) {
                register(j);
            }
        } else if ("PAUSED".equals(j.getStatus())) {
            j.setStatus("NORMAL");
            jobMapper.updateById(j);
            register(j);
        } else {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.cronjob.msg_job_status_invalid", j.getStatus());
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
     * <p>P0-5: 修复手动触发绕过锁的问题。
     * P1-7: Leader 模式下优先走 {@link TaskDispatcher}（若可用），否则回退到内部 executeJob 旧路径。
     *
     * @param id       任务 ID
     * @param holdLock 是否抢占分布式锁
     * @return 执行日志 ID；当 holdLock=true 且锁被持有时返回 null
     * @throws SysException 当任务不存在时抛出
     */
    @Override
    public String trigger(String id, boolean holdLock) {
        Job j = getById(id);
        TaskDispatcher dispatcher = taskDispatcherProvider != null
                ? taskDispatcherProvider.getIfAvailable() : null;
        if (dispatcher != null) {
            // P1-7: 走 Dispatcher 派发路径
            // holdLock=true → triggerType=CRON（Dispatcher 内部会抢锁）
            // holdLock=false → triggerType=MANUAL（Dispatcher 内部不抢锁）
            String triggerType = holdLock
                    ? DefaultTaskDispatcher.TRIGGER_CRON
                    : DefaultTaskDispatcher.TRIGGER_MANUAL;
            return dispatcher.dispatch(j, null, triggerType);
        }
        // Leaderless 回退路径（保留 P0 行为）
        return executeJob(j, !holdLock);
    }

    /**
     * 批量暂停任务
     *
     * <p>逐个调用 {@link #pause(String)}，单条失败记录 warn 日志并继续处理后续任务，
     * 不影响其他任务的暂停操作。不使用整体事务，避免单条失败回滚所有操作。
     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数量
     */
    @Override
    public int batchPause(List<String> jobIds) {
        int success = 0;
        for (String jobId : jobIds) {
            try {
                pause(jobId);
                success++;
            } catch (Exception e) {
                log.warn("[Cronjob] 批量暂停失败: jobId={} reason={}", jobId, e.getMessage());
            }
        }
        log.info("[Cronjob] 批量暂停完成: total={} success={}", jobIds.size(), success);
        return success;
    }

    /**
     * 批量恢复任务
     *
     * <p>逐个调用 {@link #resume(String)}，单条失败记录 warn 日志并继续处理后续任务，
     * 不影响其他任务的恢复操作。不使用整体事务，避免单条失败回滚所有操作。
     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数量
     */
    @Override
    public int batchResume(List<String> jobIds) {
        int success = 0;
        for (String jobId : jobIds) {
            try {
                resume(jobId);
                success++;
            } catch (Exception e) {
                log.warn("[Cronjob] 批量恢复失败: jobId={} reason={}", jobId, e.getMessage());
            }
        }
        log.info("[Cronjob] 批量恢复完成: total={} success={}", jobIds.size(), success);
        return success;
    }

    /**
     * 批量触发任务
     *
     * <p>逐个调用 {@link #trigger(String)}，单条失败记录 warn 日志并继续处理后续任务，
     * 不影响其他任务的触发操作。不使用整体事务，避免单条失败回滚所有操作。
     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数量
     */
    @Override
    public int batchTrigger(List<String> jobIds) {
        int success = 0;
        for (String jobId : jobIds) {
            try {
                trigger(jobId);
                success++;
            } catch (Exception e) {
                log.warn("[Cronjob] 批量触发失败: jobId={} reason={}", jobId, e.getMessage());
            }
        }
        log.info("[Cronjob] 批量触发完成: total={} success={}", jobIds.size(), success);
        return success;
    }

    /**
     * 批量删除任务
     *
     * <p>逐个调用 {@link #delete(String)}，单条失败记录 warn 日志并继续处理后续任务，
     * 不影响其他任务的删除操作。不使用整体事务，避免单条失败回滚所有操作。
     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数量
     */
    @Override
    public int batchDelete(List<String> jobIds) {
        int success = 0;
        for (String jobId : jobIds) {
            try {
                delete(jobId);
                success++;
            } catch (Exception e) {
                log.warn("[Cronjob] 批量删除失败: jobId={} reason={}", jobId, e.getMessage());
            }
        }
        log.info("[Cronjob] 批量删除完成: total={} success={}", jobIds.size(), success);
        return success;
    }

    /**
     * 注册到调度器（从 DB 加载/动态新增）。
     *
     * <p>P0-3: 根据 {@code scheduleType} 分发到不同调度器：
     * <ul>
     *   <li>CRON: 注册到 CronTrigger（Leaderless 模式）或由 JobScanner 扫描（Leader 模式）</li>
     *   <li>FIXED_RATE / FIXED_DELAY: Leader 模式由 SecondLevelScheduler 接管；
     *       Leaderless 模式注册到本地 TaskScheduler 的 scheduleAtFixedRate/scheduleWithFixedDelay</li>
     *   <li>API: 不注册任何调度（仅手动触发）</li>
     * </ul>
     *
     * @param job 任务定义
     * @return 注册成功返回 true，否则返回 false
     */
    @Override
    public boolean register(Job job) {
        if (!"NORMAL".equals(job.getStatus())) {
            return false;
        }
        ScheduleType type = ScheduleType.parse(job.getScheduleType());
        // P0-3: API 类型不注册任何调度
        if (type == ScheduleType.API) {
            log.info("[Cronjob] API 类型任务不注册调度: key={}", job.getJobKey());
            return true;
        }
        // P0-3: FIXED_RATE / FIXED_DELAY 类型优先交给 SecondLevelScheduler（Leader 模式）
        if (type == ScheduleType.FIXED_RATE || type == ScheduleType.FIXED_DELAY) {
            return registerFixedRateJob(job, type);
        }
        // CRON 类型走原有逻辑
        if (!StringUtils.hasText(job.getCronExpression())) {
            log.warn("[Cronjob] 注册失败: 任务 {} cron 表达式为空", job.getJobKey());
            return false;
        }
        // P1-7: Leader 模式下跳过本地 CronTrigger 注册，仅确保 next_fire_time 已计算
        if (cronjobProperties.getLeader().isEnabled()) {
            if (job.getNextFireTime() == null) {
                job.setNextFireTime(nextFireTime(job));
                jobMapper.updateById(job);
            }
            log.debug("[Cronjob] Leader 模式跳过本地注册: key={}（由 JobScanner 扫描派发）",
                    job.getJobKey());
            return true;
        }
        if (scheduledMap.containsKey(job.getJobKey())) {
            unregister(job.getJobKey());
        }
        try {
            CronTrigger trigger = buildTrigger(job);
            ScheduledFuture<?> f = taskScheduler.schedule(
                    () -> executeJob(job, false),
                    trigger
            );
            scheduledMap.put(job.getJobKey(), f);
            log.info("[Cronjob] 注册任务成功: key={} cron={}", job.getJobKey(), job.getCronExpression());
            return true;
        } catch (Exception e) {
            log.error("[Cronjob] 注册任务失败: key={} reason={}", job.getJobKey(), e.getMessage());
            return false;
        }
    }

    /**
     * 注册 FIXED_RATE / FIXED_DELAY 类型任务（P0-3）。
     *
     * <p>Leader 模式：委托给 {@link SecondLevelScheduler}（仅 Leader 节点派发）；
     * Leaderless 模式：回退到本地 {@link TaskScheduler} 的 scheduleAtFixedRate / scheduleWithFixedDelay，
     * 通过 Redis 分布式锁防止多实例重复执行。
     *
     * @param job  任务定义
     * @param type 调度类型（FIXED_RATE / FIXED_DELAY）
     * @return 注册成功返回 true，否则返回 false
     */
    private boolean registerFixedRateJob(Job job, ScheduleType type) {
        // Leader 模式：委托给 SecondLevelScheduler
        if (cronjobProperties.getLeader().isEnabled()) {
            SecondLevelScheduler scheduler = secondLevelSchedulerProvider != null
                    ? secondLevelSchedulerProvider.getIfAvailable() : null;
            if (scheduler == null) {
                log.warn("[Cronjob] Leader 模式但 SecondLevelScheduler 未启用, FIXED_RATE/FIXED_DELAY 任务无法注册: key={}",
                        job.getJobKey());
                return false;
            }
            return scheduler.register(job);
        }
        // Leaderless 模式：回退到本地 TaskScheduler
        long intervalMs;
        if (type == ScheduleType.FIXED_RATE) {
            intervalMs = job.getFixedRateMs() == null ? 0 : job.getFixedRateMs();
        } else {
            intervalMs = job.getFixedDelayMs() == null ? 0 : job.getFixedDelayMs();
        }
        if (intervalMs <= 0) {
            log.warn("[Cronjob] 注册失败: 任务 {} 间隔非法, type={} fixedRateMs={} fixedDelayMs={}",
                    job.getJobKey(), type, job.getFixedRateMs(), job.getFixedDelayMs());
            return false;
        }
        if (scheduledMap.containsKey(job.getJobKey())) {
            unregister(job.getJobKey());
        }
        try {
            ScheduledFuture<?> f;
            if (type == ScheduleType.FIXED_RATE) {
                f = taskScheduler.scheduleAtFixedRate(
                        () -> executeJob(job, false),
                        Duration.ofMillis(intervalMs)
                );
            } else {
                f = taskScheduler.scheduleWithFixedDelay(
                        () -> executeJob(job, false),
                        Duration.ofMillis(intervalMs)
                );
            }
            scheduledMap.put(job.getJobKey(), f);
            log.info("[Cronjob] 注册 {} 任务成功: key={} intervalMs={}",
                    type, job.getJobKey(), intervalMs);
            return true;
        } catch (Exception e) {
            log.error("[Cronjob] 注册 {} 任务失败: key={} reason={}",
                    type, job.getJobKey(), e.getMessage());
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
     * 注销 SecondLevelScheduler 中的调度（P0-3）。
     *
     * <p>仅 Leader 模式下 SecondLevelScheduler Bean 存在时才调用；
     * Leaderless 模式下为空操作（FIXED_RATE/FIXED_DELAY 已由 {@link #unregister} 注销本地调度）。
     *
     * @param jobId 任务 ID
     */
    private void unregisterFromSecondLevel(String jobId) {
        if (jobId == null) {
            return;
        }
        SecondLevelScheduler scheduler = secondLevelSchedulerProvider != null
                ? secondLevelSchedulerProvider.getIfAvailable() : null;
        if (scheduler != null) {
            scheduler.unregister(jobId);
        }
    }

    /**
     * 重新注册（用于更新 Cron）
     *
     * @param job 任务定义
     * @return 重新注册成功返回 true，否则返回 false
     */
    @Override
    public boolean reschedule(Job job) {
        unregister(job.getJobKey());
        return register(job);
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
    public Job getById(String id) {
        Job j = jobMapper.selectById(id);
        if (j == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.cronjob.msg_c0d8369f");
        }
        return j;
    }

    /**
     * 分页查询任务
     *
     * @param page    页码
     * @param size    每页条数
     * @param keyword 关键字（任务名/KEY/处理器，可选）
     * @param status  状态过滤（可选）
     * @param group   分组过滤（可选）
     * @return 任务分页数据
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Job> page(int page, int size, String keyword, String status, String group) {
        Page<Job> p = new Page<>(page, size);
        LambdaQueryWrapper<Job> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(Job::getJobName, keyword)
                    .or().like(Job::getJobKey, keyword)
                    .or().like(Job::getHandler, keyword));
        }
        if (StringUtils.hasText(status)) {
            w.eq(Job::getStatus, status);
        }
        if (StringUtils.hasText(group)) {
            w.eq(Job::getJobGroup, group);
        }
        w.orderByDesc(Job::getCreatedAt);
        return jobMapper.selectPage(p, w);
    }

    /**
     * 分页查询执行日志
     *
     * @param page   页码
     * @param size   每页条数
     * @param jobKey 任务 KEY 过滤（可选）
     * @param status 状态过滤（可选）
     * @return 执行日志分页数据
     */
    @Override
    @Transactional(readOnly = true)
    public Page<JobLog> pageLog(int page, int size, String jobKey, String status) {
        Page<JobLog> p = new Page<>(page, size);
        LambdaQueryWrapper<JobLog> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(jobKey)) {
            w.eq(JobLog::getJobKey, jobKey);
        }
        if (StringUtils.hasText(status)) {
            w.eq(JobLog::getStatus, status);
        }
        w.orderByDesc(JobLog::getStartTime);
        return jobLogMapper.selectPage(p, w);
    }

    // ==================== 内部执行逻辑 ====================

    /**
     * 执行任务内部逻辑
     *
     * <p>定时触发（非手动）时通过 Redis 分布式锁防止多实例重复执行；
     * 记录执行日志（开始/结束/耗时/状态/结果）并更新任务统计字段。
     *
     * @param job    任务定义
     * @param manual 是否手动触发（手动触发不加分布式锁）
     * @return 执行日志 ID；定时触发且锁已被持有时返回 null
     */
    private String executeJob(Job job, boolean manual) {
        // 定时触发（非手动）时获取分布式锁，防止多实例重复执行
        // P0-4: TTL 支持任务级 override + 全局配置 + 上下限规整
        String lockKey = null;
        if (!manual) {
            lockKey = LockKeyUtil.buildJobLockKey(job.getJobKey());
            Duration ttl = resolveLockTtl(job);
            Boolean acquired = redisService.getRedisTemplate().opsForValue()
                    .setIfAbsent(lockKey, INSTANCE_ID, ttl);
            if (!Boolean.TRUE.equals(acquired)) {
                log.info("[Cronjob] 任务已被其他实例持有锁, 跳过本次执行: key={}", job.getJobKey());
                return null;
            }
            log.debug("[Cronjob] 获取分布式锁成功: key={} holder={} ttl={}ms",
                    lockKey, INSTANCE_ID, ttl.toMillis());
        }

        // 写开始日志
        JobLog log0 = new JobLog();
        log0.setJobId(job.getId());
        log0.setJobKey(job.getJobKey());
        log0.setStartTime(LocalDateTime.now());
        log0.setStatus("RUNNING");
        log0.setParamsJson(job.getParamsJson());
        log0.setTraceId(TracerUtils.getTraceId());
        log0.setDeleted(0);
        jobLogMapper.insert(log0);

        boolean success = false;
        String error = null;
        Object result = null;
        try {
            JobHandler handler = applicationContext.getBean(job.getHandler(), JobHandler.class);
            result = handler.execute(job.getParamsJson());
            success = true;
            log0.setResultJson(result == null ? null : YdszJson.toJson(result));
        } catch (Exception e) {
            log.error("[Cronjob] 任务执行失败: key={} handler={} reason={}",
                    job.getJobKey(), job.getHandler(), e.getMessage(), e);
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
            log0.setErrorMessage(error);
        } finally {
            log0.setEndTime(LocalDateTime.now());
            log0.setDurationMs(Duration.between(log0.getStartTime(), log0.getEndTime()).toMillis());
            log0.setStatus(success ? "SUCCESS" : "FAILED");
            jobLogMapper.updateById(log0);

            // 更新任务统计
            Long incFire = 1L;
            Long incSucc = success ? 1L : 0L;
            Long incFail = success ? 0L : 1L;
            LocalDateTime next = null;
            if (!manual) {
                next = nextFireTime(job);
            }
            jobMapper.updateStats(job.getId(), log0.getStartTime(), next, incFire, incSucc, incFail,
                    success ? null : "ERROR");

            // 释放分布式锁（Lua 脚本安全释放: 仅当 value 匹配时才 delete）
            if (lockKey != null) {
                try {
                    redisService.execute(RELEASE_LOCK_SCRIPT,
                            Collections.singletonList(lockKey), INSTANCE_ID);
                } catch (Exception e) {
                    log.warn("[Cronjob] 释放分布式锁失败(将等待 TTL 自动过期): key={} reason={}",
                            lockKey, e.getMessage());
                }
            }
        }
        return log0.getId();
    }

    /**
     * 解析任务实际使用的锁 TTL。
     *
     * <p>P0-4: 优先使用任务级 {@code lockTtlMs}（如果配置且合法），
     * 否则回退到全局 {@link CronjobProperties#getJobLockTtl()}。
     * 最终经 {@link CronjobProperties#normalizeTtl(Duration)} 规整到 [min, max] 区间。
     *
     * @param job 任务定义
     * @return 规整化后的锁 TTL
     */
    private Duration resolveLockTtl(Job job) {
        Duration taskLevel = null;
        if (job.getLockTtlMs() != null && job.getLockTtlMs() > 0) {
            taskLevel = Duration.ofMillis(job.getLockTtlMs());
        }
        return cronjobProperties.normalizeTtl(taskLevel);
    }

    /**
     * 校验任务必填字段
     *
     * <p>P0-3: 根据 {@code scheduleType} 校验：
     * <ul>
     *   <li>CRON: 必须有 cronExpression</li>
     *   <li>FIXED_RATE: 必须有 fixedRateMs &gt; 0</li>
     *   <li>FIXED_DELAY: 必须有 fixedDelayMs &gt; 0</li>
     *   <li>API: 无额外必填字段</li>
     * </ul>
     *
     * @param job 任务定义
     * @throws SysException 当 jobKey/handler 为空或调度参数非法时抛出
     */
    private void validate(Job job) {
        if (!StringUtils.hasText(job.getJobKey())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_884214e7");
        }
        if (!StringUtils.hasText(job.getHandler())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_04ebee77");
        }
        // P2-8: 校验任务级时区（非空时必须为有效时区 ID）
        if (StringUtils.hasText(job.getTimezone())) {
            try {
                ZoneId.of(job.getTimezone());
            } catch (Exception e) {
                throw new SysException(BaseResultCode.BAD_REQUEST,
                        "error.cronjob.msg_5d0044ca", "无效的时区 ID: " + job.getTimezone());
            }
        }
        ScheduleType type = ScheduleType.parse(job.getScheduleType());
        switch (type) {
            case CRON:
                validateCron(job.getCronExpression());
                break;
            case FIXED_RATE:
                if (job.getFixedRateMs() == null || job.getFixedRateMs() <= 0) {
                    throw new SysException(BaseResultCode.BAD_REQUEST,
                            "error.cronjob.msg_5d0044ca", "fixedRateMs 必须为正数");
                }
                break;
            case FIXED_DELAY:
                if (job.getFixedDelayMs() == null || job.getFixedDelayMs() <= 0) {
                    throw new SysException(BaseResultCode.BAD_REQUEST,
                            "error.cronjob.msg_5d0044ca", "fixedDelayMs 必须为正数");
                }
                break;
            case API:
                // API 类型仅手动触发，无额外必填字段
                break;
            default:
                // 不会到达此处（parse 方法已兜底）
                validateCron(job.getCronExpression());
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
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_35ac148f");
        }
        try {
            new CronTrigger(cron);
        } catch (Exception e) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_5d0044ca", e.getMessage());
        }
    }

    /**
     * 构造 CronTrigger（P2-8: 支持任务级时区）。
     *
     * <p>P0-3 修复: 不再使用系统默认时区，避免多时区部署时触发时间漂移。
     * P2-8: 优先使用任务级时区，为空时回退到 {@link #SCHEDULE_TIMEZONE}。
     *
     * @param job 任务定义（含 cron 表达式和时区）
     * @return CronTrigger 实例
     */
    private CronTrigger buildTrigger(Job job) {
        String tz = StringUtils.hasText(job.getTimezone()) ? job.getTimezone() : SCHEDULE_TIMEZONE.getID();
        return new CronTrigger(job.getCronExpression(), TimeZone.getTimeZone(tz));
    }

    /**
     * 计算下次触发时间（P2-8: 支持任务级时区）。
     *
     * <p>P0-5 修复: 仅调用一次 expr.next() 避免竞态条件。
     * P2-8: 优先使用 {@link Job#getTimezone()} 指定的时区计算当前时间，
     * 为空时回退到默认时区 Asia/Shanghai。
     *
     * @param job 任务定义（含 cron 表达式和时区）
     * @return 下次触发时间；表达式非法时返回 null
     */
    private LocalDateTime nextFireTime(Job job) {
        try {
            // P2-8: 任务级时区，null 使用默认 Asia/Shanghai
            String tz = StringUtils.hasText(job.getTimezone()) ? job.getTimezone() : "Asia/Shanghai";
            ZoneId zoneId = ZoneId.of(tz);
            CronExpression expr = CronExpression.parse(job.getCronExpression());
            // P0-5 修复: 仅调用一次 expr.next() 避免竞态条件
            // P2-8: 使用任务时区的当前时间计算
            LocalDateTime now = LocalDateTime.now(zoneId);
            return expr.next(now);
        } catch (Exception e) {
            log.warn("[Cronjob] 计算 nextFireTime 失败: cron={} tz={} err={}",
                    job.getCronExpression(), job.getTimezone(), e.getMessage());
            return null;
        }
    }
}
