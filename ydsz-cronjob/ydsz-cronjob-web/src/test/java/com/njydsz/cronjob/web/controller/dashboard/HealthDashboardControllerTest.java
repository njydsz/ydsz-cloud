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

import com.njydsz.cronjob.domain.service.HealthDashboardQueryService;

/**
 * HealthDashboardController 集成测试。
 *
 * <p>验证系统健康仪表盘数据查询端点：聚合系统资源、任务概览、DAG 工作流、调度器状态等多维度健康指标。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ExtendWith(MockitoExtension.class)
class HealthDashboardControllerTest {

  private MockMvc mockMvc;

  @Mock
  private HealthDashboardQueryService healthDashboardQueryService;

  @InjectMocks
  private HealthDashboardController healthDashboardController;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(healthDashboardController).build();
  }

  @Nested
  @DisplayName("GET /cronjob/dashboard/health - getHealth")
  class GetHealth {

    @Test
    @DisplayName("返回完整健康数据（系统资源 + 任务 + DAG + 调度器）")
    void shouldReturnFullHealthData() throws Exception {
      Map<String, Object> healthData = Map.of(
          "systemResources", Map.of("cpuUsage", 0.45, "memoryUsage", 0.62),
          "jobOverview", Map.of("totalJobs", 50, "runningJobs", 3),
          "dagOverview", Map.of("runningInstances", 2, "successRate", 0.98),
          "schedulerStatus", Map.of("leader", "node-1", "scannerActive", true));

      when(healthDashboardQueryService.getHealth()).thenReturn(healthData);

      mockMvc.perform(get("/cronjob/dashboard/health"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.systemResources.cpuUsage").value(0.45))
          .andExpect(jsonPath("$.data.systemResources.memoryUsage").value(0.62))
          .andExpect(jsonPath("$.data.jobOverview.totalJobs").value(50))
          .andExpect(jsonPath("$.data.dagOverview.successRate").value(0.98))
          .andExpect(jsonPath("$.data.schedulerStatus.leader").value("node-1"))
          .andExpect(jsonPath("$.data.schedulerStatus.scannerActive").value(true));

      verify(healthDashboardQueryService, times(1)).getHealth();
    }

    @Test
    @DisplayName("Service 返回空数据时仍返回成功响应")
    void shouldReturnSuccessForEmptyHealthData() throws Exception {
      when(healthDashboardQueryService.getHealth()).thenReturn(Map.of());

      mockMvc.perform(get("/cronjob/dashboard/health"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(healthDashboardQueryService, times(1)).getHealth();
    }
  }
}
