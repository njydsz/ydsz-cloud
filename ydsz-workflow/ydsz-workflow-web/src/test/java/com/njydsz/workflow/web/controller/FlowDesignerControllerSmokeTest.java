package com.njydsz.workflow.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.server.engine.listener.FlowListenerPluginExecutor;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowTemplateService;

/**
 * {@link FlowDesignerController} Smoke Test。
 *
 * <p>验证流程设计器核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowDesignerControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FlowDefinitionService definitionService;

  @org.mockito.Mock
  private FlowTemplateService templateService;

  @org.mockito.Mock
  private FlowListenerPluginExecutor listenerPluginExecutor;

  @org.mockito.InjectMocks
  private FlowDesignerController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/{id}/designer")
  class GetDesignerData {

    @Test
    @DisplayName("should return 200 when get designer data succeeds")
    void should_return_200_when_get_designer_data_succeeds() throws Exception {
      when(definitionService.getDesignerData(anyString())).thenReturn(Map.of());

      mockMvc.perform(get("/workflow/engine/definition/{id}/designer", "def-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/template/list")
  class ListTemplates {

    @Test
    @DisplayName("should return 200 when list templates succeeds")
    void should_return_200_when_list_templates_succeeds() throws Exception {
      when(templateService.listTemplates(any())).thenReturn(List.of());

      mockMvc.perform(get("/workflow/engine/template/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
