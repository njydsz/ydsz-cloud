package com.njydsz.nextwiki.web.controller.ai;

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

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.server.service.AiSummaryService;

/**
 * {@link AiController} Smoke Test。
 *
 * <p>验证 AI 智能摘要端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class AiControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private AiSummaryService aiSummaryService;

  @org.mockito.InjectMocks
  private AiController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/ai/status")
  class Status {

    @Test
    @DisplayName("should return 200 when get AI status succeeds")
    void should_return_200_when_get_ai_status_succeeds() throws Exception {
      when(aiSummaryService.isAvailable()).thenReturn(true);
      when(aiSummaryService.getSupportedFileTypes()).thenReturn(List.of());

      mockMvc.perform(get("/nextwiki/ai/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
