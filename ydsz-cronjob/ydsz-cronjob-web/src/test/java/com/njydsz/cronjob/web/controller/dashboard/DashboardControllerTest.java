package com.njydsz.cronjob.web.controller.dashboard;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.cronjob.domain.service.DashboardQueryService;

/**
 * DashboardController 集成测试。
 *
 * <p>验证运维 Dashboard 概览数据查询端点的路由与响应格式。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

  private MockMvc mockMvc;

  @Mock
  private DashboardQueryService dashboardQueryService;

  @InjectMocks
  private DashboardController dashboardController;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(dashboardController).build();
  }

  @Nested
  @DisplayName("GET /cronjob/dashboard/overview - getOverview")
  class GetOverview {

    @Test
    @DisplayName("返回聚合 Dashboard 数据（statusDistribution/groupStats 等）")
    void shouldReturnDashboardOverview() throws Exception {
      Map<String, Object> overviewData = Map.of(
          "statusDistribution", Map.of("NORMAL", 10, "PAUSED", 2),
          "groupStats", Map.of("ORDER-CENTER", 8, "FINANCE-DAILY", 4),
          "summary", Map.of("totalJobs", 12));

      when(dashboardQueryService.getOverview()).thenReturn(overviewData);

      mockMvc.perform(get("/cronjob/dashboard/overview"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.statusDistribution.NORMAL").value(10))
          .andExpect(jsonPath("$.data.statusDistribution.PAUSED").value(2))
          .andExpect(jsonPath("$.data.groupStats.ORDER-CENTER").value(8))
          .andExpect(jsonPath("$.data.summary.totalJobs").value(12));

      verify(dashboardQueryService, times(1)).getOverview();
    }

    @Test
    @DisplayName("Service 返回空 Map 时仍返回成功响应")
    void shouldReturnSuccessForEmptyData() throws Exception {
      when(dashboardQueryService.getOverview()).thenReturn(Map.of());

      mockMvc.perform(get("/cronjob/dashboard/overview"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(dashboardQueryService, times(1)).getOverview();
    }
  }
}
