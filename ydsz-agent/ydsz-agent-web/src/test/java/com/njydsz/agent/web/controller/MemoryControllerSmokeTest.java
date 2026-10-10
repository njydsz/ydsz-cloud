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

import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.server.memory.ConversationMemoryConsolidationService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link MemoryController} Smoke Test.
 *
 * <p>Verify conversation memory endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class MemoryControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private ConversationMemory conversationMemory;

  @org.mockito.Mock
  private ConversationMemoryConsolidationService consolidationService;

  @org.mockito.InjectMocks
  private MemoryController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/memory/{conversationId}")
  class LoadMemory {

    @Test
    @DisplayName("should return 200 when load memory succeeds")
    void should_return_200_when_load_memory_succeeds() throws Exception {
      when(conversationMemory.load("conv-001", 50)).thenReturn(List.of());

      mockMvc.perform(get("/agent/memory/{conversationId}", "conv-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
