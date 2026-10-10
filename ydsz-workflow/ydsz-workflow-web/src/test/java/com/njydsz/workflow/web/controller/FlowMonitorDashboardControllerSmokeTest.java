package com.njydsz.workflow.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.server.service.FlowEfficiencyService;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowTaskService;

/**
 * {@link FlowMonitorDashboardController} Smoke Test。
 *
 * <p>验证监控看板核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowMonitorDashboardControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FlowEfficiencyService efficiencyService;

  @org.mockito.Mock
  private FlowTaskService taskService;

  @org.mockito.Mock
  private FlowInstanceService instanceService;

  @org.mockito.InjectMocks
  private FlowMonitorDashboardController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /workflow/engine/monitor/overview")
  class MonitorOverview {

    @Test
    @DisplayName("should return 200 when monitor overview query succeeds")
    void should_return_200_when_monitor_overview_succeeds() throws Exception {
      when(instanceService.selectCountGroupByStatus(anyString())).thenReturn(List.of());
      when(instanceService.selectTodayCount(anyString())).thenReturn(Map.of());
      when(taskService.countPending(anyString())).thenReturn(0L);
      when(taskService.countOverdue(any(), anyString())).thenReturn(0L);

      mockMvc.perform(get("/workflow/engine/monitor/overview"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/monitor/instanceTrend")
  class MonitorInstanceTrend {

    @Test
    @DisplayName("should return 200 when instance trend query succeeds")
    void should_return_200_when_instance_trend_succeeds() throws Exception {
      when(instanceService.selectDailyNewCount(anyString(), any(), any())).thenReturn(List.of());
      when(instanceService.selectDailyCompletedCount(anyString(), any(), any())).thenReturn(List.of());

      mockMvc.perform(get("/workflow/engine/monitor/instanceTrend")
              .param("days", "7"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
