package com.njydsz.system.web.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.server.config.DictSseEmitterRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@link DictSseEmitterController} SSE contract tests. */
class DictSseEmitterControllerTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock private DictSseEmitterRegistry emitterRegistry;

  @InjectMocks private DictSseEmitterController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Test
  @DisplayName("GET /dict/sse creates an unlimited emitter and registers it")
  void should_open_and_register_sse_stream() throws Exception {
    when(emitterRegistry.register(any(SseEmitter.class))).thenReturn("session-1");

    MvcResult result =
        mockMvc.perform(get("/dict/sse").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andReturn();

    SseEmitter emitter = (SseEmitter) result.getAsyncResult();
    verify(emitterRegistry).register(emitter);
  }
}
