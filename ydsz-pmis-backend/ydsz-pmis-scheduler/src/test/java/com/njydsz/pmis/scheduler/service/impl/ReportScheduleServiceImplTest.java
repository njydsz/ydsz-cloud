package com.njydsz.pmis.scheduler.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReportScheduleServiceImpl 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ReportScheduleServiceImpl 报表调度服务测试")
@ExtendWith(MockitoExtension.class)
class ReportScheduleServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ReportScheduleServiceImpl service;

    @Test
    @DisplayName("executeDailyReports 应处理所有日报订阅")
    void executeDailyReports_shouldProcessSubscriptions() {
        when(jdbcTemplate.queryForList(anyString(), eq("DAILY")))
                .thenReturn(List.of(
                        Map.of("id", 1, "report_type", "COCKPIT",
                                "recipients", "admin@test.com", "channels", "EMAIL"),
                        Map.of("id", 2, "report_type", "EVM",
                                "recipients", "pm@test.com", "channels", "EMAIL")
                ));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any()))
                .thenReturn(1);

        service.executeDailyReports();

        verify(jdbcTemplate, times(2)).update(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("executeDailyReports 在无订阅时不应调用 update")
    void executeDailyReports_shouldHandleEmptySubscriptions() {
        when(jdbcTemplate.queryForList(anyString(), eq("DAILY")))
                .thenReturn(List.of());

        service.executeDailyReports();

        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("executeDailyReports 在报表生成失败时不应抛出异常")
    void executeDailyReports_shouldNotThrowWhenReportGenerationFails() {
        when(jdbcTemplate.queryForList(anyString(), eq("DAILY")))
                .thenReturn(List.of(Map.of("id", 1, "report_type", "COCKPIT")));

        service.executeDailyReports();
    }

    @Test
    @DisplayName("executeWeeklyReports 应查询 WEEKLY 频率订阅")
    void executeWeeklyReports_shouldCallWeeklySubscriptions() {
        when(jdbcTemplate.queryForList(anyString(), eq("WEEKLY")))
                .thenReturn(List.of());

        service.executeWeeklyReports();

        verify(jdbcTemplate).queryForList(anyString(), eq("WEEKLY"));
    }

    @Test
    @DisplayName("executeMonthlyReports 应查询 MONTHLY 频率订阅")
    void executeMonthlyReports_shouldCallMonthlySubscriptions() {
        when(jdbcTemplate.queryForList(anyString(), eq("MONTHLY")))
                .thenReturn(List.of());

        service.executeMonthlyReports();

        verify(jdbcTemplate).queryForList(anyString(), eq("MONTHLY"));
    }

    @Test
    @DisplayName("generateReport 应返回包含报表类型的 fileKey")
    void generateReport_shouldReturnFileKey() {
        String fileKey = service.generateReport("COCKPIT", Map.of());
        assertThat(fileKey).contains("COCKPIT");
        assertThat(fileKey).endsWith(".xlsx");
    }
}
