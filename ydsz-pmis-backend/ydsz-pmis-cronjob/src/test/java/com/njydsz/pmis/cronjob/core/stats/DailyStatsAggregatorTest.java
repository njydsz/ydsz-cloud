package com.njydsz.pmis.cronjob.core.stats;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.entity.JobDailyStatsDO;
import com.njydsz.pmis.cronjob.mapper.JobDailyStatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DailyStatsAggregator} 单元测试（P2-3 执行历史趋势可视化）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>aggregate: Leader 禁用 / 非 Leader 时跳过</li>
 *   <li>aggregateForDate: 无执行记录时跳过</li>
 *   <li>aggregateForDate: 正常聚合写入</li>
 *   <li>aggregateForDate: 单任务异常不影响其他任务</li>
 *   <li>aggregateForDate: 聚合查询异常被捕获</li>
 *   <li>字段映射正确性（含 null 耗时场景）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DailyStatsAggregator 每日统计聚合测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DailyStatsAggregatorTest {

    @Mock
    private JobDailyStatsMapper jobDailyStatsMapper;
    @Mock
    private LeaderElector leaderElector;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private DailyStatsAggregator aggregator;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties（@InjectMocks 不会自动创建配置对象）
        java.lang.reflect.Field f = DailyStatsAggregator.class.getDeclaredField("cronjobProperties");
        f.setAccessible(true);
        f.set(aggregator, cronjobProperties);
        aggregator.init();
        // 默认为 Leader
        lenient().when(leaderElector.isLeader(anyString())).thenReturn(true);
    }

    // ==================== aggregate 入口测试 ====================

    @Test
    @DisplayName("aggregate: leader.enabled=false 时跳过")
    void aggregate_leaderDisabled_skip() {
        cronjobProperties.getLeader().setEnabled(false);

        aggregator.aggregate();

        verify(jobDailyStatsMapper, never()).aggregateDaily(any(), any());
    }

    @Test
    @DisplayName("aggregate: 非 Leader 时跳过")
    void aggregate_notLeader_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(false);

        aggregator.aggregate();

        verify(jobDailyStatsMapper, never()).aggregateDaily(any(), any());
    }

    @Test
    @DisplayName("aggregate: 是 Leader 时执行聚合")
    void aggregate_isLeader_executes() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        when(jobDailyStatsMapper.aggregateDaily(any(), any()))
                .thenReturn(Collections.emptyList());

        aggregator.aggregate();

        verify(jobDailyStatsMapper, times(1)).aggregateDaily(any(), any());
    }

    // ==================== aggregateForDate 测试 ====================

    @Test
    @DisplayName("aggregateForDate: 无执行记录时跳过 upsert")
    void aggregateForDate_noRecords_skip() {
        LocalDate statsDate = LocalDate.of(2026, 7, 7);
        when(jobDailyStatsMapper.aggregateDaily(any(), any()))
                .thenReturn(Collections.emptyList());

        aggregator.aggregateForDate(statsDate);

        verify(jobDailyStatsMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("aggregateForDate: 聚合查询返回 null 时跳过")
    void aggregateForDate_nullRecords_skip() {
        LocalDate statsDate = LocalDate.of(2026, 7, 7);
        when(jobDailyStatsMapper.aggregateDaily(any(), any()))
                .thenReturn(null);

        aggregator.aggregateForDate(statsDate);

        verify(jobDailyStatsMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("aggregateForDate: 正常聚合写入单任务统计")
    void aggregateForDate_normal_writesStats() {
        LocalDate statsDate = LocalDate.of(2026, 7, 7);
        Map<String, Object> row = buildAggRow("job-1", "key-1", 10L, 8L, 1L, 1L,
                500L, 1000L, 100L, 800L);
        when(jobDailyStatsMapper.aggregateDaily(any(), any()))
                .thenReturn(List.of(row));

        aggregator.aggregateForDate(statsDate);

        ArgumentCaptor<JobDailyStatsDO> captor = ArgumentCaptor.forClass(JobDailyStatsDO.class);
        verify(jobDailyStatsMapper, times(1)).upsert(captor.capture());
        JobDailyStatsDO stats = captor.getValue();
        assertEquals("job-1", stats.getJobId());
        assertEquals("key-1", stats.getJobKey());
        assertEquals(statsDate, stats.getStatsDate());
        assertEquals(10L, stats.getFireCount());
        assertEquals(8L, stats.getSuccessCount());
        assertEquals(1L, stats.getFailCount());
        assertEquals(1L, stats.getTimeoutCount());
        assertEquals(500L, stats.getAvgDurationMs());
        assertEquals(1000L, stats.getMaxDurationMs());
        assertEquals(100L, stats.getMinDurationMs());
        assertEquals(800L, stats.getP95DurationMs());
        assertNotNull(stats.getId());
        assertNotNull(stats.getCreatedAt());
        assertEquals(0, stats.getDeleted());
    }

    @Test
    @DisplayName("aggregateForDate: 多任务聚合写入")
    void aggregateForDate_multipleJobs_writesAll() {
        LocalDate statsDate = LocalDate.of(2026, 7, 7);
        Map<String, Object> row1 = buildAggRow("job-1", "key-1", 10L, 8L, 1L, 1L,
                500L, 1000L, 100L, 800L);
        Map<String, Object> row2 = buildAggRow("job-2", "key-2", 5L, 5L, 0L, 0L,
                200L, 300L, 100L, 250L);
        when(jobDailyStatsMapper.aggregateDaily(any(), any()))
                .thenReturn(List.of(row1, row2));

        aggregator.aggregateForDate(statsDate);

        verify(jobDailyStatsMapper, times(2)).upsert(any());
    }

    @Test
    @DisplayName("aggregateForDate: 单任务 upsert 异常不影响其他任务")
    void aggregateForDate_singleJobException_continuesOthers() {
        LocalDate statsDate = LocalDate.of(2026, 7, 7);
        Map<String, Object> row1 = buildAggRow("job-1", "key-1", 10L, 8L, 1L, 1L,
                500L, 1000L, 100L, 800L);
        Map<String, Object> row2 = buildAggRow("job-2", "key-2", 5L, 5L, 0L, 0L,
                200L, 300L, 100L, 250L);
        when(jobDailyStatsMapper.aggregateDaily(any(), any()))
                .thenReturn(List.of(row1, row2));
        // job-1 upsert 抛异常，job-2 正常
        when(jobDailyStatsMapper.upsert(any()))
                .thenThrow(new RuntimeException("DB err"))
                .thenReturn(1);

        aggregator.aggregateForDate(statsDate); // 不应抛异常

        verify(jobDailyStatsMapper, times(2)).upsert(any());
    }

    @Test
    @DisplayName("aggregateForDate: 聚合查询异常被捕获，不抛出")
    void aggregateForDate_queryException_swallowed() {
        LocalDate statsDate = LocalDate.of(2026, 7, 7);
        when(jobDailyStatsMapper.aggregateDaily(any(), any()))
                .thenThrow(new RuntimeException("DB err"));

        aggregator.aggregateForDate(statsDate); // 不应抛异常

        verify(jobDailyStatsMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("aggregateForDate: 耗时为 null 时保留 null 语义")
    void aggregateForDate_nullDuration_preservedAsNull() {
        LocalDate statsDate = LocalDate.of(2026, 7, 7);
        // 所有耗时字段为 null（无 SUCCESS 记录时 PERCENTILE_CONT 可能返回 null）
        Map<String, Object> row = buildAggRow("job-1", "key-1", 2L, 0L, 2L, 0L,
                null, null, null, null);
        when(jobDailyStatsMapper.aggregateDaily(any(), any()))
                .thenReturn(List.of(row));

        aggregator.aggregateForDate(statsDate);

        ArgumentCaptor<JobDailyStatsDO> captor = ArgumentCaptor.forClass(JobDailyStatsDO.class);
        verify(jobDailyStatsMapper, times(1)).upsert(captor.capture());
        JobDailyStatsDO stats = captor.getValue();
        assertEquals(2L, stats.getFireCount());
        assertEquals(0L, stats.getSuccessCount());
        assertEquals(2L, stats.getFailCount());
        assertNull(stats.getAvgDurationMs());
        assertNull(stats.getMaxDurationMs());
        assertNull(stats.getMinDurationMs());
        assertNull(stats.getP95DurationMs());
    }

    @Test
    @DisplayName("aggregateForDate: 正确传递时间窗口 [start, end)")
    void aggregateForDate_correctTimeWindow() {
        LocalDate statsDate = LocalDate.of(2026, 7, 7);
        when(jobDailyStatsMapper.aggregateDaily(any(), any()))
                .thenReturn(Collections.emptyList());

        aggregator.aggregateForDate(statsDate);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jobDailyStatsMapper).aggregateDaily(startCaptor.capture(), endCaptor.capture());
        assertEquals(statsDate.atStartOfDay(), startCaptor.getValue());
        assertEquals(statsDate.plusDays(1).atStartOfDay(), endCaptor.getValue());
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造聚合查询返回的 Map 行。
     */
    private Map<String, Object> buildAggRow(String jobId, String jobKey,
                                             Long fireCount, Long successCount,
                                             Long failCount, Long timeoutCount,
                                             Long avgMs, Long maxMs, Long minMs, Long p95Ms) {
        Map<String, Object> row = new HashMap<>();
        row.put("job_id", jobId);
        row.put("job_key", jobKey);
        row.put("fire_count", fireCount);
        row.put("success_count", successCount);
        row.put("fail_count", failCount);
        row.put("timeout_count", timeoutCount);
        row.put("avg_duration_ms", avgMs);
        row.put("max_duration_ms", maxMs);
        row.put("min_duration_ms", minMs);
        row.put("p95_duration_ms", p95Ms);
        return row;
    }
}
