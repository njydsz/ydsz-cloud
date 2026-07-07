package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.entity.JobSlowLogDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.mapper.JobSlowLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * {@link SlowTaskDetector} 单元测试（P6-3）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>Leader 禁用 / 非 Leader 时跳过扫描</li>
 *   <li>无慢日志时仅查询不写入</li>
 *   <li>正常场景：批量查询 JobDO + 写入 slow_log</li>
 *   <li>幂等性：JobDO 不存在 / 阈值无效 / 已记录 → 跳过</li>
 *   <li>容错性：批量查询异常 / 单条插入异常不影响其他</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SlowTaskDetector 慢任务诊断测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlowTaskDetectorTest {

    @Mock
    private JobMapper jobMapper;
    @Mock
    private JobLogMapper jobLogMapper;
    @Mock
    private JobSlowLogMapper jobSlowLogMapper;
    @Mock
    private LeaderElector leaderElector;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private SlowTaskDetector detector;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties（@InjectMocks 不会自动创建配置对象）
        java.lang.reflect.Field f = SlowTaskDetector.class.getDeclaredField("cronjobProperties");
        f.setAccessible(true);
        f.set(detector, cronjobProperties);
        detector.init();
        lenient().when(leaderElector.isLeader(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("leader.enabled=false 时 scan 不执行")
    void scan_leaderDisabled_skip() {
        cronjobProperties.getLeader().setEnabled(false);

        detector.scan();

        verify(jobLogMapper, never()).selectSlowLogs(any(), anyInt());
    }

    @Test
    @DisplayName("非 Leader 时 scan 不执行")
    void scan_notLeader_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(false);

        detector.scan();

        verify(jobLogMapper, never()).selectSlowLogs(any(), anyInt());
    }

    @Test
    @DisplayName("无慢日志时仅查询不写入")
    void scan_noSlowLogs_noInsert() {
        cronjobProperties.getLeader().setEnabled(true);
        when(jobLogMapper.selectSlowLogs(any(), anyInt())).thenReturn(Collections.emptyList());

        detector.scan();

        verify(jobSlowLogMapper, never()).insert(any(JobSlowLogDO.class));
        verify(jobMapper, never()).selectBatchIds(any());
    }

    @Test
    @DisplayName("发现慢日志时批量查询 JobDO 并写入 slow_log")
    void scan_slowLogsFound_batchFetchAndInsert() {
        cronjobProperties.getLeader().setEnabled(true);
        JobLogDO log1 = buildSlowLog("log-1", "job-1", "key-1", 5000L);
        JobLogDO log2 = buildSlowLog("log-2", "job-2", "key-2", 8000L);
        when(jobLogMapper.selectSlowLogs(any(), anyInt())).thenReturn(List.of(log1, log2));

        JobDO job1 = buildJob("job-1", "key-1", 3000L, "tenant-1");
        JobDO job2 = buildJob("job-2", "key-2", 3000L, "tenant-2");
        when(jobMapper.selectBatchIds(any())).thenReturn(List.of(job1, job2));
        when(jobSlowLogMapper.countByLogId(anyString())).thenReturn(0);

        detector.scan();

        verify(jobMapper, times(1)).selectBatchIds(any());
        verify(jobSlowLogMapper, times(2)).insert(any(JobSlowLogDO.class));
    }

    @Test
    @DisplayName("JobDO 已被删除时跳过该条")
    void scan_jobDeleted_skipRecord() {
        cronjobProperties.getLeader().setEnabled(true);
        JobLogDO log1 = buildSlowLog("log-1", "job-missing", "key-1", 5000L);
        when(jobLogMapper.selectSlowLogs(any(), anyInt())).thenReturn(List.of(log1));
        when(jobMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

        detector.scan();

        verify(jobSlowLogMapper, never()).insert(any(JobSlowLogDO.class));
    }

    @Test
    @DisplayName("slowThresholdMs 已被清空时跳过")
    void scan_slowThresholdCleared_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        JobLogDO log1 = buildSlowLog("log-1", "job-1", "key-1", 5000L);
        when(jobLogMapper.selectSlowLogs(any(), anyInt())).thenReturn(List.of(log1));
        JobDO job1 = buildJob("job-1", "key-1", null, "tenant-1");
        when(jobMapper.selectBatchIds(any())).thenReturn(List.of(job1));

        detector.scan();

        verify(jobSlowLogMapper, never()).insert(any(JobSlowLogDO.class));
    }

    @Test
    @DisplayName("slowThresholdMs <= 0 时跳过")
    void scan_slowThresholdNonPositive_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        JobLogDO log1 = buildSlowLog("log-1", "job-1", "key-1", 5000L);
        when(jobLogMapper.selectSlowLogs(any(), anyInt())).thenReturn(List.of(log1));
        JobDO job1 = buildJob("job-1", "key-1", 0L, "tenant-1");
        when(jobMapper.selectBatchIds(any())).thenReturn(List.of(job1));

        detector.scan();

        verify(jobSlowLogMapper, never()).insert(any(JobSlowLogDO.class));
    }

    @Test
    @DisplayName("已记录过（countByLogId > 0）时跳过（幂等）")
    void scan_alreadyRecorded_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        JobLogDO log1 = buildSlowLog("log-1", "job-1", "key-1", 5000L);
        when(jobLogMapper.selectSlowLogs(any(), anyInt())).thenReturn(List.of(log1));
        JobDO job1 = buildJob("job-1", "key-1", 3000L, "tenant-1");
        when(jobMapper.selectBatchIds(any())).thenReturn(List.of(job1));
        when(jobSlowLogMapper.countByLogId("log-1")).thenReturn(1);

        detector.scan();

        verify(jobSlowLogMapper, never()).insert(any(JobSlowLogDO.class));
    }

    @Test
    @DisplayName("批量查询 JobDO 异常时跳过本批所有")
    void scan_batchFetchException_skipAll() {
        cronjobProperties.getLeader().setEnabled(true);
        JobLogDO log1 = buildSlowLog("log-1", "job-1", "key-1", 5000L);
        when(jobLogMapper.selectSlowLogs(any(), anyInt())).thenReturn(List.of(log1));
        when(jobMapper.selectBatchIds(any())).thenThrow(new RuntimeException("DB conn err"));

        detector.scan(); // 不应抛异常

        verify(jobSlowLogMapper, never()).insert(any(JobSlowLogDO.class));
    }

    @Test
    @DisplayName("单条插入异常时其他条继续处理")
    void scan_singleInsertException_continuesOthers() {
        cronjobProperties.getLeader().setEnabled(true);
        JobLogDO log1 = buildSlowLog("log-1", "job-1", "key-1", 5000L);
        JobLogDO log2 = buildSlowLog("log-2", "job-2", "key-2", 8000L);
        when(jobLogMapper.selectSlowLogs(any(), anyInt())).thenReturn(List.of(log1, log2));
        JobDO job1 = buildJob("job-1", "key-1", 3000L, "tenant-1");
        JobDO job2 = buildJob("job-2", "key-2", 3000L, "tenant-2");
        when(jobMapper.selectBatchIds(any())).thenReturn(List.of(job1, job2));
        when(jobSlowLogMapper.countByLogId(anyString())).thenReturn(0);
        // 第一条插入抛异常，第二条应继续
        when(jobSlowLogMapper.insert(any(JobSlowLogDO.class)))
                .thenThrow(new RuntimeException("insert log-1 err"))
                .thenReturn(1);

        detector.scan(); // 不应抛异常

        verify(jobSlowLogMapper, times(2)).insert(any(JobSlowLogDO.class));
    }

    @Test
    @DisplayName("多个慢日志时逐个处理并全部写入")
    void scan_multipleSlowLogs_processesEach() {
        cronjobProperties.getLeader().setEnabled(true);
        JobLogDO log1 = buildSlowLog("log-a", "job-a", "key-a", 10000L);
        JobLogDO log2 = buildSlowLog("log-b", "job-b", "key-b", 12000L);
        JobLogDO log3 = buildSlowLog("log-c", "job-c", "key-c", 15000L);
        when(jobLogMapper.selectSlowLogs(any(), anyInt())).thenReturn(List.of(log1, log2, log3));
        JobDO jobA = buildJob("job-a", "key-a", 5000L, "tenant-a");
        JobDO jobB = buildJob("job-b", "key-b", 5000L, "tenant-b");
        JobDO jobC = buildJob("job-c", "key-c", 5000L, "tenant-c");
        when(jobMapper.selectBatchIds(any())).thenReturn(List.of(jobA, jobB, jobC));
        when(jobSlowLogMapper.countByLogId(anyString())).thenReturn(0);

        detector.scan();

        verify(jobSlowLogMapper, times(3)).insert(any(JobSlowLogDO.class));
    }

    @Test
    @DisplayName("doScan 异常时被外层 try-catch 捕获不影响下次")
    void scan_doScanException_swallowed() {
        cronjobProperties.getLeader().setEnabled(true);
        when(jobLogMapper.selectSlowLogs(any(), anyInt()))
                .thenThrow(new RuntimeException("scan err"));

        detector.scan(); // 不应抛异常

        verify(jobSlowLogMapper, never()).insert(any(JobSlowLogDO.class));
    }

    @Test
    @DisplayName("recordSlowLog: 正常写入返回 true")
    void recordSlowLog_normal_returnsTrue() {
        JobLogDO log0 = buildSlowLog("log-x", "job-x", "key-x", 6000L);
        JobDO job = buildJob("job-x", "key-x", 3000L, "tenant-x");
        when(jobSlowLogMapper.countByLogId("log-x")).thenReturn(0);

        boolean result = detector.recordSlowLog(log0, java.util.Map.of("job-x", job));

        assertTrue(result);
        verify(jobSlowLogMapper, times(1)).insert(any(JobSlowLogDO.class));
    }

    @Test
    @DisplayName("recordSlowLog: JobDO 不存在返回 false")
    void recordSlowLog_jobMissing_returnsFalse() {
        JobLogDO log0 = buildSlowLog("log-x", "job-x", "key-x", 6000L);

        boolean result = detector.recordSlowLog(log0, java.util.Collections.emptyMap());

        assertFalse(result);
        verify(jobSlowLogMapper, never()).insert(any(JobSlowLogDO.class));
    }

    @Test
    @DisplayName("recordSlowLog: 阈值已清空返回 false")
    void recordSlowLog_thresholdCleared_returnsFalse() {
        JobLogDO log0 = buildSlowLog("log-x", "job-x", "key-x", 6000L);
        JobDO job = buildJob("job-x", "key-x", null, "tenant-x");

        boolean result = detector.recordSlowLog(log0, java.util.Map.of("job-x", job));

        assertFalse(result);
        verify(jobSlowLogMapper, never()).insert(any(JobSlowLogDO.class));
    }

    @Test
    @DisplayName("recordSlowLog: 已记录返回 false（幂等）")
    void recordSlowLog_alreadyRecorded_returnsFalse() {
        JobLogDO log0 = buildSlowLog("log-x", "job-x", "key-x", 6000L);
        JobDO job = buildJob("job-x", "key-x", 3000L, "tenant-x");
        when(jobSlowLogMapper.countByLogId("log-x")).thenReturn(1);

        boolean result = detector.recordSlowLog(log0, java.util.Map.of("job-x", job));

        assertFalse(result);
        verify(jobSlowLogMapper, never()).insert(any(JobSlowLogDO.class));
    }

    @Test
    @DisplayName("recordSlowLog: 写入的 slow_log 字段正确填充")
    void recordSlowLog_fieldsPopulated() {
        JobLogDO log0 = buildSlowLog("log-x", "job-x", "key-x", 6000L);
        JobDO job = buildJob("job-x", "key-x", 3000L, "tenant-x");
        when(jobSlowLogMapper.countByLogId("log-x")).thenReturn(0);

        detector.recordSlowLog(log0, java.util.Map.of("job-x", job));

        org.mockito.ArgumentCaptor<JobSlowLogDO> captor =
                org.mockito.ArgumentCaptor.forClass(JobSlowLogDO.class);
        verify(jobSlowLogMapper).insert(captor.capture());
        JobSlowLogDO slow = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("job-x", slow.getJobId());
        org.junit.jupiter.api.Assertions.assertEquals("key-x", slow.getJobKey());
        org.junit.jupiter.api.Assertions.assertEquals("log-x", slow.getLogId());
        org.junit.jupiter.api.Assertions.assertEquals(6000L, slow.getDurationMs());
        org.junit.jupiter.api.Assertions.assertEquals(3000L, slow.getSlowThresholdMs());
        org.junit.jupiter.api.Assertions.assertEquals("params-json", slow.getParamsJson());
        org.junit.jupiter.api.Assertions.assertEquals("some-error", slow.getErrorMessage());
        org.junit.jupiter.api.Assertions.assertEquals("trace-x", slow.getTraceId());
        org.junit.jupiter.api.Assertions.assertEquals("tenant-x", slow.getTenantId());
    }

    // -------- 辅助方法 --------

    private JobLogDO buildSlowLog(String id, String jobId, String jobKey, long durationMs) {
        JobLogDO log0 = new JobLogDO();
        log0.setId(id);
        log0.setJobId(jobId);
        log0.setJobKey(jobKey);
        log0.setDurationMs(durationMs);
        log0.setParamsJson("params-json");
        log0.setErrorMessage("some-error");
        log0.setTraceId("trace-x");
        log0.setStatus("SUCCESS");
        log0.setCreatedAt(LocalDateTime.now());
        return log0;
    }

    private JobDO buildJob(String id, String jobKey, Long slowThresholdMs, String tenantId) {
        JobDO job = new JobDO();
        job.setId(id);
        job.setJobKey(jobKey);
        job.setSlowThresholdMs(slowThresholdMs);
        job.setTenantId(tenantId);
        return job;
    }
}
