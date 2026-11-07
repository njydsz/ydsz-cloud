package com.njydsz.userinfo.web.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.domain.vo.WebAuthnCredentialVO;
import com.njydsz.userinfo.server.auth.RoleCacheService;
import com.njydsz.userinfo.server.auth.WebAuthnRegisterCommand;
import com.njydsz.userinfo.server.auth.WebAuthnService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebAuthnController} HTTP 契约测试。
 *
 * <p>覆盖 Passkey 注册/认证、WebAuthn 注册/认证、凭证列表与删除端点。
 * 认证上下文由 {@code AuthContextUtils}（底层 {@link RequestContext}）提供，故测试需写入登录用户。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class WebAuthnControllerTest extends BaseControllerMockTest {

  private static final String BASE = "/webauthn";

  /** 登录用户上下文键，与 BizContextKeys.KEY_LOGIN_USER 保持一致。 */
  private static final String KEY_LOGIN_USER = "loginUser";

  @Mock private WebAuthnService webAuthnService;
  @Mock private TokenService tokenService;
  @Mock private RoleCacheService roleCacheService;

  @InjectMocks private WebAuthnController controller;

  private MockMvc mockMvc;
  private MockMvc adviceMockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    // 错误信封需要 advice + 项目统一 converter
    adviceMockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new MvcExceptionHandler(
                    new MockEnvironment(),
                    null,
                    absentProvider(),
                    absentProvider(),
                    absentProvider()))
            .setMessageConverters(new JsonHttpMessageConverter())
            .build();
    givenLoginUser("user-001", "admin");
  }

  /**
   * 构造一个"容器里没有这个 bean"的 {@link ObjectProvider}。
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

  /**
   * 写入登录用户上下文（模拟认证过滤器行为）。
   *
   * @param userId 用户 ID
   * @param username 用户名
   */
  private static void givenLoginUser(String userId, String username) {
    LoginUser loginUser = new LoginUser();
    loginUser.setUserId(userId);
    loginUser.setUsername(username);
    AuthContextUtils.setCurrent(loginUser);
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  private UserAccountVO account() {
    UserAccountVO account = new UserAccountVO();
    account.setId("user-001");
    account.setUsername("admin");
    account.setTenantId("1");
    return account;
  }

  private RoleVO role() {
    RoleVO role = new RoleVO();
    role.setRoleCode("ROLE_ADMIN");
    role.setRoleName("管理员");
    return role;
  }

  private void givenTokenPair() {
    when(tokenService.issueAccessToken(any(com.njydsz.common.auth.model.UserInfo.class)))
        .thenReturn("access-token");
    when(tokenService.issueRefreshToken(any(com.njydsz.common.auth.model.UserInfo.class)))
        .thenReturn("refresh-token");
  }

  @Nested
  @DisplayName("GET /webauthn/passkey/registration/options")
  class PasskeyRegistrationOptions {

    @Test
    @DisplayName("返回可发现凭证注册选项")
    void should_return_passkey_registration_options() throws Exception {
      Map<String, Object> options = new HashMap<>();
      options.put("challenge", "challenge-1");
      options.put("residentKey", "required");
      when(webAuthnService.generatePasskeyRegistrationOptions("user-001", "admin", "admin"))
          .thenReturn(options);

      mockMvc.perform(get(BASE + "/passkey/registration/options"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.challenge").value("challenge-1"))
          .andExpect(jsonPath("$.data.residentKey").value("required"));
    }

    @Test
    @DisplayName("显式传参时使用请求参数而非上下文")
    void should_use_request_params_when_provided() throws Exception {
      Map<String, Object> options = new HashMap<>();
      options.put("challenge", "challenge-2");
      when(webAuthnService.generatePasskeyRegistrationOptions("user-001", "bob", "Bob"))
          .thenReturn(options);

      mockMvc.perform(get(BASE + "/passkey/registration/options")
              .param("username", "bob")
              .param("displayName", "Bob"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.challenge").value("challenge-2"));
    }
  }

  @Nested
  @DisplayName("GET /webauthn/passkey/options")
  class PasskeyAuthenticationOptions {

    @Test
    @DisplayName("返回空 allowCredentials 的认证选项")
    void should_return_passkey_authentication_options() throws Exception {
      Map<String, Object> options = new HashMap<>();
      options.put("challenge", "challenge-auth");
      options.put("allowCredentials", List.of());
      when(webAuthnService.generatePasskeyAuthenticationOptions()).thenReturn(options);

      mockMvc.perform(get(BASE + "/passkey/options"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.challenge").value("challenge-auth"))
          .andExpect(jsonPath("$.data.allowCredentials").isArray());
    }
  }

  @Nested
  @DisplayName("POST /webauthn/passkey/verify")
  class VerifyPasskeyAuthentication {

    @Test
    @DisplayName("验签通过：签发 access/refresh token")
    void should_issue_tokens_when_passkey_valid() throws Exception {
      when(webAuthnService.verifyPasskeyAuthentication(
              eq("challenge-auth"), eq("cred-1"), eq("cd"), eq("ad"), eq("sig")))
          .thenReturn("user-001");
      when(webAuthnService.findUserById("user-001")).thenReturn(account());
      when(roleCacheService.loadUserRoles("user-001")).thenReturn(List.of(role()));
      givenTokenPair();

      mockMvc.perform(post(BASE + "/passkey/verify")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"challenge\":\"challenge-auth\",\"credentialId\":\"cred-1\","
                  + "\"clientDataJSON\":\"cd\",\"authenticatorData\":\"ad\",\"signature\":\"sig\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.accessToken").value("access-token"))
          .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
          .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("反例：验签抛错 → HTTP 500 错误信封")
    void should_return_error_envelope_when_passkey_invalid() throws Exception {
      when(webAuthnService.verifyPasskeyAuthentication(
              anyString(), anyString(), anyString(), anyString(), anyString()))
          .thenThrow(new IllegalArgumentException("signature invalid"));

      // IllegalArgumentException 由全局 advice 映射为 HTTP 400（非法参数异常）
      adviceMockMvc.perform(post(BASE + "/passkey/verify")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"challenge\":\"challenge-auth\",\"credentialId\":\"cred-1\","
                  + "\"clientDataJSON\":\"cd\",\"authenticatorData\":\"ad\",\"signature\":\"sig\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").exists());
    }
  }

  @Nested
  @DisplayName("POST /webauthn/registration/verify")
  class VerifyRegistration {

    @Test
    @DisplayName("注册凭证验证并存储成功")
    void should_store_credential() throws Exception {
      mockMvc.perform(post(BASE + "/registration/verify")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"challenge\":\"ch\",\"credentialId\":\"cred-1\","
                  + "\"publicKey\":\"pk\",\"aaguid\":\"aaguid-1\",\"clientDataJSON\":\"cd\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      ArgumentCaptor<WebAuthnRegisterCommand> captor =
          ArgumentCaptor.forClass(WebAuthnRegisterCommand.class);
      verify(webAuthnService).verifyAndStoreCredential(captor.capture());
      assertThat(captor.getValue().userId()).isEqualTo("user-001");
      assertThat(captor.getValue().credentialId()).isEqualTo("cred-1");
      assertThat(captor.getValue().publicKey()).isEqualTo("pk");
    }
  }

  @Nested
  @DisplayName("GET /webauthn/authentication/options")
  class AuthenticationOptions {

    @Test
    @DisplayName("按 userId 生成认证选项")
    void should_return_authentication_options() throws Exception {
      Map<String, Object> options = new HashMap<>();
      options.put("challenge", "challenge-3");
      when(webAuthnService.generateAuthenticationOptions("user-001")).thenReturn(options);

      mockMvc.perform(get(BASE + "/authentication/options").param("userId", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.challenge").value("challenge-3"));
    }
  }

  @Nested
  @DisplayName("POST /webauthn/authentication/verify")
  class VerifyAuthentication {

    @Test
    @DisplayName("认证断言验签通过：签发 token")
    void should_issue_tokens_when_assertion_valid() throws Exception {
      when(webAuthnService.verifyAuthenticationResponse(
              eq("challenge-3"), eq("cred-1"), eq("cd"), eq("ad"), eq("sig")))
          .thenReturn("user-001");
      when(webAuthnService.findUserById("user-001")).thenReturn(account());
      when(roleCacheService.loadUserRoles("user-001")).thenReturn(List.of(role()));
      givenTokenPair();

      mockMvc.perform(post(BASE + "/authentication/verify")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"challenge\":\"challenge-3\",\"credentialId\":\"cred-1\","
                  + "\"clientDataJSON\":\"cd\",\"authenticatorData\":\"ad\",\"signature\":\"sig\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.accessToken").value("access-token"))
          .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("反例：验签抛错 → HTTP 500 错误信封")
    void should_return_error_envelope_when_signature_invalid() throws Exception {
      when(webAuthnService.verifyAuthenticationResponse(
              anyString(), anyString(), anyString(), anyString(), anyString()))
          .thenThrow(new IllegalArgumentException("signature invalid"));

      // IllegalArgumentException 由全局 advice 映射为 HTTP 400（非法参数异常）
      adviceMockMvc.perform(post(BASE + "/authentication/verify")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"challenge\":\"challenge-3\",\"credentialId\":\"cred-1\","
                  + "\"clientDataJSON\":\"cd\",\"authenticatorData\":\"ad\",\"signature\":\"sig\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").exists());
    }
  }

  @Nested
  @DisplayName("GET /webauthn/credentials")
  class ListCredentials {

    @Test
    @DisplayName("返回当前用户的 Passkey 列表")
    void should_list_credentials() throws Exception {
      WebAuthnCredentialVO credential = new WebAuthnCredentialVO();
      credential.setCredentialId("cred-1");
      credential.setDisplayName("MacBook Touch ID");
      when(webAuthnService.listCredentials("user-001")).thenReturn(List.of(credential));

      mockMvc.perform(get(BASE + "/credentials"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].credentialId").value("cred-1"))
          .andExpect(jsonPath("$.data[0].displayName").value("MacBook Touch ID"));
    }
  }

  @Nested
  @DisplayName("DELETE /webauthn/credentials/{credentialId}")
  class DeleteCredential {

    @Test
    @DisplayName("删除指定 Passkey 成功")
    void should_delete_credential() throws Exception {
      mockMvc.perform(delete(BASE + "/credentials/{credentialId}", "cred-1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(webAuthnService).deleteCredential("user-001", "cred-1");
    }
  }
}
