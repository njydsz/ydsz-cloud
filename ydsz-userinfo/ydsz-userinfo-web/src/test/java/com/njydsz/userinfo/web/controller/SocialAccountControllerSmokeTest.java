package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
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
import com.njydsz.userinfo.domain.config.SocialAuthProperties;
import com.njydsz.userinfo.server.auth.SocialAuthService;

/**
 * {@link SocialAccountController} Smoke Test。
 *
 * <p>验证第三方账号绑定查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class SocialAccountControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    SocialAuthService socialAuthService = org.mockito.Mockito.mock(SocialAuthService.class);
    SocialAuthProperties socialAuthProperties = new SocialAuthProperties();
    socialAuthProperties.setProviders(java.util.Map.of());
    when(socialAuthService.listBindings(anyString())).thenReturn(List.of());
    SocialAccountController controller = new SocialAccountController(socialAuthService, socialAuthProperties);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /profile/social/bindings")
  class GetBindings {

    @Test
    @DisplayName("should return 200 when bindings query succeeds")
    void should_return_200_when_bindings_query_succeeds() throws Exception {
      mockMvc.perform(get("/profile/social/bindings"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /profile/social/platforms")
  class GetAvailablePlatforms {

    @Test
    @DisplayName("should return 200 when platforms query succeeds")
    void should_return_200_when_platforms_query_succeeds() throws Exception {
      mockMvc.perform(get("/profile/social/platforms"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
