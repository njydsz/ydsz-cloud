package com.njydsz.pmis.scheduler.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.scheduler.entity.JobDO;
import com.njydsz.pmis.scheduler.entity.JobLogDO;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.scheduler.mapper.JobLogMapper;
import com.njydsz.pmis.scheduler.mapper.JobMapper;
import com.njydsz.pmis.scheduler.service.JobService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
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

    private final JobMapper jobMapper;
    private final JobLogMapper jobLogMapper;
    private final ApplicationContext applicationContext;

    /** 调度器 */
    private TaskScheduler taskScheduler;

    /** 已调度的任务: jobKey -> Future */
    private final Map<String, ScheduledFuture<?>> scheduledMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void initScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(8);
        s.setThreadNamePrefix("pmis-job-");
        s.setWaitForTasksToCompleteOnShutdown(true);
        s.setAwaitTerminationSeconds(30);
        s.initialize();
        this.taskScheduler = s;
        log.info("[Scheduler] 任务调度器初始化完成, poolSize=8");
    }

    @PreDestroy
    public void destroy() {
        scheduledMap.values().forEach(f -> f.cancel(true));
        scheduledMap.clear();
        log.info("[Scheduler] 任务调度器已关闭");
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            loadOnStartup();
        } catch (Exception e) {
            log.error("[Scheduler] 启动加载任务失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void loadOnStartup() {
        List<JobDO> list = jobMapper.selectAllNormal();
        log.info("[Scheduler] 启动加载任务数量: {}", list.size());
        for (JobDO j : list) {
            try {
                register(j);
            } catch (Exception e) {
                log.warn("[Scheduler] 注册任务失败: key={} reason={}", j.getJobKey(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(JobDO job) {
        validate(job);
        if (jobMapper.selectByJobKey(job.getJobKey()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "任务 KEY 已存在: " + job.getJobKey());
        }
        if (job.getStatus() == null) {
            job.setStatus("NORMAL");
        }
        if (job.getJobGroup() == null) {
            job.setJobGroup("DEFAULT");
        }
        if (job.getTenantId() == null) {
            job.setTenantId(1L);
        }
        // 计算 nextFireTime
        LocalDateTime next = nextFireTime(job.getCronExpression());
        job.setNextFireTime(next);
        jobMapper.insert(job);
        if ("NORMAL".equals(job.getStatus())) {
            register(job);
        }
        log.info("[Scheduler] 创建任务: key={} cron={} handler={}", job.getJobKey(), job.getCronExpression(), job.getHandler());
        return job.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(JobDO job) {
        if (job.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务 ID 不能为空");
        }
        JobDO exists = jobMapper.selectById(job.getId());
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "任务不存在");
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
        log.info("[Scheduler] 更新任务: key={}", exists.getJobKey());
    }

    @Override
    public void delete(Long id) {
        JobDO j = jobMapper.selectById(id);
        if (j == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "任务不存在");
        }
        unregister(j.getJobKey());
        jobMapper.deleteById(id);
        log.info("[Scheduler] 删除任务: key={}", j.getJobKey());
    }

    @Override
    public void pause(Long id) {
        JobDO j = getById(id);
        unregister(j.getJobKey());
        j.setStatus("PAUSED");
        jobMapper.updateById(j);
        log.info("[Scheduler] 暂停任务: key={}", j.getJobKey());
    }

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
        log.info("[Scheduler] 恢复任务: key={}", j.getJobKey());
    }

    @Override
    public Long trigger(Long id) {
        JobDO j = getById(id);
        return executeJob(j, true);
    }

    @Override
    public boolean register(JobDO job) {
        if (!"NORMAL".equals(job.getStatus())) {
            return false;
        }
        if (!StringUtils.hasText(job.getCronExpression())) {
            log.warn("[Scheduler] 注册失败: 任务 {} cron 表达式为空", job.getJobKey());
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
            log.info("[Scheduler] 注册任务成功: key={} cron={}", job.getJobKey(), job.getCronExpression());
            return true;
        } catch (Exception e) {
            log.error("[Scheduler] 注册任务失败: key={} reason={}", job.getJobKey(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean unregister(String jobKey) {
        ScheduledFuture<?> f = scheduledMap.remove(jobKey);
        if (f != null) {
            f.cancel(false);
            log.info("[Scheduler] 注销任务: key={}", jobKey);
            return true;
        }
        return false;
    }

    @Override
    public boolean reschedule(JobDO job) {
        unregister(job.getJobKey());
        return register(job);
    }

    @Override
    public JobDO getById(Long id) {
        JobDO j = jobMapper.selectById(id);
        if (j == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "任务不存在");
        }
        return j;
    }

    @Override
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

    @Override
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

    private Long executeJob(JobDO job, boolean manual) {
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
            log.error("[Scheduler] 任务执行失败: key={} handler={} reason={}",
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
        }
        return log0.getId();
    }

    private void validate(JobDO job) {
        if (!StringUtils.hasText(job.getJobKey())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "jobKey 不能为空");
        }
        if (!StringUtils.hasText(job.getHandler())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "handler 不能为空");
        }
        validateCron(job.getCronExpression());
    }

    private void validateCron(String cron) {
        if (!StringUtils.hasText(cron)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "cron 表达式不能为空");
        }
        try {
            new CronTrigger(cron);
        } catch (Exception e) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "cron 表达式不合法: " + e.getMessage());
        }
    }

    private CronTrigger buildTrigger(String cron) {
        return new CronTrigger(cron, TimeZone.getDefault());
    }

    private LocalDateTime nextFireTime(String cron) {
        try {
            CronExpression expr = CronExpression.parse(cron);
            return expr.next(LocalDateTime.now()) == null ? null
                    : expr.next(LocalDateTime.now()).atZone(ZoneId.systemDefault())
                            .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(java.time.ZonedDateTime zdt) {
        return zdt == null ? null : LocalDateTime.ofInstant(zdt.toInstant(), java.time.ZoneId.systemDefault());
    }
}
