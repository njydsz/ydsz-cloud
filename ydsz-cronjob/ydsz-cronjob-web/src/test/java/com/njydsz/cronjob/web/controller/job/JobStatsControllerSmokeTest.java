package com.njydsz.cronjob.web.controller.job;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.domain.service.JobStatsQueryService;

/**
 * {@link JobStatsController} Smoke Test。
 *
 * <p>验证任务执行统计端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class JobStatsControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private JobStatsQueryService jobStatsQueryService;

  @org.mockito.InjectMocks
  private JobStatsController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /cronjob/stats/dashboard")
  class Dashboard {

    @Test
    @DisplayName("should return 200 when dashboard succeeds")
    void should_return_200_when_dashboard_succeeds() throws Exception {
      when(jobStatsQueryService.getDashboard()).thenReturn(Map.of());

      mockMvc.perform(get("/cronjob/stats/dashboard"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
