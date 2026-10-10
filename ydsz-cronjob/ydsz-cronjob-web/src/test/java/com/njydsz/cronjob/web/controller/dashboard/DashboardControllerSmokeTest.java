package com.njydsz.cronjob.web.controller.dashboard;

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
import com.njydsz.cronjob.domain.service.DashboardQueryService;

/**
 * {@link DashboardController} Smoke Test。
 *
 * <p>验证 Dashboard 数据端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class DashboardControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private DashboardQueryService dashboardQueryService;

  @org.mockito.InjectMocks
  private DashboardController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /cronjob/dashboard/overview")
  class Overview {

    @Test
    @DisplayName("should return 200 when overview succeeds")
    void should_return_200_when_overview_succeeds() throws Exception {
      when(dashboardQueryService.getOverview()).thenReturn(Map.of());

      mockMvc.perform(get("/cronjob/dashboard/overview"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
