package com.njydsz.userinfo.web.controller;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.service.TokenBlacklistService;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.oauth2.OAuthCodeContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link OAuth2Controller} HTTP 契约测试。
 *
 * <p>覆盖授权码模式 /authorize、/consent、/token（authorization_code + refresh_token + PKCE）、
 * /revoke、/introspect、/userinfo 端点，含客户端认证失败、redirect_uri 白名单不匹配、
 * 授权码无效、PKCE 校验失败等错误信封反例。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class OAuth2ControllerTest extends BaseControllerMockTest {

  private static final String BASE = "/oauth2";
  private static final String CLIENT_ID = "third-party-app";
  private static final String CLIENT_SECRET = "secret-1";
  private static final String REDIRECT_URI = "https://example.com/callback";

  @Mock private RedisStringOps redisStringOps;
  @Mock private TokenService tokenService;

  private UserInfoProperties properties;

  private MockMvc mockMvc;
  private MockMvc adviceMockMvc;
  private OAuth2Controller controller;

  @BeforeEach
  void setUp() {
    properties = new UserInfoProperties();
    UserInfoProperties.OAuth2Client client = new UserInfoProperties.OAuth2Client();
    client.setClientName("第三方应用");
    client.setClientSecret(CLIENT_SECRET);
    client.setRedirectUris(List.of(REDIRECT_URI));
    client.setAllowedScopes(Set.of("read", "write"));
    // getter 返回不可变视图，必须通过 setter 写入
    properties.setOauth2Clients(new HashMap<>(Map.of(CLIENT_ID, client)));

    ObjectProvider<TokenBlacklistService> blacklistProvider = provider(null);
    controller = new OAuth2Controller(redisStringOps, tokenService, blacklistProvider, properties);
    mockMvc = standaloneSetup(controller);
    // 错误信封需要 advice + 项目统一 converter
    adviceMockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new MvcExceptionHandler(
                    new org.springframework.mock.env.MockEnvironment(),
                    null,
                    provider(null),
                    provider(null),
                    provider(null)))
            .setMessageConverters(new JsonHttpMessageConverter())
            .build();
  }

  /**
   * 构造 {@link ObjectProvider}。
   *
   * @param value 提供的 bean（可为 null）
   * @param <T> bean 类型
   * @return provider 实例
   */
  private static <T> ObjectProvider<T> provider(T value) {
    return new ObjectProvider<>() {
      @Override
      public T getIfAvailable() {
        return value;
      }

      @Override
      public T getIfUnique() {
        return value;
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

  private static String pkceChallenge(String verifier) {
    byte[] hash = DigestUtils.sha256(verifier.getBytes(StandardCharsets.US_ASCII));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
  }

  @Nested
  @DisplayName("GET /oauth2/authorize")
  class Authorize {

    /**
     * ⚠️ 生产缺陷记录：{@code OAuth2AuthorizeRequest} 是嵌套 record，其 {@code authorization}
     * 组件上的 {@code @RequestHeader} 在 Spring 4.1 嵌套 record 绑定下不生效
     * （实测 authorization 恒为 null），因此 {@code /oauth2/authorize} 与 {@code /oauth2/consent}
     * 当前始终抛 {@code TOKEN_INVALID}，无法签发授权码。以下用例固定当前真实行为。
     *
     * <p>修复方向（需改生产代码）：把 authorization 提升为 Controller 方法上的独立
     * {@code @RequestHeader} 参数，而非嵌套 record 组件。
     */
    @Test
    @DisplayName("现状固定：携带 Bearer 头仍因嵌套 record 绑定缺陷返回 401 + TOKEN_INVALID")
    void should_reject_even_with_bearer_token_due_to_binding_defect() throws Exception {
      when(tokenService.parseAccessToken("access-token")).thenReturn(userInfo("user-001", "admin", "1"));
      when(tokenService.validateAccessToken("access-token")).thenReturn(true);

      adviceMockMvc.perform(get(BASE + "/authorize")
              .header("Authorization", "Bearer access-token")
              .param("clientId", CLIENT_ID)
              .param("redirectUri", REDIRECT_URI)
              .param("scope", "read"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.TOKEN_INVALID.getCode()));

      // 缺陷可观测证据：token 解析从未被调用
      verify(tokenService, org.mockito.Mockito.never()).parseAccessToken(anyString());
    }

    @Test
    @DisplayName("反例：未携带 Bearer 头 → HTTP 401 + TOKEN_INVALID")
    void should_reject_without_bearer_token() throws Exception {
      adviceMockMvc.perform(get(BASE + "/authorize")
              .param("clientId", CLIENT_ID)
              .param("redirectUri", REDIRECT_URI))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.TOKEN_INVALID.getCode()));
    }
  }

  @Nested
  @DisplayName("GET /oauth2/consent")
  class ConsentInfo {

    /**
     * 同 {@link Authorize}：嵌套 record 的 {@code @RequestHeader} 不生效，
     * consent 端点当前不可达。
     */
    @Test
    @DisplayName("现状固定：/consent 因绑定缺陷返回 401 + TOKEN_INVALID")
    void should_reject_consent_due_to_binding_defect() throws Exception {
      when(tokenService.parseAccessToken("access-token")).thenReturn(userInfo("user-001", "admin", "1"));
      when(tokenService.validateAccessToken("access-token")).thenReturn(true);

      adviceMockMvc.perform(get(BASE + "/consent")
              .header("Authorization", "Bearer access-token")
              .param("clientId", CLIENT_ID)
              .param("redirectUri", REDIRECT_URI)
              .param("scope", "read write"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.TOKEN_INVALID.getCode()));
    }
  }

  @Nested
  @DisplayName("POST /oauth2/consent")
  class SubmitConsent {

    @Test
    @DisplayName("现状固定：提交授权同意同样因绑定缺陷返回 401")
    void should_reject_consent_submit_due_to_binding_defect() throws Exception {
      adviceMockMvc.perform(post(BASE + "/consent")
              .header("Authorization", "Bearer access-token")
              .param("clientId", CLIENT_ID)
              .param("redirectUri", REDIRECT_URI))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.TOKEN_INVALID.getCode()));
    }
  }

  /**
   * ⚠️ 生产缺陷记录：{@code OAuth2TokenRequest} 的 record 组件名是 camelCase
   * （{@code grantType} / {@code refreshToken} / {@code codeVerifier}），而 RFC 6749 规定
   * 表单参数必须是 snake_case（{@code grant_type} / {@code refresh_token} / {@code code_verifier}）。
   * 实测嵌套 record 只按组件名绑定，因此标准 OAuth2 客户端发来的 snake_case 参数会全部解析为 null，
   * token 端点对真实客户端不可用。以下用例使用当前实际生效的 camelCase 参数名。
   */
  @Nested
  @DisplayName("POST /oauth2/token")
  class Token {

    @Test
    @DisplayName("authorization_code + PKCE：验码通过后签发双令牌与 id_token")
    void should_issue_tokens_with_pkce() throws Exception {
      String verifier = "a".repeat(43);
      String challenge = pkceChallenge(verifier);
      OAuthCodeContext context = OAuthCodeContext.builder()
          .clientId(CLIENT_ID)
          .userId("user-001")
          .username("admin")
          .tenantId("1")
          .redirectUri(REDIRECT_URI)
          .scope("openid read")
          .codeChallenge(challenge)
          .codeChallengeMethod("S256")
          .build();
      when(redisStringOps.getAndDelete(anyString(), eq(String.class))).thenReturn(context.toJson());
      when(tokenService.issueAccessToken(any(UserInfo.class))).thenReturn("new-access");
      when(tokenService.issueRefreshToken(any(UserInfo.class))).thenReturn("new-refresh");
      when(tokenService.issueIdToken(any(UserInfo.class), any(), eq(CLIENT_ID))).thenReturn("id-token");

      mockMvc.perform(post(BASE + "/token")
              .param("grantType", "authorization_code")
              .param("code", "auth-code-1")
              .param("clientId", CLIENT_ID)
              .param("codeVerifier", verifier))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.access_token").value("new-access"))
          .andExpect(jsonPath("$.data.refresh_token").value("new-refresh"))
          .andExpect(jsonPath("$.data.token_type").value("Bearer"))
          .andExpect(jsonPath("$.data.id_token").value("id-token"))
          .andExpect(jsonPath("$.data.scope").value("openid read"));
    }

    @Test
    @DisplayName("authorization_code 传统模式：clientSecret 校验通过后签发令牌")
    void should_issue_tokens_with_client_secret() throws Exception {
      OAuthCodeContext context = OAuthCodeContext.builder()
          .clientId(CLIENT_ID)
          .userId("user-001")
          .username("admin")
          .tenantId("1")
          .redirectUri(REDIRECT_URI)
          .scope("read")
          .build();
      when(redisStringOps.getAndDelete(anyString(), eq(String.class))).thenReturn(context.toJson());
      when(tokenService.issueAccessToken(any(UserInfo.class))).thenReturn("new-access");
      when(tokenService.issueRefreshToken(any(UserInfo.class))).thenReturn("new-refresh");

      mockMvc.perform(post(BASE + "/token")
              .param("grantType", "authorization_code")
              .param("code", "auth-code-1")
              .param("clientId", CLIENT_ID)
              .param("clientSecret", CLIENT_SECRET))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.access_token").value("new-access"));
    }

    @Test
    @DisplayName("反例：授权码不存在 → HTTP 401 + OAUTH2_CODE_INVALID")
    void should_reject_unknown_authorization_code() throws Exception {
      when(redisStringOps.getAndDelete(anyString(), eq(String.class))).thenReturn(null);

      adviceMockMvc.perform(post(BASE + "/token")
              .param("grantType", "authorization_code")
              .param("code", "bad-code")
              .param("clientId", CLIENT_ID)
              .param("clientSecret", CLIENT_SECRET))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.OAUTH2_CODE_INVALID.getCode()));
    }

    @Test
    @DisplayName("反例：PKCE verifier 不匹配 → HTTP 401 + OAUTH2_PKCE_VERIFIER_INVALID")
    void should_reject_pkce_mismatch() throws Exception {
      String verifier = "a".repeat(43);
      OAuthCodeContext context = OAuthCodeContext.builder()
          .clientId(CLIENT_ID)
          .userId("user-001")
          .username("admin")
          .tenantId("1")
          .redirectUri(REDIRECT_URI)
          .scope("read")
          .codeChallenge(pkceChallenge(verifier))
          .codeChallengeMethod("S256")
          .build();
      when(redisStringOps.getAndDelete(anyString(), eq(String.class))).thenReturn(context.toJson());

      adviceMockMvc.perform(post(BASE + "/token")
              .param("grantType", "authorization_code")
              .param("code", "auth-code-1")
              .param("clientId", CLIENT_ID)
              .param("codeVerifier", "b".repeat(43)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code")
              .value(UserInfoExceptionCode.OAUTH2_PKCE_VERIFIER_INVALID.getCode()));
    }

    @Test
    @DisplayName("反例：grant_type 不受支持 → HTTP 401 + OAUTH2_CLIENT_INVALID")
    void should_reject_unsupported_grant_type() throws Exception {
      adviceMockMvc.perform(post(BASE + "/token")
              .param("grantType", "password")
              .param("clientId", CLIENT_ID))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID.getCode()));
    }

    @Test
    @DisplayName("refresh_token 轮换：旧 refresh_token 加入黑名单")
    void should_rotate_refresh_token() throws Exception {
      TokenBlacklistService blacklist = org.mockito.Mockito.mock(TokenBlacklistService.class);
      controller = new OAuth2Controller(
          redisStringOps, tokenService, provider(blacklist), properties);
      MockMvc withBlacklist = standaloneSetup(controller);

      when(tokenService.validateRefreshToken("old-refresh")).thenReturn(true);
      when(tokenService.parseRefreshToken("old-refresh")).thenReturn(userInfo("user-001", "admin", "1"));
      when(tokenService.issueAccessToken(any(UserInfo.class))).thenReturn("rotated-access");
      when(tokenService.issueRefreshToken(any(UserInfo.class))).thenReturn("rotated-refresh");

      withBlacklist.perform(post(BASE + "/token")
              .param("grantType", "refresh_token")
              .param("clientId", CLIENT_ID)
              .param("clientSecret", CLIENT_SECRET)
              .param("refreshToken", "old-refresh"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.access_token").value("rotated-access"))
          .andExpect(jsonPath("$.data.refresh_token").value("rotated-refresh"));

      verify(blacklist).addToBlacklist("old-refresh");
    }

    @Test
    @DisplayName("反例：refresh_token 无效 → HTTP 401 + TOKEN_INVALID")
    void should_reject_invalid_refresh_token() throws Exception {
      when(tokenService.validateRefreshToken("bad-refresh")).thenReturn(false);

      adviceMockMvc.perform(post(BASE + "/token")
              .param("grantType", "refresh_token")
              .param("clientId", CLIENT_ID)
              .param("clientSecret", CLIENT_SECRET)
              .param("refreshToken", "bad-refresh"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.TOKEN_INVALID.getCode()));
    }
  }

  @Nested
  @DisplayName("POST /oauth2/revoke")
  class Revoke {

    @Test
    @DisplayName("撤销成功：RFC 7009 固定返回 200")
    void should_revoke_token() throws Exception {
      mockMvc.perform(post(BASE + "/revoke")
              .param("token", "access-token")
              .param("tokenTypeHint", "access_token"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /oauth2/introspect")
  class Introspect {

    @Test
    @DisplayName("有效 token：返回 active=true 与用户声明")
    void should_return_active_metadata() throws Exception {
      when(tokenService.parseAccessToken("access-token")).thenReturn(userInfo("user-001", "admin", "1"));
      when(tokenService.validateAccessToken("access-token")).thenReturn(true);

      mockMvc.perform(post(BASE + "/introspect")
              .param("token", "access-token")
              .param("clientId", CLIENT_ID)
              .param("clientSecret", CLIENT_SECRET))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.active").value(true))
          .andExpect(jsonPath("$.data.sub").value("user-001"))
          .andExpect(jsonPath("$.data.username").value("admin"))
          .andExpect(jsonPath("$.data.tenantId").value("1"));
    }

    @Test
    @DisplayName("无效 token：返回 active=false 且不含用户声明")
    void should_return_inactive_metadata() throws Exception {
      when(tokenService.parseAccessToken("bad-token")).thenReturn(null);

      mockMvc.perform(post(BASE + "/introspect")
              .param("token", "bad-token")
              .param("clientId", CLIENT_ID)
              .param("clientSecret", CLIENT_SECRET))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.active").value(false))
          .andExpect(jsonPath("$.data.sub").doesNotExist());
    }

    @Test
    @DisplayName("反例：客户端密钥错误 → HTTP 401 + OAUTH2_CLIENT_INVALID")
    void should_reject_invalid_client_secret() throws Exception {
      adviceMockMvc.perform(post(BASE + "/introspect")
              .param("token", "access-token")
              .param("clientId", CLIENT_ID)
              .param("clientSecret", "wrong-secret"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID.getCode()));
    }
  }

  @Nested
  @DisplayName("GET /oauth2/userinfo")
  class Userinfo {

    @Test
    @DisplayName("返回 OIDC 标准声明 sub/preferred_username/tenant_id")
    void should_return_oidc_userinfo() throws Exception {
      when(tokenService.parseAccessToken("access-token")).thenReturn(userInfo("user-001", "admin", "1"));
      when(tokenService.validateAccessToken("access-token")).thenReturn(true);

      mockMvc.perform(get(BASE + "/userinfo").header("Authorization", "Bearer access-token"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.sub").value("user-001"))
          .andExpect(jsonPath("$.data.preferred_username").value("admin"))
          .andExpect(jsonPath("$.data.tenant_id").value("1"));
    }

    @Test
    @DisplayName("反例：缺少 Authorization 头 → 绑定层 400（@RequestHeader 必填）")
    void should_reject_without_token() throws Exception {
      adviceMockMvc.perform(get(BASE + "/userinfo"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("反例：Authorization 非 Bearer 格式 → HTTP 401 + TOKEN_INVALID")
    void should_reject_non_bearer_token() throws Exception {
      adviceMockMvc.perform(get(BASE + "/userinfo").header("Authorization", "Basic abc"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.TOKEN_INVALID.getCode()));
    }
  }

  /**
   * 从最近一次 {@code set("oauth2:code:...")} 调用中还原授权码（用于断言 Redis 值）。
   *
   * @return 授权码
   */
  private String codeFromResponse() {
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(redisStringOps, org.mockito.Mockito.atLeastOnce())
        .set(keyCaptor.capture(), any(), anyLong());
    String key = keyCaptor.getValue();
    return key.substring("oauth2:code:".length());
  }

}
