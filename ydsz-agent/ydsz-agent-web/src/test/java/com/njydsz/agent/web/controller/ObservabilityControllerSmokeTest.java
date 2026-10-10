package com.njydsz.agent.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.agent.server.observability.ObservabilityDashboardService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link ObservabilityController} Smoke Test.
 *
 * <p>Verify observability dashboard endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class ObservabilityControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private ObservabilityDashboardService dashboardService;

  @org.mockito.InjectMocks
  private ObservabilityController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/observability/overview")
  class GetOverview {

    @Test
    @DisplayName("should return 200 when get overview succeeds")
    void should_return_200_when_get_overview_succeeds() throws Exception {
      when(dashboardService.getOverview()).thenReturn(null);

      mockMvc.perform(get("/agent/observability/overview"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
