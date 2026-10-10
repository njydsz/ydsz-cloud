package com.njydsz.agent.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.agent.server.prompt.PromptEvaluationService;
import com.njydsz.agent.server.prompt.PromptEvaluationService.PromptEvaluationResult;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link PromptController} Smoke Test.
 *
 * <p>Verify prompt evaluation endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class PromptControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private PromptEvaluationService evaluationService;

  @org.mockito.InjectMocks
  private PromptController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("POST /agent/prompt/evaluate")
  class Evaluate {

    @Test
    @DisplayName("should return 200 when evaluate succeeds")
    void should_return_200_when_evaluate_succeeds() throws Exception {
      PromptEvaluationResult result = new PromptEvaluationResult(
          "tpl-001", "rendered", "gpt-4", 100L, 5, 10, 15,
          new BigDecimal("0.001"), 20, "response", LocalDateTime.now());
      when(evaluationService.evaluate("tpl-001", null, "hello", null))
          .thenReturn(result);

      mockMvc.perform(post("/agent/prompt/evaluate")
              .contentType("application/json")
              .content("{\"templateCode\":\"tpl-001\",\"userMessage\":\"hello\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
