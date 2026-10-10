package com.njydsz.agent.web.controller.teamrun;

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

import com.njydsz.agent.server.teamrun.TeamRunOrchestrationService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link TeamRunController} Smoke Test.
 *
 * <p>Verify Team Run management endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class TeamRunControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private TeamRunOrchestrationService orchestrationService;

  @org.mockito.InjectMocks
  private TeamRunController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/teamruns")
  class ListActiveTeamRuns {

    @Test
    @DisplayName("should return 200 when list active team runs succeeds")
    void should_return_200_when_list_active_team_runs_succeeds() throws Exception {
      when(orchestrationService.listActiveTeamRuns("1")).thenReturn(List.of());

      mockMvc.perform(get("/agent/teamruns"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
