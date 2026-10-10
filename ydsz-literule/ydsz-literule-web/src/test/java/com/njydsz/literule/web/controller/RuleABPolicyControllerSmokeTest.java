package com.njydsz.literule.web.controller;

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
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.server.converter.LiteruleWebConverter;
import com.njydsz.literule.server.spi.ABTestAutoRollbackProvider;

/**
 * {@link RuleABPolicyController} Smoke Test。
 *
 * <p>验证 AB Test 自动回滚策略端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuleABPolicyControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private ABTestAutoRollbackProvider abTestAutoRollbackProvider;

  @org.mockito.Mock
  private LiteruleWebConverter literuleWebConverter;

  @org.mockito.InjectMocks
  private RuleABPolicyController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/rules/{ruleCode}/ab-policy")
  class GetABPolicy {

    @Test
    @DisplayName("should return 200 when get AB policy succeeds")
    void should_return_200_when_get_ab_policy_succeeds() throws Exception {
      when(abTestAutoRollbackProvider.getPolicy("RULE-001")).thenReturn(new RuleABPolicyVO());

      mockMvc.perform(get("/literule/rules/RULE-001/ab-policy"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
