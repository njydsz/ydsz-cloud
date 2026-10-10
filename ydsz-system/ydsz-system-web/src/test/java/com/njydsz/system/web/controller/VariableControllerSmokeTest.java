package com.njydsz.system.web.controller;

import static org.mockito.ArgumentMatchers.any;
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

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.query.VariablePageQuery;
import com.njydsz.system.server.service.VariableService;

/**
 * {@link VariableController} Smoke Test。
 *
 * <p>验证系统变量查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class VariableControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private VariableService variableService;

  @org.mockito.InjectMocks
  private VariableController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(variableService.page(any(VariablePageQuery.class)))
        .thenReturn(PageResponse.success(0L, 1L, 20L, List.of()));
    when(variableService.getById("var-001")).thenReturn(null);
    when(variableService.getVariableValue("finance.current_fiscal_year")).thenReturn(null);
  }

  @Nested
  @DisplayName("GET /variable/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      mockMvc.perform(get("/variable/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /variable/key/{variableKey}")
  class GetByKey {

    @Test
    @DisplayName("should return 200 when get by key succeeds")
    void should_return_200_when_get_by_key_succeeds() throws Exception {
      mockMvc.perform(get("/variable/key/{variableKey}", "finance.current_fiscal_year"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
