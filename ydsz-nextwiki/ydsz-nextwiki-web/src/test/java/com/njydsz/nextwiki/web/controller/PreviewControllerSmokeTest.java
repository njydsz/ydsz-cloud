package com.njydsz.nextwiki.web.controller;

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
import com.njydsz.nextwiki.server.service.PreviewApplicationService;

/**
 * {@link PreviewController} Smoke Test。
 *
 * <p>验证文件预览端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class PreviewControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private PreviewApplicationService previewService;

  @org.mockito.InjectMocks
  private PreviewController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/preview/supported")
  class Supported {

    @Test
    @DisplayName("should return 200 when get supported preview types succeeds")
    void should_return_200_when_get_supported_preview_types_succeeds() throws Exception {
      when(previewService.isPreviewSupported("pdf")).thenReturn(true);

      mockMvc.perform(get("/nextwiki/preview/supported").param("suffix", "pdf"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
