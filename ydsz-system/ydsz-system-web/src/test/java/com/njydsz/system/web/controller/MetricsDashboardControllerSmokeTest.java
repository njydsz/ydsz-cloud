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
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.server.metrics.RedisMetricsService;

/**
 * {@link MetricsDashboardController} Smoke Test。
 *
 * <p>验证运维指标仪表盘端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class MetricsDashboardControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    DiscoveryClient discoveryClient = org.mockito.Mockito.mock(DiscoveryClient.class);
    when(discoveryClient.getServices()).thenReturn(List.of());
    Environment environment = org.mockito.Mockito.mock(Environment.class);
    RedisMetricsService redisMetricsService = org.mockito.Mockito.mock(RedisMetricsService.class);
    when(redisMetricsService.collectRedisMetrics()).thenReturn(java.util.Map.of());
    MetricsDashboardController controller =
        new MetricsDashboardController(discoveryClient, environment, redisMetricsService);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /system/metrics/dashboard")
  class Dashboard {

    @Test
    @DisplayName("should return 200 when dashboard query succeeds")
    void should_return_200_when_dashboard_query_succeeds() throws Exception {
      mockMvc.perform(get("/system/metrics/dashboard"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
