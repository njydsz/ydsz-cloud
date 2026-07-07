package com.njydsz.pmis.cronjob.core.dispatch;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.executor.JobNodeHeartbeat;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

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

    /** 缓存: jobKey -> 上次扫描的下次触发时间（用于避免重复派发） */
    private final Map<String, LocalDateTime> lastDispatchedNextFire = new ConcurrentHashMap<>();

    @Override
    public String dispatch(JobDO job, String executorNode, String triggerType) {
        // 当前实现：executorNode 参数忽略，始终本地执行（P3 阶段扩展远程派发）
        boolean holdLock = !TRIGGER_MANUAL.equals(triggerType);
        return executeJob(job, holdLock, triggerType);
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
        return log0.getId();
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
            Date next = trigger.nextExecutionTime(ctx);
            return next == null ? null : LocalDateTime.ofInstant(next.toInstant(),
                    ZoneId.systemDefault());
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
