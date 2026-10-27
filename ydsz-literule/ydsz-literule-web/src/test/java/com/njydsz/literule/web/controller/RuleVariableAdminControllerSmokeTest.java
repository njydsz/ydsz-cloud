package com.njydsz.literule.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.server.expression.ExpressionValidationService;
import com.njydsz.literule.server.expression.VariableRegistry;

/**
 * {@link RuleVariableAdminController} Smoke Test。
 *
 * <p>验证规则变量管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuleVariableAdminControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private VariableRegistry variableRegistry;

  @Mock
  private ExpressionValidationService expressionValidationService;

  private RuleVariableAdminController controller;

  @BeforeEach
  void setUp() {
    // 构造器签名注入 ObjectProvider<VariableRegistry>（缺失时回退 EmptyVariableRegistry）→ 手工构造
    ObjectProvider<VariableRegistry> variableRegistryProvider = mock(ObjectProvider.class);
    when(variableRegistryProvider.getIfAvailable(any())).thenReturn(variableRegistry);
    controller =
        new RuleVariableAdminController(variableRegistryProvider, expressionValidationService);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/variables")
  class ListVariables {

    @Test
    @DisplayName("should return 200 when list variables succeeds")
    void should_return_200_when_list_variables_succeeds() throws Exception {
      when(variableRegistry.listAll()).thenReturn(List.of());

      mockMvc.perform(get("/literule/variables"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
