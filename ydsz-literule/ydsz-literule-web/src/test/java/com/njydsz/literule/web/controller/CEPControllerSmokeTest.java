package com.njydsz.literule.web.controller;

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
import com.njydsz.literule.domain.RuleEngine;
import com.njydsz.literule.server.cep.CEPEngine;

/**
 * {@link CEPController} Smoke Test。
 *
 * <p>验证 CEP 复杂事件处理端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class CEPControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private CEPEngine cepEngine;

  private CEPController controller;

  @BeforeEach
  void setUp() {
    ObjectProvider<CEPEngine> cepEngineProvider = mock(ObjectProvider.class);
    when(cepEngineProvider.getIfAvailable()).thenReturn(cepEngine);
    // CEPController 仅在 @PostConstruct 注册的命中回调里解析 ruleEngineProvider，
    // standaloneSetup 不触发该回调 → 此处不打桩 getIfAvailable()，否则 STRICT_STUBS 判为 UnnecessaryStubbing
    ObjectProvider<RuleEngine> ruleEngineProvider = mock(ObjectProvider.class);
    controller = new CEPController(cepEngineProvider, ruleEngineProvider);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/cep/patterns")
  class ListPatterns {

    @Test
    @DisplayName("should return 200 when list CEP patterns succeeds")
    void should_return_200_when_list_cep_patterns_succeeds() throws Exception {
      when(cepEngine.listPatterns()).thenReturn(List.of());

      mockMvc.perform(get("/literule/cep/patterns"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
