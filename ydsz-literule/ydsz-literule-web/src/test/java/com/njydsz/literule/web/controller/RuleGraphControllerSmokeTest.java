package com.njydsz.literule.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.domain.vo.RuleChainGraphVO;
import com.njydsz.literule.server.converter.LiteruleWebConverter;
import com.njydsz.literule.server.expression.ExpressionValidationService;
import com.njydsz.literule.server.orchestrator.RuleChainGraph;
import com.njydsz.literule.server.spi.GraphExecutionProvider;
import com.njydsz.literule.server.spi.RuleChainGraphProvider;

/**
 * {@link RuleGraphController} Smoke Test。
 *
 * <p>验证规则链图端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuleGraphControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private RuleChainGraphProvider ruleChainGraphProvider;

  @Mock
  private GraphExecutionProvider graphExecutionProvider;

  @Mock
  private ExpressionValidationService expressionValidationService;

  @Mock
  private LiteruleWebConverter literuleWebConverter;

  @InjectMocks
  private RuleGraphController controller;

  @BeforeEach
  void setUp() {
    // @Resource 字段不参与 Mockito 构造器注入，需显式装配
    ReflectionTestUtils.setField(controller, "literuleWebConverter", literuleWebConverter);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/rules/{ruleCode}/graph")
  class GetGraph {

    @Test
    @DisplayName("should return 200 when get graph succeeds")
    void should_return_200_when_get_graph_succeeds() throws Exception {
      when(ruleChainGraphProvider.getByRuleCode("RULE-001")).thenReturn(new RuleChainGraph());
      when(literuleWebConverter.entityToVO(any(RuleChainGraph.class)))
          .thenReturn(new RuleChainGraphVO());

      mockMvc.perform(get("/literule/rules/RULE-001/graph"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
