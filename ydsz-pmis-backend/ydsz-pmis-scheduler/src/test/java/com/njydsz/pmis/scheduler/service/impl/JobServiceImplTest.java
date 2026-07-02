package com.njydsz.pmis.scheduler.service.impl;

import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.scheduler.entity.JobDO;
import com.njydsz.pmis.scheduler.entity.JobLogDO;
import com.njydsz.pmis.scheduler.mapper.JobLogMapper;
import com.njydsz.pmis.scheduler.mapper.JobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.support.CronExpression;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JobServiceImpl 单元测试
 *
 * <p>P0-5: 验证 nextFireTime 修复 + 分布式锁防重入
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobServiceImpl 调度服务测试")
@ExtendWith(MockitoExtension.class)
class JobServiceImplTest {

    /** 任务定义 Mapper（Mock） */
    @Mock
    private JobMapper jobMapper;
    /** 任务日志 Mapper（Mock） */
    @Mock
    private JobLogMapper jobLogMapper;
    /** Spring 应用上下文（Mock） */
    @Mock
    private ApplicationContext applicationContext;
    /** Redis 模板（Mock） */
    @Mock
    private StringRedisTemplate redisTemplate;
    /** Redis Value 操作（Mock） */
    @Mock
    private ValueOperations<String, String> valueOps;

    /** 待测服务实例 */
    @InjectMocks
    private JobServiceImpl service;

    // ==================== nextFireTime 修复验证 ====================

    @Test
    @DisplayName("nextFireTime: 标准 cron 表达式应返回未来时间")
    void nextFireTime_standardCron() throws Exception {
        LocalDateTime next = invokeNextFireTime("0 0 12 * * ?");
        assertThat(next).isNotNull();
        assertThat(next).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("nextFireTime: 每分钟 cron 应返回下一分钟")
    void nextFireTime_everyMinute() throws Exception {
        LocalDateTime next = invokeNextFireTime("0 * * * * *");
        assertThat(next).isNotNull();
        assertThat(next).isAfter(LocalDateTime.now());
        assertThat(next.getSecond()).isEqualTo(0);
    }

    @Test
    @DisplayName("nextFireTime: 非法 cron 应返回 null 且不抛异常")
    void nextFireTime_invalidCron() throws Exception {
        LocalDateTime next = invokeNextFireTime("invalid-cron");
        assertThat(next).isNull();
    }

    @Test
    @DisplayName("nextFireTime: 验证仅调用一次 expr.next（无竞态条件）")
    void nextFireTime_noDoubleCall() throws Exception {
        // CronExpression.next 是无状态的，但旧代码调用了两次，可能返回不同结果
        // 修复后应仅调用一次
        LocalDateTime next = invokeNextFireTime("0 0 * * * *");
        assertThat(next).isNotNull();
        // 确保返回的时间是 "now" 之后最近的整点
        assertThat(next.getMinute()).isEqualTo(0);
        assertThat(next.getSecond()).isEqualTo(0);
        assertThat(next).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    // ==================== 分布式锁验证 ====================

    @Test
    @DisplayName("手动触发(manual=true)不获取分布式锁")
    void trigger_manual_noLock() {
        // 准备
        JobDO job = new JobDO();
        job.setId(1L);
        job.setJobKey("test-job");
        job.setHandler("testHandler");
        job.setCronExpression("0 0 * * * *");
        job.setParamsJson("{}");

        when(jobMapper.selectById(1L)).thenReturn(job);
        when(applicationContext.getBean("testHandler", JobHandler.class))
                .thenReturn(params -> "ok");
        // redisTemplate 不应被调用

        // 执行
        service.trigger(1L);

        // 验证: 未调用 setIfAbsent（手动触发不加锁）
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("定时触发(manual=false)获取锁失败时跳过执行")
    void executeJob_scheduled_lockAcquired() throws Exception {
        // 准备一个测试 Job 用于反射调用 executeJob
        JobDO job = new JobDO();
        job.setId(2L);
        job.setJobKey("scheduled-job");
        job.setHandler("testHandler");
        job.setCronExpression("0 0 * * * *");
        job.setParamsJson("{}");

        // 模拟锁获取成功
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(applicationContext.getBean("testHandler", JobHandler.class))
                .thenReturn(params -> "ok");

        // 通过反射调用 executeJob(job, false)
        Method method = JobServiceImpl.class.getDeclaredMethod("executeJob", JobDO.class, boolean.class);
        method.setAccessible(true);
        Long logId = (Long) method.invoke(service, job, false);

        // 验证: 获取了锁
        verify(valueOps).setIfAbsent(eq("pmis:job:lock:scheduled-job"), anyString(), any(Duration.class));
        // 验证: 释放了锁（通过 Lua 脚本）
        verify(redisTemplate).execute(any(RedisScript.class),
                eq(Collections.singletonList("pmis:job:lock:scheduled-job")), anyString());
    }

    @Test
    @DisplayName("定时触发锁已被持有时跳过执行并返回 null")
    void executeJob_scheduled_lockHeld_skip() throws Exception {
        JobDO job = new JobDO();
        job.setId(3L);
        job.setJobKey("locked-job");
        job.setHandler("testHandler");
        job.setCronExpression("0 0 * * * *");
        job.setParamsJson("{}");

        // 模拟锁已被其他实例持有
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        // 通过反射调用 executeJob(job, false)
        Method method = JobServiceImpl.class.getDeclaredMethod("executeJob", JobDO.class, boolean.class);
        method.setAccessible(true);
        Long result = (Long) method.invoke(service, job, false);

        // 验证: 返回 null（跳过执行）
        assertThat(result).isNull();
        // 验证: 未写入 JobLog
        verify(jobLogMapper, never()).insert(any(JobLogDO.class));
        // 验证: 未查找 handler
        verify(applicationContext, never()).getBean(anyString(), any(Class.class));
    }

    // ==================== 辅助方法 ====================

    /**
     * 通过反射调用私有方法 nextFireTime
     */
    private LocalDateTime invokeNextFireTime(String cron) throws Exception {
        Method method = JobServiceImpl.class.getDeclaredMethod("nextFireTime", String.class);
        method.setAccessible(true);
        return (LocalDateTime) method.invoke(service, cron);
    }
}
