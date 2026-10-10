package com.njydsz.literule.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.domain.vo.RuleDashboardOverviewVO;
import com.njydsz.literule.server.core.RuleMetrics;
import com.njydsz.literule.server.spi.DashboardDataProvider;

/**
 * {@link RuleDashboardController} Smoke Test。
 *
 * <p>验证规则引擎监控大盘端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuleDashboardControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private DashboardDataProvider dashboardService;

  @org.mockito.Mock
  private ObjectProvider<RuleMetrics> ruleMetricsProvider;

  @org.mockito.InjectMocks
  private RuleDashboardController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/dashboard/overview")
  class Overview {

    @Test
    @DisplayName("should return 200 when overview succeeds")
    void should_return_200_when_overview_succeeds() throws Exception {
      when(dashboardService.getOverview()).thenReturn(null);

      mockMvc.perform(get("/literule/dashboard/overview"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
