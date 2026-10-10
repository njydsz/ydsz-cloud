package com.njydsz.agent.web.controller;

import static org.mockito.Mockito.mock;
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

import com.njydsz.agent.server.agent.AgentFacade;
import com.njydsz.agent.server.chat.AgentRequestGuard;
import com.njydsz.common.socket.push.SsePushChannel;
import com.njydsz.common.socket.push.SsePushChannelFactory;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link AgentController} Smoke Test.
 *
 * <p>Verify agent execution and chat endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class AgentControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    SsePushChannelFactory ssePushChannelFactory = mock(SsePushChannelFactory.class);
    SsePushChannel channel = mock(SsePushChannel.class);
    when(ssePushChannelFactory.create()).thenReturn(channel);

    AgentFacade agentFacade = mock(AgentFacade.class);
    AgentRequestGuard requestGuard = mock(AgentRequestGuard.class);

    when(agentFacade.getHistory("conv-001")).thenReturn(List.of());

    AgentController agentController = new AgentController(ssePushChannelFactory, agentFacade, requestGuard);
    mockMvc = standaloneSetup(agentController);
  }

  @Nested
  @DisplayName("GET /agent/history")
  class History {

    @Test
    @DisplayName("should return 200 when get history succeeds")
    void should_return_200_when_get_history_succeeds() throws Exception {
      mockMvc.perform(get("/agent/history")
              .param("conversationId", "conv-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
