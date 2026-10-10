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

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.query.SocialClientPageQuery;
import com.njydsz.userinfo.server.service.SocialClientConfigService;

/**
 * {@link SocialClientConfigController} Smoke Test。
 *
 * <p>验证社交平台配置查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class SocialClientConfigControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private SocialClientConfigService configService;

  @org.mockito.InjectMocks
  private SocialClientConfigController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(configService.findByPage(any(SocialClientPageQuery.class))).thenReturn(List.of());
    when(configService.findEnabledWithFallback()).thenReturn(List.of());
  }

  @Nested
  @DisplayName("GET /social-client-config/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      mockMvc.perform(get("/social-client-config/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /social-client-config/enabled")
  class ListEnabled {

    @Test
    @DisplayName("should return 200 when list enabled succeeds")
    void should_return_200_when_list_enabled_succeeds() throws Exception {
      mockMvc.perform(get("/social-client-config/enabled"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
