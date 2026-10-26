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
import org.mockito.Mock;
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
 * <p>{@code /sso/validate} 先按 Origin 白名单放行，再校验 token，因此测试必须携带 Origin 头，
 * 且桩参数需与 Controller 实际传入的 (origin, trustedDomains) 完全一致。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class TokenExchangeControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private CrossDomainTokenService crossDomainTokenService;

  @Mock
  private TokenService tokenService;

  @Mock
  private SessionManager sessionManager;

  private CrossDomainSsoProperties ssoProperties;

  @BeforeEach
  void setUp() {
    ssoProperties = new CrossDomainSsoProperties();
    TokenExchangeController controller =
        new TokenExchangeController(
            ssoProperties, crossDomainTokenService, tokenService, sessionManager);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /sso/validate")
  class Validate {

    @Test
    @DisplayName("should return 200 when validate succeeds")
    void should_return_200_when_validate_succeeds() throws Exception {
      ssoProperties.setTrustedDomains(List.of("https://app.example.com"));

      when(crossDomainTokenService.isTrustedDomain(
              "https://app.example.com", List.of("https://app.example.com")))
          .thenReturn(true);
      when(tokenService.validateAccessToken("test-token")).thenReturn(true);

      mockMvc.perform(
              get("/sso/validate")
                  .param("token", "test-token")
                  .header("Origin", "https://app.example.com"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.valid").value(true));
    }
  }
}
