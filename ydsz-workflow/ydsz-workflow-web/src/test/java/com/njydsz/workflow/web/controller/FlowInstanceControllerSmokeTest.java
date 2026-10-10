package com.njydsz.workflow.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.query.FlowInstancePageQuery;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.server.service.DraftInstanceService;
import com.njydsz.workflow.server.service.FlowAutoTriggerService;
import com.njydsz.workflow.server.service.FlowInstanceMigrationService;
import com.njydsz.workflow.server.service.FlowInstanceService;

/**
 * {@link FlowInstanceController} Smoke Test。
 *
 * <p>验证流程实例端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class FlowInstanceControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FlowInstanceService instanceService;

  @org.mockito.Mock
  private WorkflowFacade workflowFacade;

  @org.mockito.Mock
  private FlowInstanceMigrationService instanceMigrationService;

  @org.mockito.Mock
  private FlowAutoTriggerService autoTriggerService;

  @org.mockito.Mock
  private DraftInstanceService draftInstanceService;

  @org.mockito.InjectMocks
  private FlowInstanceController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/page")
  class InstancePage {

    @Test
    @DisplayName("should return 200 when instance page query succeeds")
    void should_return_200_when_instance_page_query_succeeds() throws Exception {
      FlowInstanceVO vo = new FlowInstanceVO();
      vo.setId("inst-001");
      PageResponse<List<FlowInstanceVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(instanceService.page(any(FlowInstancePageQuery.class))).thenReturn(pageResult);

      mockMvc.perform(get("/workflow/engine/instance/page")
              .param("pageNo", "1")
              .param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/{id}/auditTrail")
  class AuditTrail {

    @Test
    @DisplayName("should return 200 when audit trail query succeeds")
    void should_return_200_when_audit_trail_query_succeeds() throws Exception {
      when(workflowFacade.listAuditTrail("inst-001")).thenReturn(List.of());

      mockMvc.perform(get("/workflow/engine/instance/{id}/auditTrail", "inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when instance variables query succeeds")
    void should_return_200_when_instance_variables_query_succeeds() throws Exception {
      when(instanceService.getVariables("inst-001")).thenReturn(java.util.Map.of());

      mockMvc.perform(get("/workflow/engine/instance/{id}/variables", "inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
