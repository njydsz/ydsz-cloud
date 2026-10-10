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
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.dto.FlowInstanceViewDTO;

/**
 * {@link com.njydsz.workflow.web.controller.internal.InternalWorkflowApiController} Smoke Test。
 *
 * <p>验证内部 API 核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class InternalWorkflowApiControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private WorkflowFacade workflowFacade;

  @org.mockito.InjectMocks
  private com.njydsz.workflow.web.controller.internal.InternalWorkflowApiController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /internal/engine/instance/byBusiness")
  class GetByBusiness {

    @Test
    @DisplayName("should return 200 when get instance by business succeeds")
    void should_return_200_when_get_by_business_succeeds() throws Exception {
      FlowInstanceViewDTO dto = new FlowInstanceViewDTO();
      dto.setId("inst-001");
      when(workflowFacade.getByBusiness(anyString(), anyString())).thenReturn(dto);

      mockMvc.perform(get("/internal/engine/instance/byBusiness")
              .param("businessType", "PROJECT_INITIATION")
              .param("businessId", "proj-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
