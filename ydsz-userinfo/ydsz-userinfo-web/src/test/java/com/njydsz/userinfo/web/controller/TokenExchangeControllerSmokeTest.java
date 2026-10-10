package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.server.auth.CrossDomainTokenService;
import com.njydsz.userinfo.server.auth.SessionManager;
import com.njydsz.userinfo.server.config.CrossDomainSsoProperties;

/**
 * {@link TokenExchangeController} Smoke Test。
 *
 * <p>验证 Token 校验端点可正确返回 HTTP 200。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class TokenExchangeControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    CrossDomainSsoProperties ssoProperties = new CrossDomainSsoProperties();
    CrossDomainTokenService crossDomainTokenService = org.mockito.Mockito.mock(CrossDomainTokenService.class);
    TokenService tokenService = org.mockito.Mockito.mock(TokenService.class);
    SessionManager sessionManager = org.mockito.Mockito.mock(SessionManager.class);
    when(crossDomainTokenService.isTrustedDomain(anyString(), anyString())).thenReturn(true);
    when(tokenService.validateAccessToken(anyString())).thenReturn(true);
    TokenExchangeController controller = new TokenExchangeController(
        ssoProperties, crossDomainTokenService, tokenService, sessionManager);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /sso/validate")
  class Validate {

    @Test
    @DisplayName("should return 200 when validate succeeds")
    void should_return_200_when_validate_succeeds() throws Exception {
      mockMvc.perform(get("/sso/validate").param("token", "test-token"))
          .andExpect(status().isOk());
    }
  }
}
