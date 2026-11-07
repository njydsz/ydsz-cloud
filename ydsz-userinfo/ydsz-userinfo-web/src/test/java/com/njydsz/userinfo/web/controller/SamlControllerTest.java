package com.njydsz.userinfo.web.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.SamlIdpConfigVO;
import com.njydsz.userinfo.server.auth.SamlService;
import com.njydsz.userinfo.server.service.SamlIdpConfigService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SamlController} HTTP 契约测试。
 *
 * <p>覆盖 SP Metadata / SSO 发起 / ACS 回调 / 多租户 IdP 路由与 IdP 列表端点，
 * 每个端点验证真实 HTTP 路径、方法、参数绑定、成功响应字段，并至少包含一个错误信封反例。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class SamlControllerTest extends BaseControllerMockTest {

  private static final String BASE = "/saml";

  @Mock private SamlService samlService;
  @Mock private SamlIdpConfigService samlIdpConfigService;

  @InjectMocks private SamlController controller;

  private MockMvc mockMvc;
  private MockMvc adviceMockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    // 错误信封是 YdszResponse（@JsonInclude/@JsonPropertyOrder），需装项目统一 converter 才能与生产序列化一致。
    adviceMockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new MvcExceptionHandler(
                    new org.springframework.mock.env.MockEnvironment(),
                    null,
                    absentProvider(),
                    absentProvider(),
                    absentProvider()))
            .setMessageConverters(new JsonHttpMessageConverter())
            .build();
  }

  /**
   * 构造一个"容器里没有这个 bean"的 {@link org.springframework.beans.factory.ObjectProvider}。
   *
   * @param <T> Provider 承载的 bean 类型
   * @return 取不到任何 bean 的 provider
   */
  /**
   * 百分号编码路径变量（entityId 含斜杠与冒号）。
   *
   * @param value 原始值
   * @return 编码后的安全路径段
   */
  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static <T> org.springframework.beans.factory.ObjectProvider<T> absentProvider() {
    return new org.springframework.beans.factory.ObjectProvider<>() {
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
  @DisplayName("GET /saml/metadata")
  class Metadata {

    @Test
    @DisplayName("返回 application/xml 的 SP 元数据")
    void should_return_sp_metadata() throws Exception {
      when(samlService.generateMetadata()).thenReturn("<EntityDescriptor>test</EntityDescriptor>");

      mockMvc.perform(get(BASE + "/metadata"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML_VALUE))
          .andExpect(content().string("<EntityDescriptor>test</EntityDescriptor>"));
    }
  }

  @Nested
  @DisplayName("GET /saml/sso")
  class InitiateSso {

    @Test
    @DisplayName("重定向到 IdP SSO 地址")
    void should_redirect_to_idp() throws Exception {
      when(samlService.buildAuthnRequestUrl()).thenReturn("https://idp.example.com/sso?SAMLRequest=abc");

      mockMvc.perform(get(BASE + "/sso"))
          .andExpect(status().is3xxRedirection())
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
              .redirectedUrl("https://idp.example.com/sso?SAMLRequest=abc"));
    }

    @Test
    @DisplayName("反例：AuthnRequest 构建失败 → HTTP 400 + SAML_SSO_INIT_FAILED")
    void should_return_error_envelope_when_build_fails() throws Exception {
      when(samlService.buildAuthnRequestUrl()).thenThrow(new IllegalStateException("keystore missing"));

      adviceMockMvc.perform(get(BASE + "/sso"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.SAML_SSO_INIT_FAILED.getCode()));
    }
  }

  @Nested
  @DisplayName("POST /saml/acs")
  class AssertionConsumerService {

    @Test
    @DisplayName("ACS 成功：SAMLResponse 绑定为请求参数并返回用户属性")
    void should_process_saml_response() throws Exception {
      when(samlService.processSamlResponse("saml-response-body"))
          .thenReturn(Map.of("nameId", "user@example.com"));

      mockMvc.perform(post(BASE + "/acs")
              .param("SAMLResponse", "saml-response-body")
              .param("RelayState", "/home"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.nameId").value("user@example.com"));
    }

    @Test
    @DisplayName("反例：SAMLResponse 为空 → HTTP 400 + SAML_RESPONSE_INVALID")
    void should_reject_blank_saml_response() throws Exception {
      adviceMockMvc.perform(post(BASE + "/acs").param("SAMLResponse", "  "))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.SAML_RESPONSE_INVALID.getCode()));

      verifyNoInteractions(samlService);
    }
  }

  @Nested
  @DisplayName("GET /saml/sso/{idpEntityId}")
  class InitiateSsoByEntityId {

    @Test
    @DisplayName("按 IdP Entity ID 路由并重定向")
    void should_redirect_by_entity_id() throws Exception {
      String entityId = "https://idp.example.com/metadata";
      when(samlService.buildAuthnRequestUrlByEntityId(entityId))
          .thenReturn("https://idp.example.com/sso?SAMLRequest=xyz");

      // entityId 是 URL，作为路径变量必须百分号编码，否则其中的斜杠会被当成路径分段导致 404
      mockMvc.perform(get(BASE + "/sso/{idpEntityId}", encode(entityId)))
          .andExpect(status().is3xxRedirection())
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
              .redirectedUrl("https://idp.example.com/sso?SAMLRequest=xyz"));
    }

    @Test
    @DisplayName("反例：IdP 配置缺失 → HTTP 400 + SAML_SSO_INIT_FAILED")
    void should_return_error_envelope_when_idp_not_found() throws Exception {
      when(samlService.buildAuthnRequestUrlByEntityId(anyString()))
          .thenThrow(BusinessException.of(UserInfoExceptionCode.SAML_CONFIG_MISSING));

      adviceMockMvc.perform(get(BASE + "/sso/{idpEntityId}", "unknown-idp"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.SAML_CONFIG_MISSING.getCode()));
    }
  }

  @Nested
  @DisplayName("POST /saml/acs/{idpEntityId}")
  class AssertionConsumerServiceByEntityId {

    @Test
    @DisplayName("多租户 ACS 成功：使用指定 IdP 证书验证")
    void should_process_multi_tenant_saml_response() throws Exception {
      String entityId = "https://idp.example.com/metadata";
      when(samlService.processSamlResponseWithEntityId("saml-response-body", entityId))
          .thenReturn(Map.of("nameId", "user@example.com"));

      mockMvc.perform(post(BASE + "/acs/{idpEntityId}", encode(entityId))
              .param("SAMLResponse", "saml-response-body"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.nameId").value("user@example.com"));
    }

    @Test
    @DisplayName("反例：多租户 SAMLResponse 为空 → HTTP 400 + SAML_RESPONSE_INVALID")
    void should_reject_blank_saml_response_multi_tenant() throws Exception {
      adviceMockMvc.perform(post(BASE + "/acs/{idpEntityId}", encode("https://idp.example.com/metadata"))
              .param("SAMLResponse", ""))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.SAML_RESPONSE_INVALID.getCode()));
    }
  }

  @Nested
  @DisplayName("GET /saml/idp-list")
  class ListIdps {

    @Test
    @DisplayName("返回启用的 IdP 列表")
    void should_list_enabled_idps() throws Exception {
      SamlIdpConfigVO vo = new SamlIdpConfigVO();
      vo.setEntityId("https://idp.example.com/metadata");
      vo.setName("示例 IdP");
      when(samlIdpConfigService.findEnabled()).thenReturn(List.of(vo));

      mockMvc.perform(get(BASE + "/idp-list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].entityId").value("https://idp.example.com/metadata"))
          .andExpect(jsonPath("$.data[0].name").value("示例 IdP"));
    }
  }
}
