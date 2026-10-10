package com.njydsz.workflow.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
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
import com.njydsz.workflow.domain.dto.EmbeddedApprovalViewDTO;
import com.njydsz.workflow.server.service.FlowEmbeddedApprovalService;

/**
 * {@link FlowEmbeddedApprovalController} Smoke Test。
 *
 * <p>验证嵌入式审批核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowEmbeddedApprovalControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FlowEmbeddedApprovalService embeddedApprovalService;

  @org.mockito.InjectMocks
  private FlowEmbeddedApprovalController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /workflow/embedded/panel")
  class LoadPanel {

    @Test
    @DisplayName("should return 200 when load embedded panel succeeds")
    void should_return_200_when_load_embedded_panel_succeeds() throws Exception {
      EmbeddedApprovalViewDTO dto = new EmbeddedApprovalViewDTO();
      when(embeddedApprovalService.loadPanel(anyString(), anyString(), anyString())).thenReturn(dto);

      mockMvc.perform(get("/workflow/embedded/panel")
              .param("businessType", "PROJECT_INITIATION")
              .param("businessId", "proj-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
