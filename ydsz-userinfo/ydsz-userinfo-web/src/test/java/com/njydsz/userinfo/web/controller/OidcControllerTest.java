package com.njydsz.userinfo.web.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.vo.JwksEndpointVO;
import com.njydsz.userinfo.domain.vo.OidcDiscoveryEndpointVO;
import com.njydsz.userinfo.server.config.OidcProperties;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link OidcController} HTTP 契约测试。
 *
 * <p>覆盖 OIDC Discovery 元数据与 JWKS 公钥两个标准端点：真实路径、Content-Type、响应字段，
 * 以及 JWKS Bean 缺失时的 503 降级负例。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class OidcControllerTest extends BaseControllerMockTest {

  private static final String BASE = "/.well-known";

  private MockMvc mockMvc;

  private OidcProperties oidcProperties;

  @BeforeEach
  void setUp() {
    oidcProperties = new OidcProperties();
    oidcProperties.setIssuer("https://userinfo.ydsz.com");
    mockMvc = standaloneSetup(new OidcController(oidcProperties, absentProvider(), "https://userinfo.ydsz.com"));
  }

  /**
   * 构造一个"容器里没有这个 bean"的 {@link ObjectProvider}（此处代表未注册的 JWKS 端点）。
   *
   * @param <T> Provider 承载的 bean 类型
   * @return 取不到任何 bean 的 provider
   */
  private static <T> ObjectProvider<T> absentProvider() {
    return new ObjectProvider<>() {
      @Override
      public T getIfAvailable() {
        return null;
      }

      @Override
      public T getIfUnique() {
        return null;
      }
    };
  }

  @Nested
  @DisplayName("GET /.well-known/openid-configuration")
  class Discovery {

    @Test
    @DisplayName("返回 OIDC Discovery 元数据：端点 URL 与能力声明完整")
    void should_return_discovery_metadata() throws Exception {
      mockMvc.perform(get(BASE + "/openid-configuration"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith("application/json"))
          .andExpect(jsonPath("$.issuer").value("https://userinfo.ydsz.com"))
          .andExpect(jsonPath("$.authorizationEndpoint")
              .value("https://userinfo.ydsz.com/api/oauth2/authorize"))
          .andExpect(jsonPath("$.tokenEndpoint").value("https://userinfo.ydsz.com/api/oauth2/token"))
          .andExpect(jsonPath("$.userinfoEndpoint")
              .value("https://userinfo.ydsz.com/api/oauth2/userinfo"))
          .andExpect(jsonPath("$.jwksUri").value("https://userinfo.ydsz.com/.well-known/jwks.json"))
          .andExpect(jsonPath("$.scopesSupported[0]").value(OidcDiscoveryEndpointVO.SCOPE_OPENID))
          .andExpect(jsonPath("$.responseTypesSupported[0]")
              .value(OidcDiscoveryEndpointVO.RESPONSE_TYPE_CODE))
          .andExpect(jsonPath("$.grantTypesSupported[0]")
              .value(OidcDiscoveryEndpointVO.GRANT_TYPE_AUTHORIZATION_CODE))
          .andExpect(jsonPath("$.tokenEndpointAuthMethodsSupported[0]")
              .value(OidcDiscoveryEndpointVO.AUTH_METHOD_CLIENT_SECRET_BASIC))
          .andExpect(jsonPath("$.idTokenSigningAlgValuesSupported[0]")
              .value(OidcDiscoveryEndpointVO.ALG_RS256))
          .andExpect(jsonPath("$.claimsSupported").isArray());
    }
  }

  @Nested
  @DisplayName("GET /.well-known/jwks.json")
  class Jwks {

    @Test
    @DisplayName("JWKS Bean 未注册时降级返回 503")
    void should_return_503_when_jwks_bean_absent() throws Exception {
      mockMvc.perform(get(BASE + "/jwks.json"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/json")))
          .andExpect(content().string(org.hamcrest.Matchers.containsString("JWKS endpoint not available")));
    }

    @Test
    @DisplayName("JWKS Bean 存在时返回公钥集合")
    void should_return_jwks_when_bean_present() throws Exception {
      JwksEndpointVO jwks = new JwksEndpointVO("secret".getBytes(), "-----BEGIN PUBLIC KEY-----");
      ObjectProvider<JwksEndpointVO> provider = new ObjectProvider<>() {
        @Override
        public JwksEndpointVO getIfAvailable() {
          return jwks;
        }

        @Override
        public JwksEndpointVO getIfUnique() {
          return jwks;
        }
      };
      MockMvc withJwks =
          standaloneSetup(new OidcController(oidcProperties, provider, "https://userinfo.ydsz.com"));

      withJwks.perform(get(BASE + "/jwks.json"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith("application/json"))
          .andExpect(content().string(org.hamcrest.Matchers.containsString("keys")));
    }
  }

}
