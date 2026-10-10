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
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.server.debug.RuleDebugger;

/**
 * {@link RuleDebugController} Smoke Test。
 *
 * <p>验证规则断点调试端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuleDebugControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RuleDebugger ruleDebugger;

  private RuleDebugController controller;

  @BeforeEach
  void setUp() {
    ObjectProvider<RuleDebugger> debuggerProvider = Mockito.mock(ObjectProvider.class);
    Mockito.when(debuggerProvider.getIfAvailable()).thenReturn(ruleDebugger);
    controller = new RuleDebugController(debuggerProvider);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/debug/breakpoints")
  class ListBreakpoints {

    @Test
    @DisplayName("should return 200 when list breakpoints succeeds")
    void should_return_200_when_list_breakpoints_succeeds() throws Exception {
      when(ruleDebugger.listBreakpoints()).thenReturn(List.of());

      mockMvc.perform(get("/literule/debug/breakpoints"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
