package com.njydsz.agent.web.controller;

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

import com.njydsz.agent.server.runtime.RuntimeManagementService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link RuntimeController} Smoke Test.
 *
 * <p>Verify runtime management endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuntimeControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RuntimeManagementService runtimeManagementService;

  @org.mockito.InjectMocks
  private RuntimeController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/runtime/sessions/active")
  class GetActiveSessions {

    @Test
    @DisplayName("should return 200 when get active sessions succeeds")
    void should_return_200_when_get_active_sessions_succeeds() throws Exception {
      when(runtimeManagementService.getActiveSessions()).thenReturn(List.of());

      mockMvc.perform(get("/agent/runtime/sessions/active"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /agent/runtime/overview")
  class GetOverview {

    @Test
    @DisplayName("should return 200 when get overview succeeds")
    void should_return_200_when_get_overview_succeeds() throws Exception {
      when(runtimeManagementService.getOverviewStats()).thenReturn(java.util.Map.of());

      mockMvc.perform(get("/agent/runtime/overview"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
