package com.njydsz.userinfo.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.oauth2.OAuth2Application;
import com.njydsz.userinfo.server.oauth2.OAuth2ApplicationService;

/**
 * {@link OAuth2ApplicationController} Smoke Test。
 *
 * <p>验证 OAuth2 应用查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class OAuth2ApplicationControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private OAuth2ApplicationService applicationService;

  @InjectMocks
  private OAuth2ApplicationController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /admin/oauth2/applications")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      // status/keyword 未传时 Controller 传入 null；pageNum/pageSize 走 @RequestParam 默认值
      when(applicationService.page(null, null, 1, 20))
          .thenReturn(PageResponse.success(0L, 1L, 20L, List.of()));

      mockMvc.perform(get("/admin/oauth2/applications").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /admin/oauth2/applications/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      when(applicationService.getById("app-001")).thenReturn(null);

      mockMvc.perform(get("/admin/oauth2/applications/{id}", "app-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
