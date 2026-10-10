package com.njydsz.userinfo.web.controller;

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
import com.njydsz.userinfo.domain.query.LoginLogPageQuery;
import com.njydsz.userinfo.server.service.LoginHistoryService;

/**
 * {@link LoginLogController} Smoke Test。
 *
 * <p>验证登录日志查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class LoginLogControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private LoginHistoryService loginHistoryService;

  @org.mockito.InjectMocks
  private LoginLogController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(loginHistoryService.pageLoginHistory(any(LoginLogPageQuery.class)))
        .thenReturn(PageResponse.success(0L, 1L, 20L, List.of()));
  }

  @Nested
  @DisplayName("GET /userinfo/login-log/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      mockMvc.perform(get("/userinfo/login-log/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
