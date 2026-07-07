package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.alert.AlertTrigger;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.metrics.CronjobMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TimeoutMonitor} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("TimeoutMonitor 任务超时监控测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class TimeoutMonitorTest {

    @Mock
    private JobMapper jobMapper;
    @Mock
    private JobLogMapper jobLogMapper;
    @Mock
    private LeaderElector leaderElector;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ObjectProvider<AlertTrigger> alertTriggerProvider;
    @Mock
    private ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private TimeoutMonitor monitor;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        try {
            java.lang.reflect.Field f = TimeoutMonitor.class.getDeclaredField("cronjobProperties");
            f.setAccessible(true);
            f.set(monitor, cronjobProperties);
        } catch (Exception e) {
            throw new IllegalStateException("注入 cronjobProperties 失败", e);
        }
        // P6-2: 手动注入 ObjectProvider 字段，避免 @InjectMocks 因类型擦除将
        // alertTriggerProvider 与 cronjobMetricsProvider 互相错位注入
        try {
            java.lang.reflect.Field f1 = TimeoutMonitor.class.getDeclaredField("alertTriggerProvider");
            f1.setAccessible(true);
            f1.set(monitor, alertTriggerProvider);
            java.lang.reflect.Field f2 = TimeoutMonitor.class.getDeclaredField("cronjobMetricsProvider");
            f2.setAccessible(true);
            f2.set(monitor, cronjobMetricsProvider);
        } catch (Exception e) {
            throw new IllegalStateException("注入 ObjectProvider 失败", e);
        }
        monitor.init();
        lenient().when(leaderElector.isLeader(anyString())).thenReturn(true);
        // P5/P6-2: ObjectProvider 默认返回 null（告警触发器与指标收集器在测试中不启用）
        lenient().when(alertTriggerProvider.getIfAvailable()).thenReturn(null);
        lenient().when(cronjobMetricsProvider.getIfAvailable()).thenReturn(null);
    }

    @Test
    @DisplayName("leader.enabled=false 时 scan 不执行")
    void scan_leaderDisabled_skip() {
        cronjobProperties.getLeader().setEnabled(false);

        monitor.scan();

        verify(jobLogMapper, never()).selectTimedOutLogs(any(), anyInt());
    }

    @Test
    @DisplayName("非 Leader 时 scan 不执行")
    void scan_notLeader_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(false);

        monitor.scan();

        verify(jobLogMapper, never()).selectTimedOutLogs(any(), anyInt());
    }

    @Test
    @DisplayName("无超时日志时仅记录不调用 markTimeout")
    void scan_noTimeoutLogs_noAction() {
        cronjobProperties.getLeader().setEnabled(true);
        when(jobLogMapper.selectTimedOutLogs(any(), anyInt())).thenReturn(Collections.emptyList());

        monitor.scan();

        verify(jobLogMapper, never()).markTimeout(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("发现超时日志时调用 markTimeout + 释放锁 + 更新统计")
    void scan_timeoutFound_callsMarkTimeoutAndReleaseLock() {
        cronjobProperties.getLeader().setEnabled(true);
        JobLogDO log0 = new JobLogDO();
        log0.setId("log-1");
        log0.setJobId("job-1");
        log0.setJobKey("timeout-key");
        log0.setStartTime(LocalDateTime.now().minusMinutes(10));
        log0.setStatus("RUNNING");
        log0.setLockHolder("instance-1");
        when(jobLogMapper.selectTimedOutLogs(any(), anyInt())).thenReturn(List.of(log0));
        when(jobLogMapper.markTimeout(anyString(), any(), anyLong(), anyString())).thenReturn(1);

        monitor.scan();

        verify(jobLogMapper, times(1)).markTimeout(eq("log-1"), any(), anyLong(), anyString());
        // P0-1: 锁释放改用 Lua 脚本安全释放（仅当 holder 匹配时才 delete）
        verify(redisTemplate, times(1)).execute(any(RedisScript.class),
                eq(Collections.singletonList("pmis:job:lock:timeout-key")), eq("instance-1"));
        verify(jobMapper, times(1)).updateStats(eq("job-1"), any(), eq(null),
                eq(null), eq(0L), eq(1L), eq("ERROR"));
    }

    @Test
    @DisplayName("markTimeout 返回 0（CAS 失败）时不释放锁不更新统计")
    void scan_markTimeoutCasFailed_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        JobLogDO log0 = new JobLogDO();
        log0.setId("log-2");
        log0.setJobId("job-2");
        log0.setJobKey("cas-key");
        log0.setStartTime(LocalDateTime.now().minusMinutes(10));
        log0.setStatus("RUNNING");
        when(jobLogMapper.selectTimedOutLogs(any(), anyInt())).thenReturn(List.of(log0));
        when(jobLogMapper.markTimeout(anyString(), any(), anyLong(), anyString())).thenReturn(0);

        monitor.scan();

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyString());
        verify(jobMapper, never()).updateStats(anyString(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("释放锁异常时不影响后续更新统计")
    void scan_releaseLockException_continuesUpdateStats() {
        cronjobProperties.getLeader().setEnabled(true);
        JobLogDO log0 = new JobLogDO();
        log0.setId("log-3");
        log0.setJobId("job-3");
        log0.setJobKey("fail-lock-key");
        log0.setStartTime(LocalDateTime.now().minusMinutes(10));
        log0.setStatus("RUNNING");
        log0.setLockHolder("instance-3");
        when(jobLogMapper.selectTimedOutLogs(any(), anyInt())).thenReturn(List.of(log0));
        when(jobLogMapper.markTimeout(anyString(), any(), anyLong(), anyString())).thenReturn(1);
        doThrow(new RuntimeException("redis conn err"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), eq("instance-3"));

        monitor.scan(); // 不应抛异常

        verify(jobMapper, times(1)).updateStats(eq("job-3"), any(), eq(null),
                eq(null), eq(0L), eq(1L), eq("ERROR"));
    }

    @Test
    @DisplayName("多个超时日志时逐个处理")
    void scan_multipleTimeouts_processesEach() {
        cronjobProperties.getLeader().setEnabled(true);
        JobLogDO log1 = new JobLogDO();
        log1.setId("log-a");
        log1.setJobId("job-a");
        log1.setJobKey("key-a");
        log1.setStartTime(LocalDateTime.now().minusMinutes(20));
        log1.setStatus("RUNNING");
        log1.setLockHolder("instance-a");
        JobLogDO log2 = new JobLogDO();
        log2.setId("log-b");
        log2.setJobId("job-b");
        log2.setJobKey("key-b");
        log2.setStartTime(LocalDateTime.now().minusMinutes(15));
        log2.setStatus("RUNNING");
        log2.setLockHolder("instance-b");
        when(jobLogMapper.selectTimedOutLogs(any(), anyInt())).thenReturn(List.of(log1, log2));
        when(jobLogMapper.markTimeout(anyString(), any(), anyLong(), anyString())).thenReturn(1);

        monitor.scan();

        verify(jobLogMapper, times(2)).markTimeout(anyString(), any(), anyLong(), anyString());
        // P0-1: 锁释放改用 Lua 脚本安全释放（仅当 holder 匹配时才 delete）
        verify(redisTemplate, times(1)).execute(any(RedisScript.class),
                eq(Collections.singletonList("pmis:job:lock:key-a")), eq("instance-a"));
        verify(redisTemplate, times(1)).execute(any(RedisScript.class),
                eq(Collections.singletonList("pmis:job:lock:key-b")), eq("instance-b"));
        verify(jobMapper, times(2)).updateStats(anyString(), any(), any(),
                any(), any(), any(), anyString());
    }
}
