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
import com.njydsz.literule.domain.vo.DecisionTableVO;
import com.njydsz.literule.server.config.DecisionTableAdminService;
import com.njydsz.literule.server.converter.LiteruleWebConverter;
import com.njydsz.literule.server.spi.DecisionTableEvalProvider;
import com.njydsz.literule.server.spi.DecisionTableQueryService;
import com.njydsz.literule.server.support.ExcelWebSupport;

/**
 * {@link RuleDecisionTableController} Smoke Test。
 *
 * <p>验证决策表端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuleDecisionTableControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private DecisionTableQueryService decisionTableQueryService;

  @org.mockito.Mock
  private DecisionTableAdminService decisionTableAdminService;

  @org.mockito.Mock
  private DecisionTableEvalProvider decisionTableEvalProvider;

  @org.mockito.Mock
  private ExcelWebSupport excelWebSupport;

  @org.mockito.Mock
  private LiteruleWebConverter literuleWebConverter;

  @org.mockito.InjectMocks
  private RuleDecisionTableController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/rules/decision-tables")
  class ListDecisionTables {

    @Test
    @DisplayName("should return 200 when list decision tables succeeds")
    void should_return_200_when_list_decision_tables_succeeds() throws Exception {
      when(decisionTableQueryService.findAll()).thenReturn(List.of(new DecisionTableVO()));

      mockMvc.perform(get("/literule/rules/decision-tables"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
