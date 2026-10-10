package com.njydsz.message.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.server.service.retry.RetryPreviewService;

/**
 * {@link RetryPreviewController} Smoke Test。
 *
 * <p>验证重试策略预览端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RetryPreviewControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RetryPreviewService retryPreviewService;

  @org.mockito.InjectMocks
  private RetryPreviewController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/retry/presets")
  class ListPresets {

    @Test
    @DisplayName("should return 200 when list presets succeeds")
    void should_return_200_when_list_presets_succeeds() throws Exception {
      when(retryPreviewService.previewAllPresets()).thenReturn(java.util.Map.of());

      mockMvc.perform(get("/message/retry/presets"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
