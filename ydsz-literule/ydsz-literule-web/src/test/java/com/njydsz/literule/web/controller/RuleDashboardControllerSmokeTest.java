package com.njydsz.literule.web.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
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

  @Mock
  private DashboardDataProvider dashboardService;

  private RuleDashboardController controller;

  @BeforeEach
  void setUp() {
    // 构造器两个入参均为 ObjectProvider（泛型擦除后同类型），@InjectMocks 会把同一个 mock
    // 注入两位 → dashboardServiceProvider 解析不到 provider，端点走空数据分支，
    // 导致 dashboardService 的打桩从未被消费（UnnecessaryStubbing）。改为手工构造。
    ObjectProvider<DashboardDataProvider> dashboardServiceProvider = mock(ObjectProvider.class);
    when(dashboardServiceProvider.getIfAvailable()).thenReturn(dashboardService);
    ObjectProvider<RuleMetrics> ruleMetricsProvider = mock(ObjectProvider.class);
    controller = new RuleDashboardController(dashboardServiceProvider, ruleMetricsProvider);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/dashboard/overview")
  class Overview {

    @Test
    @DisplayName("should return 200 when overview succeeds")
    void should_return_200_when_overview_succeeds() throws Exception {
      when(dashboardService.getOverview())
          .thenReturn(RuleDashboardOverviewVO.builder().build());

      mockMvc.perform(get("/literule/dashboard/overview"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
