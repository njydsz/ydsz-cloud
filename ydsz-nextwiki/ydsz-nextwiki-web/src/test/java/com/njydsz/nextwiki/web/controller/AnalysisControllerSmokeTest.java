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
import com.njydsz.nextwiki.server.service.AiSummaryApplicationService;
import com.njydsz.nextwiki.server.service.StorageAnalysisApplicationService;

/**
 * {@link AnalysisController} Smoke Test。
 *
 * <p>验证存储分析端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class AnalysisControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private StorageAnalysisApplicationService storageAnalysisService;

  @org.mockito.Mock
  private AiSummaryApplicationService aiSummaryService;

  @org.mockito.InjectMocks
  private AnalysisController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/analysis/overview")
  class Overview {

    @Test
    @DisplayName("should return 200 when get analysis overview succeeds")
    void should_return_200_when_get_analysis_overview_succeeds() throws Exception {
      StorageAnalysisApplicationService.StorageOverview overview =
          new StorageAnalysisApplicationService.StorageOverview();
      when(storageAnalysisService.getUserOverview("user-001")).thenReturn(overview);

      mockMvc.perform(get("/nextwiki/analysis/overview")
              .header("X-User-Id", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
