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
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.config.RuleTraceQueryService;

/**
 * {@link RuleTraceController} Smoke Test。
 *
 * <p>验证规则执行追踪端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuleTraceControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RuleTraceQueryService ruleTraceQueryService;

  @org.mockito.Mock
  private RuleAdminService ruleAdminService;

  @org.mockito.InjectMocks
  private RuleTraceController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/rules/traces")
  class ListRecentTraces {

    @Test
    @DisplayName("should return 200 when list recent traces succeeds")
    void should_return_200_when_list_recent_traces_succeeds() throws Exception {
      when(ruleTraceQueryService.findRecent(50)).thenReturn(List.of());

      mockMvc.perform(get("/literule/rules/traces"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
