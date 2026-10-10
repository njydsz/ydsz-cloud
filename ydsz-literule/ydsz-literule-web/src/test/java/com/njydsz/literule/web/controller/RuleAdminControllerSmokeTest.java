package com.njydsz.literule.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.domain.RuleEngine;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.server.config.ABTestService;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.converter.LiteruleWebConverter;
import com.njydsz.literule.server.expression.ExpressionValidationService;

/**
 * {@link RuleAdminController} Smoke Test。
 *
 * <p>验证规则管理核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuleAdminControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RuleAdminService ruleAdminService;

  @org.mockito.Mock
  private ABTestService abTestService;

  @org.mockito.Mock
  private RuleEngine ruleEngine;

  @org.mockito.Mock
  private ExpressionValidationService expressionValidationService;

  @org.mockito.Mock
  private LiteruleWebConverter literuleWebConverter;

  @org.mockito.InjectMocks
  private RuleAdminController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/rules")
  class List {

    @Test
    @DisplayName("should return 200 when list succeeds")
    void should_return_200_when_list_succeeds() throws Exception {
      PageResponse<List<RuleDefinitionDTO>> pageResult = PageResponse.success(0L, 1L, 20L, Collections.emptyList());
      when(ruleAdminService.pageRuleDefinitions(any(PageQuery.class))).thenReturn(pageResult);

      mockMvc.perform(get("/literule/rules").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /literule/rules/validate")
  class Validate {

    @Test
    @DisplayName("should return 200 when validate expression succeeds")
    void should_return_200_when_validate_expression_succeeds() throws Exception {
      when(ruleAdminService.validateExpression("amount > 1000")).thenReturn(true);

      mockMvc.perform(get("/literule/rules/validate").param("expression", "amount > 1000"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /literule/rules/stats")
  class Stats {

    @Test
    @DisplayName("should return 200 when stats query succeeds")
    void should_return_200_when_stats_query_succeeds() throws Exception {
      when(ruleEngine.getStats()).thenReturn(null);

      mockMvc.perform(get("/literule/rules/stats"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
