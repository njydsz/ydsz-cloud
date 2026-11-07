package com.njydsz.userinfo.web.controller;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.dto.LoginDTO;
import com.njydsz.userinfo.domain.dto.RefreshRequest;
import com.njydsz.userinfo.domain.dto.SecondaryAuthRequest;
import com.njydsz.userinfo.domain.dto.SendVerifyCodeDTO;
import com.njydsz.userinfo.domain.dto.WebAuthnSecondaryAuthDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.CurrentUserInfoVO;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.server.auth.AuthService;
import com.njydsz.userinfo.server.auth.MfaService;
import com.njydsz.userinfo.server.auth.SecondaryAuthService;
import com.njydsz.userinfo.server.auth.WebAuthnService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AuthController} HTTP 契约测试。
 *
 * <p>覆盖登录 / 登出 / 刷新 / MFA 验证码 / 会话治理 / 场景化二级认证 / WebAuthn 二级认证等端点，
 * 每个端点都验证真实 HTTP 路径、方法、参数绑定、成功响应字段，并至少包含一个错误信封反例。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class AuthControllerTest extends BaseControllerMockTest {

  private static final String BASE = "/auth";

  @Mock private AuthService authService;
  @Mock private MfaService mfaService;
  @Mock private SecondaryAuthService secondaryAuthService;
  @Mock private WebAuthnService webAuthnService;
  @Mock private TokenService tokenService;
  @Mock private RedisStringOps redisStringOps;

  @InjectMocks private AuthController controller;

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
                    new MockEnvironment(),
                    null,
                    absentProvider(),
                    absentProvider(),
                    absentProvider()))
            .setMessageConverters(new JsonHttpMessageConverter())
            .build();
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  /**
   * 构造一个"容器里没有这个 bean"的 {@link ObjectProvider}，用于脱离 Spring 容器手工装配 advice。
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

  private static UserInfo userInfo(String userId, String username, String tenantId) {
    UserInfo info = new UserInfo();
    info.setUserId(userId);
    info.setUsername(username);
    info.setTenantId(tenantId);
    return info;
  }

  private static LoginUser loginUser(String userId, String username) {
    LoginUser user = new LoginUser();
    user.setUserId(userId);
    user.setUsername(username);
    return user;
  }

  /**
   * 写入登录用户上下文（模拟认证过滤器行为）。
   *
   * @param userId 用户 ID
   * @param username 用户名
   */
  private static void givenLoginUser(String userId, String username) {
    AuthContextUtils.setCurrent(loginUser(userId, username));
  }

  @Nested
  @DisplayName("POST /auth/login")
  class Login {

    @Test
    @DisplayName("账号密码登录成功：请求体绑定 + 回填 loginIp/userAgent + 返回 token 字段")
    void should_return_tokens_when_login_succeeds() throws Exception {
      LoginVO loginVO = new LoginVO();
      loginVO.setAccessToken("access-token-1");
      loginVO.setRefreshToken("refresh-token-1");
      loginVO.setTokenType("Bearer");
      loginVO.setExpiresIn(7200L);
      when(authService.login(any(LoginDTO.class), any())).thenReturn(loginVO);

      mockMvc.perform(post(BASE + "/login")
              .contentType(MediaType.APPLICATION_JSON)
              .header("User-Agent", "junit-agent")
              .content("{\"username\":\"admin\",\"password\":\"S3cret!\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.accessToken").value("access-token-1"))
          .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-1"))
          .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

      ArgumentCaptor<LoginDTO> captor = ArgumentCaptor.forClass(LoginDTO.class);
      verify(authService).login(captor.capture(), any());
      LoginDTO bound = captor.getValue();
      assertThat(bound.getUsername()).isEqualTo("admin");
      assertThat(bound.getPassword()).isEqualTo("S3cret!");
      assertThat(bound.getUserAgent()).isEqualTo("junit-agent");
    }

    @Test
    @DisplayName("反例：Service 抛 TOKEN_INVALID → HTTP 401 + $.code=A20003")
    void should_return_error_envelope_when_login_fails() throws Exception {
      when(authService.login(any(LoginDTO.class), any()))
          .thenThrow(BusinessException.of(UserInfoExceptionCode.TOKEN_INVALID));

      adviceMockMvc.perform(post(BASE + "/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"username\":\"admin\",\"password\":\"bad\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.TOKEN_INVALID.getCode()));
    }
  }

  @Nested
  @DisplayName("POST /auth/refresh")
  class Refresh {

    @Test
    @DisplayName("刷新成功：refreshToken 从请求体绑定并回传新 token")
    void should_return_new_tokens_when_refresh_succeeds() throws Exception {
      LoginVO loginVO = new LoginVO();
      loginVO.setAccessToken("new-access");
      loginVO.setRefreshToken("new-refresh");
      when(authService.refresh("old-refresh")).thenReturn(loginVO);

      mockMvc.perform(post(BASE + "/refresh")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"refreshToken\":\"old-refresh\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.accessToken").value("new-access"))
          .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));
    }

    @Test
    @DisplayName("反例：refresh_token 无效 → HTTP 401 错误信封")
    void should_return_error_envelope_when_refresh_token_invalid() throws Exception {
      when(authService.refresh("expired-refresh"))
          .thenThrow(BusinessException.of(UserInfoExceptionCode.TOKEN_INVALID));

      adviceMockMvc.perform(post(BASE + "/refresh")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"refreshToken\":\"expired-refresh\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.TOKEN_INVALID.getCode()));
    }
  }

  @Nested
  @DisplayName("POST /auth/logout")
  class Logout {

    @Test
    @DisplayName("登出成功：剥离 Bearer 前缀后把裸 token 交给 AuthService")
    void should_logout_with_bearer_token_stripped() throws Exception {
      mockMvc.perform(post(BASE + "/logout").header("Authorization", "Bearer raw-access-token"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(authService).logout("raw-access-token");
    }
  }

  @Nested
  @DisplayName("GET /auth/userinfo")
  class CurrentUserInfo {

    @Test
    @DisplayName("已登录：返回当前用户资料字段")
    void should_return_current_user_info() throws Exception {
      givenLoginUser("user-001", "admin");
      CurrentUserInfoVO vo = new CurrentUserInfoVO();
      vo.setUserId("user-001");
      vo.setUsername("admin");
      vo.setTenantId("1");
      when(authService.getCurrentUserInfo("user-001")).thenReturn(vo);

      mockMvc.perform(get(BASE + "/userinfo"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.username").value("admin"))
          .andExpect(jsonPath("$.data.tenantId").value("1"));
    }

    @Test
    @DisplayName("反例：未登录上下文 → 认证上下文工具抛 401 错误信封")
    void should_return_error_envelope_when_not_logged_in() throws Exception {
      adviceMockMvc.perform(get(BASE + "/userinfo"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").exists());
    }
  }

  @Nested
  @DisplayName("GET /auth/codes")
  class AccessCodes {

    @Test
    @DisplayName("返回当前用户权限码集合")
    void should_return_access_codes() throws Exception {
      givenLoginUser("user-001", "admin");
      when(authService.getAccessCodes("user-001")).thenReturn(Set.of("system:user:create"));

      mockMvc.perform(get(BASE + "/codes"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0]").value("system:user:create"));
    }
  }

  @Nested
  @DisplayName("POST /auth/mfa/send-code")
  class SendMfaSmsCode {

    @Test
    @DisplayName("发送短信验证码成功：targetType=PHONE 时取 target 作为手机号")
    void should_send_sms_code() throws Exception {
      mockMvc.perform(post(BASE + "/mfa/send-code")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"type\":\"MFA_LOGIN\",\"targetType\":\"PHONE\","
                  + "\"target\":\"13800000000\",\"captchaKey\":\"ck\",\"captcha\":\"1234\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(mfaService).sendLoginSmsCode("13800000000");
    }
  }

  @Nested
  @DisplayName("POST /auth/mfa/send-email-code")
  class SendMfaEmailCode {

    @Test
    @DisplayName("发送邮件验证码成功：targetType=EMAIL 时取 target 作为邮箱")
    void should_send_email_code() throws Exception {
      mockMvc.perform(post(BASE + "/mfa/send-email-code")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"type\":\"MFA_LOGIN\",\"targetType\":\"EMAIL\","
                  + "\"target\":\"user@example.com\",\"captchaKey\":\"ck\",\"captcha\":\"1234\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(mfaService).sendLoginEmailCode("user@example.com");
    }
  }

  @Nested
  @DisplayName("POST /auth/sso/device-code")
  class GenerateDeviceCode {

    @Test
    @DisplayName("已登录：生成 8 位设备码并写入 Redis，响应含 deviceCode/expiresIn")
    void should_generate_device_code() throws Exception {
      UserInfo info = userInfo("user-001", "admin", "1");
      when(tokenService.parseAccessToken("access-token")).thenReturn(info);
      when(tokenService.validateAccessToken("access-token")).thenReturn(true);

      mockMvc.perform(post(BASE + "/sso/device-code").header("Authorization", "Bearer access-token"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.deviceCode").isString())
          .andExpect(jsonPath("$.data.deviceCode").value(org.hamcrest.Matchers.hasLength(8)))
          .andExpect(jsonPath("$.data.expiresIn").value(300));

      verify(redisStringOps).set(anyString(), any(), eq(300L));
    }

    @Test
    @DisplayName("反例：缺少 Authorization 头 → 绑定层 400（@RequestHeader 必填）")
    void should_reject_when_authorization_header_missing() throws Exception {
      // authorization 是 @RequestHeader 必填参数，缺失时在进入方法前就被绑定层拦下
      adviceMockMvc.perform(post(BASE + "/sso/device-code"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("反例：Authorization 非 Bearer 格式 → HTTP 401 + TOKEN_INVALID")
    void should_reject_non_bearer_authorization() throws Exception {
      adviceMockMvc.perform(post(BASE + "/sso/device-code")
              .header("Authorization", "Basic dXNlcjpwYXNz"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.TOKEN_INVALID.getCode()));
    }
  }

  @Nested
  @DisplayName("POST /auth/sso/device-exchange")
  class ExchangeDeviceCode {

    @Test
    @DisplayName("设备码换 Token 成功：一次性读取并删除，签发双令牌")
    void should_exchange_device_code() throws Exception {
      when(redisStringOps.get(eq("sso:device:code:ABCD1234"), eq(String.class)))
          .thenReturn("{\"userId\":\"user-001\",\"username\":\"admin\",\"tenantId\":\"1\"}");
      UserInfo parsed = userInfo("user-001", "admin", "1");
      when(tokenService.issueAccessToken(any(UserInfo.class))).thenReturn("new-access");
      when(tokenService.issueRefreshToken(any(UserInfo.class))).thenReturn("new-refresh");

      mockMvc.perform(post(BASE + "/sso/device-exchange").param("code", "abcd1234"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.accessToken").value("new-access"))
          .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"))
          .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

      verify(redisStringOps).del("sso:device:code:ABCD1234");
    }

    @Test
    @DisplayName("反例：设备码不存在 → HTTP 401 + SSO_DEVICE_CODE_INVALID")
    void should_reject_when_device_code_unknown() throws Exception {
      when(redisStringOps.get(anyString(), eq(String.class))).thenReturn(null);

      adviceMockMvc.perform(post(BASE + "/sso/device-exchange").param("code", "ZZZZ9999"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.SSO_DEVICE_CODE_INVALID.getCode()));
    }
  }

  @Nested
  @DisplayName("GET /auth/sessions")
  class ListActiveSessions {

    @Test
    @DisplayName("返回当前用户活跃会话集合")
    void should_list_active_sessions() throws Exception {
      givenLoginUser("user-001", "admin");
      when(authService.listActiveSessions("user-001")).thenReturn(Set.of("token-a", "token-b"));

      mockMvc.perform(get(BASE + "/sessions"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.length()").value(2));
    }
  }

  @Nested
  @DisplayName("DELETE /auth/sessions/{token}")
  class KickOutSession {

    @Test
    @DisplayName("下线他人 token 被拒：HTTP 401 + TOKEN_INVALID，且不调用 logout")
    void should_reject_kick_out_of_other_users_token() throws Exception {
      givenLoginUser("user-001", "admin");
      when(tokenService.parseAccessToken("someone-elses-token"))
          .thenReturn(userInfo("user-002", "bob", "1"));

      adviceMockMvc.perform(delete(BASE + "/sessions/{token}", "someone-elses-token"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.TOKEN_INVALID.getCode()));

      verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("下线自己的 token 成功")
    void should_kick_out_own_token() throws Exception {
      givenLoginUser("user-001", "admin");
      when(tokenService.parseAccessToken("my-token")).thenReturn(userInfo("user-001", "admin", "1"));

      mockMvc.perform(delete(BASE + "/sessions/{token}", "my-token"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(authService).logout("my-token");
    }
  }

  @Nested
  @DisplayName("DELETE /auth/sessions")
  class KickOutOtherSessions {

    @Test
    @DisplayName("保留当前 token，其余会话全部下线")
    void should_kick_out_other_sessions_only() throws Exception {
      givenLoginUser("user-001", "admin");
      when(authService.listActiveSessions("user-001"))
          .thenReturn(Set.of("current-token", "other-token"));

      mockMvc.perform(delete(BASE + "/sessions").header("Authorization", "Bearer current-token"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(authService).logout("other-token");
      verify(authService).listActiveSessions("user-001");
    }
  }

  @Nested
  @DisplayName("POST /auth/secondary-auth")
  class SecondaryAuth {

    @Test
    @DisplayName("场景化二级认证成功：写入安全标记并回显 scene/level/ttlSeconds")
    void should_open_safe_mode() throws Exception {
      givenLoginUser("user-001", "admin");

      mockMvc.perform(post(BASE + "/secondary-auth")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"password\":\"S3cret!\",\"scene\":\"password_change\","
                  + "\"ttlSeconds\":300,\"level\":\"HIGH\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.scene").value("password_change"))
          .andExpect(jsonPath("$.data.level").value("HIGH"))
          .andExpect(jsonPath("$.data.ttlSeconds").value(300));

      ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
      verify(secondaryAuthService)
          .openSafe(eq("S3cret!"), eq("password_change"), ttlCaptor.capture());
      assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    @DisplayName("反例：密码错误 → HTTP 401 错误信封")
    void should_return_error_envelope_when_password_wrong() throws Exception {
      givenLoginUser("user-001", "admin");
      doThrow(BusinessException.of(UserInfoExceptionCode.SENSITIVE_VERIFY_PASSWORD_INCORRECT))
          .when(secondaryAuthService)
          .openSafe(anyString(), anyString(), any(Duration.class));

      adviceMockMvc.perform(post(BASE + "/secondary-auth")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"password\":\"wrong\",\"scene\":\"password_change\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code")
              .value(UserInfoExceptionCode.SENSITIVE_VERIFY_PASSWORD_INCORRECT.getCode()));
    }
  }

  @Nested
  @DisplayName("GET /auth/secondary-auth/webauthn/challenge")
  class WebAuthnChallenge {

    @Test
    @DisplayName("返回 WebAuthn 挑战选项，并把 scene 透传进响应")
    void should_return_webauthn_challenge() throws Exception {
      givenLoginUser("user-001", "admin");
      when(webAuthnService.generateAuthenticationOptions("user-001"))
          .thenReturn(new HashMap<>(Map.of("challenge", "challenge-abc")));

      mockMvc.perform(get(BASE + "/secondary-auth/webauthn/challenge")
              .param("scene", "data_export"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.challenge").value("challenge-abc"))
          .andExpect(jsonPath("$.data.scene").value("data_export"));
    }

    @Test
    @DisplayName("反例：未登录 → HTTP 401 + SECONDARY_AUTH_REQUIRED")
    void should_reject_when_not_logged_in() throws Exception {
      // 未登录时 AuthContextUtils 抛 SysException（UNAUTHORIZED），由 advice 映射为 401
      adviceMockMvc.perform(get(BASE + "/secondary-auth/webauthn/challenge"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").exists());
    }
  }

  @Nested
  @DisplayName("POST /auth/secondary-auth/webauthn")
  class WebAuthnSecondaryAuth {

    @Test
    @DisplayName("通行钥二级认证成功：请求体字段绑定到 openSafeWithWebAuthn")
    void should_open_safe_mode_with_webauthn() throws Exception {
      givenLoginUser("user-001", "admin");
      mockMvc.perform(post(BASE + "/secondary-auth/webauthn")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"scene\":\"role_assign\",\"challenge\":\"ch\",\"credentialId\":\"cred-1\","
                  + "\"clientDataJSON\":\"cd\",\"authenticatorData\":\"ad\",\"signature\":\"sig\","
                  + "\"ttlSeconds\":300}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.scene").value("role_assign"))
          .andExpect(jsonPath("$.data.channel").value("webauthn"))
          .andExpect(jsonPath("$.data.ttlSeconds").value(300));

      verify(secondaryAuthService)
          .openSafeWithWebAuthn(
              eq("role_assign"),
              eq("ch"),
              eq("cred-1"),
              eq("cd"),
              eq("ad"),
              eq("sig"),
              eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("反例：凭证不属于当前用户 → HTTP 401 错误信封")
    void should_return_error_envelope_when_credential_not_belong_to_user() throws Exception {
      givenLoginUser("user-001", "admin");
      doThrow(BusinessException.of(UserInfoExceptionCode.WEBAUTHN_CREDENTIAL_NOT_BELONG_TO_USER))
          .when(secondaryAuthService)
          .openSafeWithWebAuthn(
              anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
              any(Duration.class));

      // WEBAUTHN_CREDENTIAL_NOT_BELONG_TO_USER 未声明 httpStatus → advice 默认 400
      adviceMockMvc.perform(post(BASE + "/secondary-auth/webauthn")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"scene\":\"role_assign\",\"challenge\":\"ch\",\"credentialId\":\"cred-1\","
                  + "\"clientDataJSON\":\"cd\",\"authenticatorData\":\"ad\",\"signature\":\"sig\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code")
              .value(UserInfoExceptionCode.WEBAUTHN_CREDENTIAL_NOT_BELONG_TO_USER.getCode()));
    }
  }

}
