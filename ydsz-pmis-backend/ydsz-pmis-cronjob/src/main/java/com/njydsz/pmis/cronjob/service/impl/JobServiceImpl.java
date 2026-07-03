package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.service.JobService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 任务调度服务实现
 *
 * @author ydsz-pmis-team
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
    private final StringRedisTemplate redisTemplate;

    /** 调度器 */
    private TaskScheduler taskScheduler;

    /** 已调度的任务: jobKey -> Future */
    private final Map<String, ScheduledFuture<?>> scheduledMap = new ConcurrentHashMap<>();

    // ==================== 分布式锁常量 ====================

    /** 任务锁 key 前缀 */
    private static final String JOB_LOCK_PREFIX = "pmis:job:lock:";

    /** 任务锁默认 TTL: 5 分钟（防止节点宕机导致锁不释放） */
    private static final Duration JOB_LOCK_TTL = Duration.ofMinutes(5);

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
     * 初始化任务调度器（线程池大小 8，关闭时等待任务完成）
     */
    @PostConstruct
    public void initScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(8);
        s.setThreadNamePrefix("pmis-job-");
        s.setWaitForTasksToCompleteOnShutdown(true);
        s.setAwaitTerminationSeconds(30);
        s.initialize();
        this.taskScheduler = s;
        log.info("[Cronjob] 任务调度器初始化完成, poolSize=8");
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
     * 应用启动回调，加载所有 NORMAL 任务到调度器
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
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
        List<JobDO> list = jobMapper.selectAllNormal();
        log.info("[Cronjob] 启动加载任务数量: {}", list.size());
        for (JobDO j : list) {
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
     * @param job 任务定义
     * @return 新增任务 ID
     * @throws BizException 当 jobKey 已存在或参数非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(JobDO job) {
        validate(job);
        if (jobMapper.selectByJobKey(job.getJobKey()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "error.cronjob.msg_7e5ef640" + job.getJobKey());
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
        // 计算 nextFireTime
        LocalDateTime next = nextFireTime(job.getCronExpression());
        job.setNextFireTime(next);
        jobMapper.insert(job);
        if ("NORMAL".equals(job.getStatus())) {
            register(job);
        }
        log.info("[Cronjob] 创建任务: key={} cron={} handler={}", job.getJobKey(), job.getCronExpression(), job.getHandler());
        return job.getId();
    }

    /**
     * 更新任务
     *
     * @param job 任务定义
     * @throws BizException 当任务不存在或 cron 表达式非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(JobDO job) {
        if (job.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_ce91ca69");
        }
        JobDO exists = jobMapper.selectById(job.getId());
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_c0d8369f");
        }
        if (StringUtils.hasText(job.getCronExpression())) {
            validateCron(job.getCronExpression());
        }
        // 重新计算 nextFireTime
        if (StringUtils.hasText(job.getCronExpression())) {
            exists.setNextFireTime(nextFireTime(job.getCronExpression()));
        }
        if (StringUtils.hasText(job.getHandler())) exists.setHandler(job.getHandler());
        if (StringUtils.hasText(job.getJobName())) exists.setJobName(job.getJobName());
        if (StringUtils.hasText(job.getJobGroup())) exists.setJobGroup(job.getJobGroup());
        if (job.getParamsJson() != null) exists.setParamsJson(job.getParamsJson());
        if (StringUtils.hasText(job.getStatus())) exists.setStatus(job.getStatus());
        if (job.getRemark() != null) exists.setRemark(job.getRemark());
        jobMapper.updateById(exists);

        // 重新调度
        unregister(exists.getJobKey());
        if ("NORMAL".equals(exists.getStatus())) {
            register(exists);
        }
        log.info("[Cronjob] 更新任务: key={}", exists.getJobKey());
    }

    /**
     * 删除任务
     *
     * @param id 任务 ID
     * @throws BizException 当任务不存在时抛出
     */
    @Override
    public void delete(Long id) {
        JobDO j = jobMapper.selectById(id);
        if (j == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_c0d8369f");
        }
        unregister(j.getJobKey());
        jobMapper.deleteById(id);
        log.info("[Cronjob] 删除任务: key={}", j.getJobKey());
    }

    /**
     * 暂停任务
     *
     * @param id 任务 ID
     * @throws BizException 当任务不存在时抛出
     */
    @Override
    public void pause(Long id) {
        JobDO j = getById(id);
        unregister(j.getJobKey());
        j.setStatus("PAUSED");
        jobMapper.updateById(j);
        log.info("[Cronjob] 暂停任务: key={}", j.getJobKey());
    }

    /**
     * 恢复任务
     *
     * @param id 任务 ID
     * @throws BizException 当任务不存在时抛出
     */
    @Override
    public void resume(Long id) {
        JobDO j = getById(id);
        if ("NORMAL".equals(j.getStatus())) {
            if (!scheduledMap.containsKey(j.getJobKey())) {
                register(j);
            }
        } else {
            j.setStatus("NORMAL");
            jobMapper.updateById(j);
            register(j);
        }
        log.info("[Cronjob] 恢复任务: key={}", j.getJobKey());
    }

    /**
     * 立即执行一次
     *
     * @param id 任务 ID
     * @return 执行日志 ID
     * @throws BizException 当任务不存在时抛出
     */
    @Override
    public Long trigger(Long id) {
        JobDO j = getById(id);
        return executeJob(j, true);
    }

    /**
     * 注册到调度器（从 DB 加载/动态新增）
     *
     * @param job 任务定义
     * @return 注册成功返回 true，否则返回 false
     */
    @Override
    public boolean register(JobDO job) {
        if (!"NORMAL".equals(job.getStatus())) {
            return false;
        }
        if (!StringUtils.hasText(job.getCronExpression())) {
            log.warn("[Cronjob] 注册失败: 任务 {} cron 表达式为空", job.getJobKey());
            return false;
        }
        if (scheduledMap.containsKey(job.getJobKey())) {
            unregister(job.getJobKey());
        }
        try {
            CronTrigger trigger = buildTrigger(job.getCronExpression());
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
     * @param job 任务定义
     * @return 重新注册成功返回 true，否则返回 false
     */
    @Override
    public boolean reschedule(JobDO job) {
        unregister(job.getJobKey());
        return register(job);
    }

    /**
     * 详情
     *
     * @param id 任务 ID
     * @return 任务定义
     * @throws BizException 当任务不存在时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public JobDO getById(Long id) {
        JobDO j = jobMapper.selectById(id);
        if (j == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_c0d8369f");
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
    public Page<JobDO> page(int page, int size, String keyword, String status, String group) {
        Page<JobDO> p = new Page<>(page, size);
        LambdaQueryWrapper<JobDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(JobDO::getJobName, keyword)
                    .or().like(JobDO::getJobKey, keyword)
                    .or().like(JobDO::getHandler, keyword));
        }
        if (StringUtils.hasText(status)) {
            w.eq(JobDO::getStatus, status);
        }
        if (StringUtils.hasText(group)) {
            w.eq(JobDO::getJobGroup, group);
        }
        w.orderByDesc(JobDO::getCreatedAt);
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
    public Page<JobLogDO> pageLog(int page, int size, String jobKey, String status) {
        Page<JobLogDO> p = new Page<>(page, size);
        LambdaQueryWrapper<JobLogDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(jobKey)) {
            w.eq(JobLogDO::getJobKey, jobKey);
        }
        if (StringUtils.hasText(status)) {
            w.eq(JobLogDO::getStatus, status);
        }
        w.orderByDesc(JobLogDO::getStartTime);
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
    private Long executeJob(JobDO job, boolean manual) {
        // 定时触发（非手动）时获取分布式锁，防止多实例重复执行
        String lockKey = null;
        if (!manual) {
            lockKey = JOB_LOCK_PREFIX + job.getJobKey();
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, INSTANCE_ID, JOB_LOCK_TTL);
            if (!Boolean.TRUE.equals(acquired)) {
                log.info("[Cronjob] 任务已被其他实例持有锁, 跳过本次执行: key={}", job.getJobKey());
                return null;
            }
            log.debug("[Cronjob] 获取分布式锁成功: key={} holder={}", lockKey, INSTANCE_ID);
        }

        // 写开始日志
        JobLogDO log0 = new JobLogDO();
        log0.setJobId(job.getId());
        log0.setJobKey(job.getJobKey());
        log0.setStartTime(LocalDateTime.now());
        log0.setStatus("RUNNING");
        log0.setParamsJson(job.getParamsJson());
        log0.setTraceId(TraceIdUtil.get());
        log0.setCreateTime(LocalDateTime.now());
        log0.setDeleted(0);
        jobLogMapper.insert(log0);

        boolean success = false;
        String error = null;
        Object result = null;
        try {
            JobHandler handler = applicationContext.getBean(job.getHandler(), JobHandler.class);
            result = handler.execute(job.getParamsJson());
            success = true;
            log0.setResultJson(result == null ? null : JSON.toJSONString(result));
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
                next = nextFireTime(job.getCronExpression());
            }
            jobMapper.updateStats(job.getId(), log0.getStartTime(), next, incFire, incSucc, incFail,
                    success ? null : "ERROR");

            // 释放分布式锁（Lua 脚本安全释放: 仅当 value 匹配时才 delete）
            if (lockKey != null) {
                try {
                    redisTemplate.execute(RELEASE_LOCK_SCRIPT,
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
     * 校验任务必填字段
     *
     * @param job 任务定义
     * @throws BizException 当 jobKey/handler/cronExpression 为空或非法时抛出
     */
    private void validate(JobDO job) {
        if (!StringUtils.hasText(job.getJobKey())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_884214e7");
        }
        if (!StringUtils.hasText(job.getHandler())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_04ebee77");
        }
        validateCron(job.getCronExpression());
    }

    /**
     * 校验 cron 表达式合法性
     *
     * @param cron cron 表达式
     * @throws BizException 当 cron 为空或非法时抛出
     */
    private void validateCron(String cron) {
        if (!StringUtils.hasText(cron)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_35ac148f");
        }
        try {
            new CronTrigger(cron);
        } catch (Exception e) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_5d0044ca" + e.getMessage());
        }
    }

    /**
     * 构造 CronTrigger（使用系统默认时区）
     *
     * @param cron cron 表达式
     * @return CronTrigger 实例
     */
    private CronTrigger buildTrigger(String cron) {
        return new CronTrigger(cron, TimeZone.getDefault());
    }

    /**
     * 计算下次触发时间
     *
     * <p>P0-5 修复: 仅调用一次 expr.next() 避免竞态条件。
     * CronExpression 基于 LocalDateTime 直接计算，无需时区转换。
     *
     * @param cron cron 表达式
     * @return 下次触发时间；表达式非法时返回 null
     */
    private LocalDateTime nextFireTime(String cron) {
        try {
            CronExpression expr = CronExpression.parse(cron);
            // P0-5 修复: 仅调用一次 expr.next() 避免竞态条件
            // CronExpression 基于 LocalDateTime 直接计算, 无需时区转换
            return expr.next(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("[Cronjob] 计算 nextFireTime 失败: cron={} err={}", cron, e.getMessage());
            return null;
        }
    }
}
