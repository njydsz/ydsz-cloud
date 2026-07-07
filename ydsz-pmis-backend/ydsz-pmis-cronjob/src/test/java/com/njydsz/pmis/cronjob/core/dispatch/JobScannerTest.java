package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.metrics.CronjobMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobScanner} 单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>leader.enabled=false 时不扫描</li>
 *   <li>非 Leader 时不扫描</li>
 *   <li>Leader 且有 due 任务时调用 dispatcher</li>
 *   <li>CAS advanceNextFireTime 失败时跳过派发</li>
 *   <li>dispatcher 返回 null 时仅记录日志</li>
 *   <li>scanning 标志位正确管理（防重入）</li>
 *   <li>P6-1: 派发时 MDC traceId 已设置且派发后已清理</li>
 *   <li>P6-1: 多任务派发时 traceId 互不串任务</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobScanner 任务扫描器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobScannerTest {

    @Mock
    private JobMapper jobMapper;
    @Mock
    private LeaderElector leaderElector;
    @Mock
    private TaskDispatcher taskDispatcher;
    @Mock
    private ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private JobScanner scanner;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties
        try {
            java.lang.reflect.Field f = JobScanner.class.getDeclaredField("cronjobProperties");
            f.setAccessible(true);
            f.set(scanner, cronjobProperties);
        } catch (Exception e) {
            throw new IllegalStateException("注入 cronjobProperties 失败", e);
        }
        // 初始化 leaderRole
        scanner.init();
        // P6-2: CronjobMetrics 默认不可用（指标收集器在测试中不启用）
        lenient().when(cronjobMetricsProvider.getIfAvailable()).thenReturn(null);
        // 清理 MDC，避免上次测试残留
        MDC.remove(TraceIdUtil.TRACE_ID_KEY);
    }

    @AfterEach
    void tearDown() {
        // 清理 MDC，避免测试间串扰
        MDC.remove(TraceIdUtil.TRACE_ID_KEY);
    }

    @Test
    @DisplayName("leader.enabled=false 时 scan 直接返回不执行扫描")
    void scan_leaderDisabled_skip() {
        cronjobProperties.getLeader().setEnabled(false);
        when(leaderElector.isLeader(anyString())).thenReturn(true);

        scanner.scan();

        verify(jobMapper, never()).selectDueJobs(any(), anyInt());
        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
        assertFalse(scanner.isScanning());
    }

    @Test
    @DisplayName("非 Leader 节点 scan 直接返回不执行扫描")
    void scan_notLeader_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(false);

        scanner.scan();

        verify(jobMapper, never()).selectDueJobs(any(), anyInt());
        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("Leader 且无 due 任务时仅记录日志不派发")
    void scan_leaderNoDueJobs_noDispatch() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(Collections.emptyList());

        scanner.scan();

        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("Leader 且有 due 任务时调用 dispatcher 派发")
    void scan_leaderWithDueJobs_dispatches() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        JobDO job = buildDueJob("due-key", LocalDateTime.now().minusMinutes(1));
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(1);
        when(taskDispatcher.dispatch(any(), any(), anyString())).thenReturn("log-123");

        scanner.scan();

        verify(taskDispatcher, times(1)).dispatch(eq(job), any(), eq(DefaultTaskDispatcher.TRIGGER_CRON));
    }

    @Test
    @DisplayName("CAS advanceNextFireTime 返回 0 时跳过该任务派发")
    void scan_casFails_skipDispatch() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        JobDO job = buildDueJob("cas-key", LocalDateTime.now().minusMinutes(1));
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(0);

        scanner.scan();

        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("dispatcher 返回 null 时仅记录日志不抛异常")
    void scan_dispatcherReturnsNull_logsAndContinues() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        JobDO job = buildDueJob("null-key", LocalDateTime.now().minusMinutes(1));
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(1);
        when(taskDispatcher.dispatch(any(), any(), anyString())).thenReturn(null);

        scanner.scan(); // 不应抛异常

        verify(taskDispatcher, times(1)).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("dispatcher 抛异常时仅记录日志不中断后续任务")
    void scan_dispatcherThrows_continuesNextJob() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        JobDO job1 = buildDueJob("throw-key", LocalDateTime.now().minusMinutes(2));
        JobDO job2 = buildDueJob("ok-key", LocalDateTime.now().minusMinutes(1));
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job1, job2));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(1);
        when(taskDispatcher.dispatch(eq(job1), any(), anyString()))
                .thenThrow(new RuntimeException("dispatch err"));
        when(taskDispatcher.dispatch(eq(job2), any(), anyString())).thenReturn("log-ok");

        scanner.scan(); // 不应抛异常

        verify(taskDispatcher, times(2)).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("leaderRole 从配置读取并暴露")
    void getLeaderRole_returnsConfiguredRole() {
        cronjobProperties.getLeader().setRole("custom-role");
        scanner.init();
        assertEquals("custom-role", scanner.getLeaderRole());
    }

    @Test
    @DisplayName("getMisfireGrace 返回配置的窗口时长")
    void getMisfireGrace_returnsConfiguredValue() {
        cronjobProperties.getScanner().setMisfireGraceMinutes(60);
        assertEquals(java.time.Duration.ofMinutes(60), scanner.getMisfireGrace());
    }

    @Test
    @DisplayName("Misfire + SKIP 策略时仅推进 next_fire_time 不调用 dispatcher")
    void scan_misfireSkip_advancesNextFireWithoutDispatch() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        // next_fire_time 早于 now-30 分钟（超过默认 misfireGraceMinutes=30）
        JobDO job = buildDueJob("skip-key", LocalDateTime.now().minusMinutes(60));
        job.setMisfirePolicy("SKIP");
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(1);

        scanner.scan();

        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
        verify(jobMapper, times(1)).advanceNextFireTime(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("Misfire + COALESCE 策略时调用 dispatcher 且 triggerType=MISFIRED")
    void scan_misfireCoalesce_dispatchesWithMisfiredType() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        JobDO job = buildDueJob("coalesce-key", LocalDateTime.now().minusMinutes(60));
        job.setMisfirePolicy("COALESCE");
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(1);
        when(taskDispatcher.dispatch(any(), any(), anyString())).thenReturn("log-misfired");

        scanner.scan();

        verify(taskDispatcher, times(1)).dispatch(eq(job), any(), eq(DefaultTaskDispatcher.TRIGGER_MISFIRED));
    }

    @Test
    @DisplayName("Misfire + FIRE_NOW 策略时调用 dispatcher 且 triggerType=CRON（保持默认行为）")
    void scan_misfireFireNow_dispatchesWithCronType() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        JobDO job = buildDueJob("firenow-key", LocalDateTime.now().minusMinutes(60));
        job.setMisfirePolicy("FIRE_NOW");
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(1);
        when(taskDispatcher.dispatch(any(), any(), anyString())).thenReturn("log-firenow");

        scanner.scan();

        verify(taskDispatcher, times(1)).dispatch(eq(job), any(), eq(DefaultTaskDispatcher.TRIGGER_CRON));
    }

    @Test
    @DisplayName("Misfire + 无效策略值视为 FIRE_NOW")
    void scan_misfireInvalidPolicy_treatedAsFireNow() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        JobDO job = buildDueJob("invalid-key", LocalDateTime.now().minusMinutes(60));
        job.setMisfirePolicy("INVALID_VALUE");
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(1);
        when(taskDispatcher.dispatch(any(), any(), anyString())).thenReturn("log-invalid");

        scanner.scan();

        verify(taskDispatcher, times(1)).dispatch(eq(job), any(), eq(DefaultTaskDispatcher.TRIGGER_CRON));
    }

    @Test
    @DisplayName("未 Misfire 的任务仍按 CRON 类型派发（misfirePolicy=null）")
    void scan_notMisfire_dispatchesWithCronType() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        // next_fire_time 在 5 分钟前（未超过 30 分钟 misfireGrace）
        JobDO job = buildDueJob("normal-key", LocalDateTime.now().minusMinutes(5));
        job.setMisfirePolicy(null);
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(1);
        when(taskDispatcher.dispatch(any(), any(), anyString())).thenReturn("log-normal");

        scanner.scan();

        verify(taskDispatcher, times(1)).dispatch(eq(job), any(), eq(DefaultTaskDispatcher.TRIGGER_CRON));
    }

    @Test
    @DisplayName("P6-1: 派发任务时 MDC traceId 已设置")
    void scan_dispatch_setsTraceIdInMdc() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        JobDO job = buildDueJob("trace-key", LocalDateTime.now().minusMinutes(1));
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(1);
        // 在 dispatcher 调用时验证 MDC 中 traceId 已被设置
        when(taskDispatcher.dispatch(any(), any(), anyString())).thenAnswer(invocation -> {
            String traceId = MDC.get(TraceIdUtil.TRACE_ID_KEY);
            assertNotNull(traceId, "派发时 MDC traceId 不应为 null");
            assertFalse(traceId.isEmpty(), "派发时 MDC traceId 不应为空");
            return "log-trace";
        });

        scanner.scan();

        verify(taskDispatcher, times(1)).dispatch(eq(job), any(), eq(DefaultTaskDispatcher.TRIGGER_CRON));
    }

    @Test
    @DisplayName("P6-1: 派发完成后 MDC traceId 已清理")
    void scan_dispatchCompleted_clearsTraceIdFromMdc() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        JobDO job = buildDueJob("clear-key", LocalDateTime.now().minusMinutes(1));
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(1);
        when(taskDispatcher.dispatch(any(), any(), anyString())).thenReturn("log-clear");

        scanner.scan();

        // 派发完成后 MDC 应已清理
        assertNull(MDC.get(TraceIdUtil.TRACE_ID_KEY),
                "派发完成后 MDC traceId 应被清理");
    }

    @Test
    @DisplayName("P6-1: 多任务派发时每个任务有独立 traceId")
    void scan_multipleJobs_eachHasIndependentTraceId() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        JobDO job1 = buildDueJob("multi-key-1", LocalDateTime.now().minusMinutes(2));
        JobDO job2 = buildDueJob("multi-key-2", LocalDateTime.now().minusMinutes(1));
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job1, job2));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(1);

        // 收集每次 dispatch 调用时的 traceId
        java.util.List<String> traceIds = new java.util.ArrayList<>();
        when(taskDispatcher.dispatch(any(), any(), anyString())).thenAnswer(invocation -> {
            traceIds.add(MDC.get(TraceIdUtil.TRACE_ID_KEY));
            return "log-" + traceIds.size();
        });

        scanner.scan();

        assertEquals(2, traceIds.size());
        assertNotNull(traceIds.get(0), "第一个任务 traceId 不应为 null");
        assertNotNull(traceIds.get(1), "第二个任务 traceId 不应为 null");
        assertFalse(traceIds.get(0).equals(traceIds.get(1)),
                "两个任务的 traceId 应不同，避免串任务");
    }

    @Test
    @DisplayName("P6-1: dispatcher 抛异常后 MDC traceId 仍被清理")
    void scan_dispatcherThrows_stillClearsTraceId() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        JobDO job = buildDueJob("throw-trace-key", LocalDateTime.now().minusMinutes(1));
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(1);
        when(taskDispatcher.dispatch(any(), any(), anyString()))
                .thenThrow(new RuntimeException("dispatch err"));

        scanner.scan(); // 不应抛异常

        // 即使 dispatcher 抛异常，finally 块也应清理 MDC
        assertNull(MDC.get(TraceIdUtil.TRACE_ID_KEY),
                "dispatcher 抛异常后 MDC traceId 仍应被清理");
    }

    @Test
    @DisplayName("P6-1: CAS 失败跳过派发后 MDC traceId 仍被清理")
    void scan_casFails_stillClearsTraceId() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        JobDO job = buildDueJob("cas-trace-key", LocalDateTime.now().minusMinutes(1));
        when(jobMapper.selectDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.advanceNextFireTime(anyString(), any(), any(), any())).thenReturn(0);

        scanner.scan();

        // CAS 失败后也走了 finally，MDC 应已清理
        assertNull(MDC.get(TraceIdUtil.TRACE_ID_KEY),
                "CAS 失败后 MDC traceId 仍应被清理");
    }

    private JobDO buildDueJob(String key, LocalDateTime nextFireTime) {
        JobDO job = new JobDO();
        job.setId("job-" + key);
        job.setJobKey(key);
        job.setJobName("due 任务 " + key);
        job.setHandler("testHandler");
        job.setCronExpression("0 0 8 * * ?");
        job.setStatus("NORMAL");
        job.setNextFireTime(nextFireTime);
        return job;
    }
}
