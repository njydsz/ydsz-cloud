package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.executor.JobNodeHeartbeat;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultTaskDispatcher} 单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>dispatch(MANUAL) 不抢锁直接执行</li>
 *   <li>dispatch(CRON) 锁获取成功时执行</li>
 *   <li>dispatch(CRON) 锁被持有时返回 null</li>
 *   <li>handler 抛异常时日志标 FAILED 并释放锁</li>
 *   <li>成功后释放锁并调用心跳组件</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DefaultTaskDispatcher 任务派发器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultTaskDispatcherTest {

    @Mock
    private JobMapper jobMapper;
    @Mock
    private JobLogMapper jobLogMapper;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private JobHandler jobHandler;
    @Mock
    private JobNodeHeartbeat jobNodeHeartbeat;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private DefaultTaskDispatcher dispatcher;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties（@RequiredArgsConstructor 不识别非 @Mock 字段）
        try {
            java.lang.reflect.Field f = DefaultTaskDispatcher.class.getDeclaredField("cronjobProperties");
            f.setAccessible(true);
            f.set(dispatcher, cronjobProperties);
        } catch (Exception e) {
            throw new IllegalStateException("注入 cronjobProperties 失败", e);
        }

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(applicationContext.getBean(anyString(), eq(JobHandler.class))).thenReturn(jobHandler);
        lenient().when(jobLogMapper.insert(any(JobLogDO.class))).thenAnswer(invocation -> {
            JobLogDO log = invocation.getArgument(0);
            log.setId("log-test-" + System.nanoTime());
            return 1;
        });
    }

    @Test
    @DisplayName("dispatch(MANUAL) 不抢锁直接执行并返回日志 ID")
    void dispatch_manual_doesNotAcquireLock() throws Exception {
        JobDO job = buildJob("manual-key", null);
        when(jobHandler.execute(any())).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_MANUAL);

        assertNotNull(logId);
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(jobLogMapper, times(1)).insert(any(JobLogDO.class));
        verify(jobLogMapper, times(1)).updateById(any(JobLogDO.class));
        verify(jobNodeHeartbeat, times(1)).onTaskStart();
        verify(jobNodeHeartbeat, times(1)).onTaskComplete();
    }

    @Test
    @DisplayName("dispatch(CRON) 锁获取成功时执行任务")
    void dispatch_cron_lockAcquired_executesJob() throws Exception {
        JobDO job = buildJob("cron-key", null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(jobHandler.execute(any())).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        assertNotNull(logId);
        verify(valueOps, times(1)).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), any(), (Object) any());
    }

    @Test
    @DisplayName("dispatch(CRON) 锁被持有时返回 null")
    void dispatch_cron_lockHeld_returnsNull() {
        JobDO job = buildJob("held-key", null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        assertNull(logId);
        verify(jobLogMapper, never()).insert(any(JobLogDO.class));
        verify(jobNodeHeartbeat, never()).onTaskStart();
    }

    @Test
    @DisplayName("dispatch(CRON) 执行失败时日志标 FAILED 且释放锁")
    void dispatch_cron_executionFailed_releasesLock() throws Exception {
        JobDO job = buildJob("fail-key", null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(jobHandler.execute(any())).thenThrow(new RuntimeException("boom"));

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        assertNotNull(logId);
        verify(jobLogMapper, times(1)).updateById(any(JobLogDO.class));
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), any(), (Object) any());
        verify(jobNodeHeartbeat, times(1)).onTaskStart();
        verify(jobNodeHeartbeat, times(1)).onTaskComplete();
    }

    @Test
    @DisplayName("dispatch(MANUAL) handler 抛异常时任务状态标 ERROR")
    void dispatch_manual_handlerThrows_statusError() throws Exception {
        JobDO job = buildJob("err-key", null);
        when(jobHandler.execute(any())).thenThrow(new RuntimeException("err"));

        dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_MANUAL);

        // 手动触发不更新 next_fire_time（triggerType != CRON）
        verify(jobMapper, times(1)).updateStats(anyString(), any(), eq(null),
                eq(1L), eq(0L), eq(1L), eq("ERROR"));
    }

    @Test
    @DisplayName("dispatch(CRON) 任务级 lockTtlMs 在 [30s,24h] 区间内使用任务级 TTL")
    void dispatch_cron_taskLevelTtl_inRange() {
        JobDO job = buildJob("ttl-key", Duration.ofMinutes(10).toMillis());
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        verify(valueOps).setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(10)));
    }

    private JobDO buildJob(String key, Long lockTtlMs) {
        JobDO job = new JobDO();
        job.setId("job-" + key);
        job.setJobKey(key);
        job.setJobName("测试任务 " + key);
        job.setHandler("testHandler");
        job.setCronExpression("0 0 8 * * ?");
        job.setStatus("NORMAL");
        job.setLockTtlMs(lockTtlMs);
        return job;
    }
}
