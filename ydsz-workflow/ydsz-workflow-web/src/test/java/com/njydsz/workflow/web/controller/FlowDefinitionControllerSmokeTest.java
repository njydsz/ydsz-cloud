package com.njydsz.workflow.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.domain.dto.FlowDeployProcessDTO;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.server.service.FlowConditionExprService;
import com.njydsz.workflow.server.service.FlowCustomButtonService;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;
import com.njydsz.workflow.server.service.FlowSlaService;
import com.njydsz.workflow.server.service.FlowTaskService;

/**
 * {@link FlowDefinitionController} Smoke Test（P0-7 集成测试试点）。
 *
 * <p>验证流程定义核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * @author ydsz-team
 * @since 26.10.10
 */
class FlowDefinitionControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FlowDefinitionService definitionService;
  @org.mockito.Mock
  private FlowEventSubscriptionService eventSubscriptionService;
  @org.mockito.Mock
  private FlowSlaService slaService;
  @org.mockito.Mock
  private FlowConditionExprService conditionExprService;
  @org.mockito.Mock
  private FlowCustomButtonService customButtonService;
  @org.mockito.Mock
  private FlowTaskService taskService;

  @org.mockito.InjectMocks
  private FlowDefinitionController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/deploy")
  class Deploy {

    @Test
    @DisplayName("should return 200 with YdszResponse envelope")
    void deploy_returns200() throws Exception {
      when(definitionService.deploy(any(FlowDeployProcessDTO.class))).thenReturn("def-new-001");

      String body = "{\"flowCode\":\"LEAVE_APPLY\",\"flowName\":\"请假流程\",\"status\":\"DRAFT\"}";

      mockMvc.perform(post("/workflow/engine/definition/deploy")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/{id}/publish")
  class Publish {

    @Test
    @DisplayName("should return 200 with YdszResponse envelope")
    void publish_returns200() throws Exception {
      doNothing().when(definitionService).publish("def-001", false);

      mockMvc.perform(post("/workflow/engine/definition/{id}/publish", "def-001")
              .param("force", "false"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/page")
  class Page {

    @Test
    @DisplayName("should return 200 with YdszResponse envelope")
    void page_returns200() throws Exception {
      FlowDefinitionVO vo = new FlowDefinitionVO();
      vo.setId("def-001");
      vo.setFlowCode("LEAVE_APPLY");
      vo.setFlowName("请假流程");
      PageResponse<List<FlowDefinitionVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(definitionService.page(anyInt(), anyInt(), anyString(), anyString())).thenReturn(pageResult);

      mockMvc.perform(get("/workflow/engine/definition/page")
              .param("pageNum", "1")
              .param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 with YdszResponse envelope")
    void getById_returns200() throws Exception {
      FlowDefinitionVO vo = new FlowDefinitionVO();
      vo.setId("def-001");
      vo.setFlowCode("LEAVE_APPLY");
      vo.setFlowName("请假流程");
      when(definitionService.getDetail("def-001")).thenReturn(null);

      mockMvc.perform(get("/workflow/engine/definition/{id}", "def-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
