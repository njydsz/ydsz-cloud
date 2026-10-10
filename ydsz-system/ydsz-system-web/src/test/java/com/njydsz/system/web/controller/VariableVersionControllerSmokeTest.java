package com.njydsz.system.web.controller;

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
import com.njydsz.system.server.service.EntityVersionService;
import com.njydsz.system.server.service.VariableService;

/**
 * {@link VariableVersionController} Smoke Test。
 *
 * <p>验证变量版本查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class VariableVersionControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private EntityVersionService entityVersionService;

  @org.mockito.Mock
  private VariableService variableService;

  @org.mockito.InjectMocks
  private VariableVersionController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(entityVersionService.listByResourceTypeAndKey(
        EntityVersionService.RESOURCE_TYPE_VARIABLE, "finance.current_fiscal_year"))
        .thenReturn(List.of());
  }

  @Nested
  @DisplayName("GET /variable/version/{resourceKey}")
  class ListByResourceKey {

    @Test
    @DisplayName("should return 200 when list succeeds")
    void should_return_200_when_list_succeeds() throws Exception {
      mockMvc.perform(get("/variable/version/{resourceKey}", "finance.current_fiscal_year"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
