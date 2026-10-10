package com.njydsz.literule.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.server.approval.RuleApprovalService;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.converter.LiteruleWebConverter;

/**
 * {@link RuleLifecycleController} Smoke Test。
 *
 * <p>验证规则生命周期端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuleLifecycleControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RuleAdminService ruleAdminService;

  @org.mockito.Mock
  private ObjectProvider<RuleApprovalService> ruleApprovalServiceProvider;

  @org.mockito.Mock
  private LiteruleWebConverter literuleWebConverter;

  @org.mockito.InjectMocks
  private RuleLifecycleController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/rules/approval-flows")
  class ApprovalFlows {

    @Test
    @DisplayName("should return 200 when list approval flows succeeds")
    void should_return_200_when_list_approval_flows_succeeds() throws Exception {
      when(ruleApprovalServiceProvider.getIfAvailable()).thenReturn(null);

      mockMvc.perform(get("/literule/rules/approval-flows"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
