package com.njydsz.agent.web.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.agent.server.prompt.PromptPlaygroundService;
import com.njydsz.agent.server.prompt.PromptPlaygroundService.PromptInvokeResult;
import com.njydsz.common.socket.push.SsePushChannel;
import com.njydsz.common.socket.push.SsePushChannelFactory;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link PromptDebugController} Smoke Test.
 *
 * <p>Verify Prompt playground endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class PromptDebugControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private SsePushChannelFactory ssePushChannelFactory;

  @Mock
  private PromptPlaygroundService playgroundService;

  @InjectMocks
  private PromptDebugController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("POST /agent/prompt/playground/invoke")
  class Invoke {

    @Test
    @DisplayName("should return 200 when invoke succeeds")
    void should_return_200_when_invoke_succeeds() throws Exception {
      PromptInvokeResult result = new PromptInvokeResult(
          "gpt-4", 100L, 5, 10, 15, new BigDecimal("0.001"), 20, "response-content", LocalDateTime.now());
      when(playgroundService.invoke("test-prompt", "hello", null, null, null))
          .thenReturn(result);

      mockMvc.perform(post("/agent/prompt/playground/invoke")
              .contentType("application/json")
              .content("{\"prompt\":\"test-prompt\",\"userMessage\":\"hello\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /agent/prompt/playground/stream")
  class Stream {

    @Test
    @DisplayName("should return 200 SSE channel when stream starts")
    void should_return_200_when_stream_starts() throws Exception {
      SsePushChannel channel = mock(SsePushChannel.class);
      when(channel.getEmitter()).thenReturn(new SseEmitter());
      when(ssePushChannelFactory.create()).thenReturn(channel);

      mockMvc.perform(post("/agent/prompt/playground/stream")
              .contentType("application/json")
              .content("{\"prompt\":\"test-prompt\",\"userMessage\":\"hello\"}"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }
  }
}
