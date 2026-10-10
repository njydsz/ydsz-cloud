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

import com.njydsz.agent.server.debug.AgentDebuggerService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link DebugController} Smoke Test.
 *
 * <p>Verify debug trace endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class DebugControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private AgentDebuggerService agentDebuggerService;

  @org.mockito.InjectMocks
  private DebugController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/debug/traces")
  class ListTraces {

    @Test
    @DisplayName("should return 200 when list traces succeeds")
    void should_return_200_when_list_traces_succeeds() throws Exception {
      when(agentDebuggerService.listTraceMetas(20)).thenReturn(List.of());

      mockMvc.perform(get("/agent/debug/traces"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
