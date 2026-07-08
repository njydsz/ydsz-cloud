package com.njydsz.pmis.cronjob.core.cleaner;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.mapper.JobAlertLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobHistoryMapper;
import com.njydsz.pmis.cronjob.mapper.JobLogContentMapper;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobSlowLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobTaskMapper;
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
 * {@link LogCleaner} 单元测试（P2-2 日志归档清理）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>Leader 禁用时跳过清理</li>
 *   <li>非 Leader 时跳过清理</li>
 *   <li>正常清理：所有表都有过期数据，循环批量删除</li>
 *   <li>多批次：第一批满 batchSize，第二批不足，验证循环逻辑</li>
 *   <li>无过期数据：所有表返回 0，不记录清理日志</li>
 *   <li>单表异常不影响其他表</li>
 *   <li>自定义保留天数和批量大小</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("LogCleaner 日志归档清理测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogCleanerTest {

    @Mock
    private JobLogMapper jobLogMapper;
    @Mock
    private JobLogContentMapper jobLogContentMapper;
    @Mock
    private JobSlowLogMapper jobSlowLogMapper;
    @Mock
    private JobAlertLogMapper jobAlertLogMapper;
    @Mock
    private JobTaskMapper jobTaskMapper;
    @Mock
    private JobHistoryMapper jobHistoryMapper;
    @Mock
    private LeaderElector leaderElector;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private LogCleaner logCleaner;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties（@InjectMocks 不会自动创建配置对象）
        java.lang.reflect.Field f = LogCleaner.class.getDeclaredField("cronjobProperties");
        f.setAccessible(true);
        f.set(logCleaner, cronjobProperties);
        logCleaner.init();
        // 默认为 Leader，便于多数测试用例直接使用
        lenient().when(leaderElector.isLeader(anyString())).thenReturn(true);
        // 默认每张表无过期数据
        lenient().when(jobLogMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(0);
        lenient().when(jobLogContentMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(0);
        lenient().when(jobSlowLogMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(0);
        lenient().when(jobAlertLogMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(0);
        lenient().when(jobTaskMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(0);
        lenient().when(jobHistoryMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(0);
    }

    // ==================== 清理入口测试 ====================

    @Test
    @DisplayName("clean: Leader 禁用时跳过清理")
    void clean_leaderDisabled_skip() {
        cronjobProperties.getLeader().setEnabled(false);

        logCleaner.clean();

        verify(jobLogMapper, never()).cleanExpiredLogs(any(), anyInt());
        verify(jobLogContentMapper, never()).cleanExpiredLogs(any(), anyInt());
        verify(jobSlowLogMapper, never()).cleanExpiredLogs(any(), anyInt());
        verify(jobAlertLogMapper, never()).cleanExpiredLogs(any(), anyInt());
        verify(jobTaskMapper, never()).cleanExpiredLogs(any(), anyInt());
        verify(jobHistoryMapper, never()).cleanExpiredLogs(any(), anyInt());
    }

    @Test
    @DisplayName("clean: 非 Leader 时跳过清理")
    void clean_notLeader_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(false);

        logCleaner.clean();

        verify(jobLogMapper, never()).cleanExpiredLogs(any(), anyInt());
        verify(jobLogContentMapper, never()).cleanExpiredLogs(any(), anyInt());
        verify(jobSlowLogMapper, never()).cleanExpiredLogs(any(), anyInt());
        verify(jobAlertLogMapper, never()).cleanExpiredLogs(any(), anyInt());
        verify(jobTaskMapper, never()).cleanExpiredLogs(any(), anyInt());
        verify(jobHistoryMapper, never()).cleanExpiredLogs(any(), anyInt());
    }

    // ==================== 正常清理测试 ====================

    @Test
    @DisplayName("clean: 正常清理所有表（每表单批删除）")
    void clean_normal_singleBatch() {
        cronjobProperties.getLeader().setEnabled(true);
        // 每张表删除 500 条（< batchSize=1000，单批完成）
        when(jobLogMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(500);
        when(jobLogContentMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(300);
        when(jobSlowLogMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(200);
        when(jobAlertLogMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(100);
        when(jobTaskMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(50);
        when(jobHistoryMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(150);

        logCleaner.clean();

        // 验证每张表只调用一次（单批完成）
        verify(jobLogMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobLogContentMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobSlowLogMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobAlertLogMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobTaskMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobHistoryMapper, times(1)).cleanExpiredLogs(any(), anyInt());
    }

    @Test
    @DisplayName("clean: 多批次循环删除（第一批满 batchSize，第二批不足）")
    void clean_multipleBatches_loopUntilDone() {
        cronjobProperties.getLeader().setEnabled(true);
        cronjobProperties.getLogRetention().setBatchSize(1000);
        // pmis_job_log: 第一批删 1000（满），第二批删 500（不足，停止）
        when(jobLogMapper.cleanExpiredLogs(any(), anyInt()))
                .thenReturn(1000)
                .thenReturn(500);
        // 其他表无数据
        when(jobLogContentMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(0);
        when(jobSlowLogMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(0);
        when(jobAlertLogMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(0);
        when(jobTaskMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(0);
        when(jobHistoryMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(0);

        logCleaner.clean();

        // 验证 pmis_job_log 被调用 2 次（2 批）
        verify(jobLogMapper, times(2)).cleanExpiredLogs(any(), anyInt());
    }

    @Test
    @DisplayName("clean: 无过期数据时各表只调用一次")
    void clean_noExpiredData_singleCall() {
        cronjobProperties.getLeader().setEnabled(true);
        // 所有表返回 0（默认 mock）

        logCleaner.clean();

        // 验证每张表只调用一次（第一批返回 0，立即停止）
        verify(jobLogMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobLogContentMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobSlowLogMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobAlertLogMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobTaskMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobHistoryMapper, times(1)).cleanExpiredLogs(any(), anyInt());
    }

    // ==================== 容错隔离测试 ====================

    @Test
    @DisplayName("clean: 单表异常不影响其他表清理")
    void clean_singleTableException_continuesOthers() {
        cronjobProperties.getLeader().setEnabled(true);
        // pmis_job_log 抛异常
        when(jobLogMapper.cleanExpiredLogs(any(), anyInt()))
                .thenThrow(new RuntimeException("DB connection error"));
        // 其他表正常返回
        when(jobLogContentMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(100);
        when(jobSlowLogMapper.cleanExpiredLogs(any(), anyInt())).thenReturn(50);

        logCleaner.clean(); // 不应抛异常

        // 验证即使 pmis_job_log 异常，其他表仍被清理
        verify(jobLogContentMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobSlowLogMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobAlertLogMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobTaskMapper, times(1)).cleanExpiredLogs(any(), anyInt());
        verify(jobHistoryMapper, times(1)).cleanExpiredLogs(any(), anyInt());
    }

    // ==================== 配置测试 ====================

    @Test
    @DisplayName("clean: 自定义保留天数和批量大小")
    void clean_customConfig_applied() {
        cronjobProperties.getLeader().setEnabled(true);
        cronjobProperties.getLogRetention().setRetentionDays(7);
        cronjobProperties.getLogRetention().setBatchSize(500);

        LocalDateTime beforeClean = LocalDateTime.now();
        logCleaner.clean();

        // 验证 batchSize 传入正确
        verify(jobLogMapper, times(1)).cleanExpiredLogs(any(), eq(500));
        verify(jobLogContentMapper, times(1)).cleanExpiredLogs(any(), eq(500));
        verify(jobSlowLogMapper, times(1)).cleanExpiredLogs(any(), eq(500));
        verify(jobAlertLogMapper, times(1)).cleanExpiredLogs(any(), eq(500));
        verify(jobTaskMapper, times(1)).cleanExpiredLogs(any(), eq(500));
        verify(jobHistoryMapper, times(1)).cleanExpiredLogs(any(), eq(500));

        // 验证 before 时间在 [now-7d-1s, now-7d+1s] 范围内
        LocalDateTime expectedBefore = beforeClean.minusDays(7);
        org.mockito.ArgumentCaptor<LocalDateTime> captor =
                org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jobLogMapper).cleanExpiredLogs(captor.capture(), anyInt());
        LocalDateTime actualBefore = captor.getValue();
        // 允许 2 秒的误差（测试执行时间）
        org.junit.jupiter.api.Assertions.assertTrue(
                actualBefore.isAfter(expectedBefore.minusSeconds(2)),
                "before 应约为 now - 7d: actual=" + actualBefore + " expected~" + expectedBefore);
        org.junit.jupiter.api.Assertions.assertTrue(
                actualBefore.isBefore(expectedBefore.plusSeconds(2)),
                "before 应约为 now - 7d: actual=" + actualBefore + " expected~" + expectedBefore);
    }

    @Test
    @DisplayName("clean: 默认配置（保留 30 天，批量 1000）")
    void clean_defaultConfig_applied() {
        cronjobProperties.getLeader().setEnabled(true);
        // 不修改配置，使用默认值 retentionDays=30, batchSize=1000

        logCleaner.clean();

        verify(jobLogMapper, times(1)).cleanExpiredLogs(any(), eq(1000));
    }
}
