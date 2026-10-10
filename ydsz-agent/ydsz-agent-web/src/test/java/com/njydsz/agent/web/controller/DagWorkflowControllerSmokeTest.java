package com.njydsz.agent.web.controller;

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

import com.njydsz.agent.domain.service.DagWorkflowService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link DagWorkflowController} Smoke Test.
 *
 * <p>Verify DAG workflow management endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class DagWorkflowControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private DagWorkflowService dagWorkflowService;

  @org.mockito.InjectMocks
  private DagWorkflowController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/dag-workflow/list")
  class ListWorkflows {

    @Test
    @DisplayName("should return 200 when list dag workflows succeeds")
    void should_return_200_when_list_dag_workflows_succeeds() throws Exception {
      when(dagWorkflowService.listByCategory(null)).thenReturn(List.of());

      mockMvc.perform(get("/agent/dag-workflow/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
