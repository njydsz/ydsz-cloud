package com.njydsz.system.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.exception.code.ErrorCodeTable;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link ErrorCodeController} Smoke Test。
 *
 * <p>验证错误码查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class ErrorCodeControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ErrorCodeTable errorCodeTable = org.mockito.Mockito.mock(ErrorCodeTable.class);
    when(errorCodeTable.size()).thenReturn(0);
    when(errorCodeTable.groupByModule()).thenReturn(Collections.emptyMap());
    ErrorCodeController controller = new ErrorCodeController(errorCodeTable);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /error-codes")
  class ListAll {

    @Test
    @DisplayName("should return 200 when list succeeds")
    void should_return_200_when_list_succeeds() throws Exception {
      mockMvc.perform(get("/error-codes"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
