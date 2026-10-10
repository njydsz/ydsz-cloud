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

import com.njydsz.agent.server.agent.AgentFacade;
import com.njydsz.agent.server.agent.HumanApprovalService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link HumanApprovalController} Smoke Test.
 *
 * <p>Verify human-in-the-loop approval endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class HumanApprovalControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private HumanApprovalService approvalService;

  @org.mockito.Mock
  private AgentFacade agentFacade;

  @org.mockito.InjectMocks
  private HumanApprovalController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/approvals/pending")
  class ListPending {

    @Test
    @DisplayName("should return 200 when list pending approvals succeeds")
    void should_return_200_when_list_pending_approvals_succeeds() throws Exception {
      when(approvalService.listPending()).thenReturn(List.of());

      mockMvc.perform(get("/agent/approvals/pending"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
