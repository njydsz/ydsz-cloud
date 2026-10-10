package com.njydsz.system.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.vo.DashboardWorkspaceVO;
import com.njydsz.system.server.service.DashboardService;

/**
 * {@link DashboardController} Smoke Test。
 *
 * <p>验证工作台聚合端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class DashboardControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private DashboardService dashboardService;

  @org.mockito.InjectMocks
  private DashboardController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(dashboardService.overview()).thenReturn(List.of());
    when(dashboardService.workspace()).thenReturn(new DashboardWorkspaceVO());
  }

  @Nested
  @DisplayName("GET /dashboard/overview")
  class Overview {

    @Test
    @DisplayName("should return 200 when overview query succeeds")
    void should_return_200_when_overview_query_succeeds() throws Exception {
      mockMvc.perform(get("/dashboard/overview"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /dashboard/workspace")
  class Workspace {

    @Test
    @DisplayName("should return 200 when workspace query succeeds")
    void should_return_200_when_workspace_query_succeeds() throws Exception {
      mockMvc.perform(get("/dashboard/workspace"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
