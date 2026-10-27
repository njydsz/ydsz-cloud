package com.njydsz.agent.web.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

  private SsePushChannelFactory ssePushChannelFactory;

  private AgentFacade agentFacade;

  @BeforeEach
  void setUp() {
    ssePushChannelFactory = mock(SsePushChannelFactory.class);
    agentFacade = mock(AgentFacade.class);
    AgentRequestGuard requestGuard = mock(AgentRequestGuard.class);

    AgentController agentController =
        new AgentController(ssePushChannelFactory, agentFacade, guardProvider(requestGuard));
    mockMvc = standaloneSetup(agentController);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<AgentRequestGuard> guardProvider(AgentRequestGuard guard) {
    ObjectProvider<AgentRequestGuard> provider = mock(ObjectProvider.class);
    // 构造器内 getIfAvailable() 消费此 stub，因此保留在 setUp 链路上
    when(provider.getIfAvailable()).thenReturn(guard);
    return provider;
  }

  @Nested
  @DisplayName("GET /agent/history")
  class History {

    @Test
    @DisplayName("should return 200 when get history succeeds")
    void should_return_200_when_get_history_succeeds() throws Exception {
      when(agentFacade.getHistory("conv-001")).thenReturn(List.of());

      mockMvc.perform(get("/agent/history")
              .param("conversationId", "conv-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /agent/chat/stream")
  class ChatStream {

    @Test
    @DisplayName("should return 200 SSE channel when chat stream starts")
    void should_return_200_when_chat_stream_starts() throws Exception {
      SsePushChannel channel = mock(SsePushChannel.class);
      when(channel.getEmitter()).thenReturn(new SseEmitter());
      when(ssePushChannelFactory.create()).thenReturn(channel);

      mockMvc.perform(post("/agent/chat/stream")
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  "{\"conversationId\":\"conv-001\",\"requestId\":\"req-001\","
                      + "\"message\":\"hello\"}"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }
  }
}
