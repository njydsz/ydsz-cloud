package com.njydsz.literule.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.literule.server.cep.CEPEngine;

/**
 * {@link CEPController} 单元测试。
 *
 * <p>覆盖 CEP 引擎 happy-path 与引擎未启用 error-path。
 *
 * @author catpaw-test
 */
@ExtendWith(MockitoExtension.class)
class CEPControllerTest {

  private MockMvc mockMvc;

  @Mock
  private ObjectProvider<CEPEngine> cepEngineProvider;

  @Mock
  private ObjectProvider<com.njydsz.literule.domain.RuleEngine> ruleEngineProvider;

  @Mock
  private CEPEngine cepEngine;

  @InjectMocks
  private CEPController controller;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /**
   * Happy-path: 引擎可用时查询 stats 返回 200。
   */
  @Test
  @DisplayName("GET /literule/cep/stats 引擎启用时返回状态")
  void statsReturnsOkWhenEngineEnabled() throws Exception {
    when(cepEngineProvider.getIfAvailable()).thenReturn(cepEngine);
    when(cepEngine.patternCount()).thenReturn(3);
    when(cepEngine.totalHits()).thenReturn(42L);

    mockMvc.perform(get("/literule/cep/stats"))
        .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  /**
   * Error-path: 引擎未启用时查询 stats 返回 SERVICE_UNAVAILABLE 错误码。
   */
  @Test
  @DisplayName("GET /literule/cep/stats 引擎未启用时返回 503 错误码")
  void statsReturnsErrorWhenEngineDisabled() throws Exception {
    when(cepEngineProvider.getIfAvailable()).thenReturn(null);

    mockMvc.perform(get("/literule/cep/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(
            com.njydsz.common.core.code.YdszResultCode.SERVICE_UNAVAILABLE.getCode()));
  }
}
