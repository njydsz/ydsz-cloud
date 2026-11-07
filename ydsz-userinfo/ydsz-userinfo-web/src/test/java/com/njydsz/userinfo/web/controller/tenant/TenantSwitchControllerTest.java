package com.njydsz.userinfo.web.controller.tenant;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link TenantSwitchController} HTTP 契约测试（多租户 JWT 切换）。
 *
 * <p>覆盖 {@code POST /auth/tenant/switch}：真实路径、请求体绑定、成功响应字段
 * （accessToken / refreshToken / tokenType / targetTenantId），以及
 * 缺少 Authorization 头、token 无效、目标租户不可访问、目标租户为空等错误信封反例。
 *
 * <p><b>⚠️ 生产缺陷记录（accessible_tenants 校验不可达）：</b>
 * {@code validateAccessibleTenant} 判断的是 {@code extras} <b>整体</b>是否为
 * {@code List} / {@code String[]}，但 {@code UserInfo.extras} 声明为
 * {@code Map<String, Object>}，且 JWT 解析路径从不写入该字段。因此这两个分支
 * 在真实链路下<b>永远不可达</b>——任何跨租户切换都会落入 else 分支并抛
 * {@code SSO_DOMAIN_NOT_TRUSTED}，只有「切换到当前租户」能成功。
 * 本测试只断言当前真实可达的行为，不引入与生产不一致的假设。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class TenantSwitchControllerTest extends BaseControllerMockTest {

  private static final String ENDPOINT = "/auth/tenant/switch";

  @Mock private TokenService tokenService;
  @Mock private RedisStringOps redisStringOps;

  private MockMvc mockMvc;
  private MockMvc adviceMockMvc;
  private TenantSwitchController controller;

  @BeforeEach
  void setUp() {
    controller = new TenantSwitchController(tokenService, redisStringOps);
    mockMvc = standaloneSetup(controller);
    // 错误信封需要 advice + 项目统一 converter
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
   * 构造带 jti 侧车的 UserInfo（模拟 JWT extras 形态）。
   *
   * @return UserInfo
   */
  private static UserInfo userInfoWithJti() {
    UserInfo info = new UserInfo();
    info.setUserId("user-001");
    info.setUsername("admin");
    info.setTenantId("1");
    Map<String, Object> extras = new HashMap<>();
    extras.put("jti", "jti-1");
    info.setExtras(extras);
    return info;
  }

  private void givenValidToken(String token, UserInfo info) {
    when(tokenService.parseAccessToken(token)).thenReturn(info);
    when(tokenService.validateAccessToken(token)).thenReturn(true);
  }

  @Nested
  @DisplayName("POST /auth/tenant/switch")
  class SwitchTenant {

    @Test
    @DisplayName("成功路径：目标租户等于当前租户 → 签发新 token 对并吊销旧 token")
    void should_switch_to_current_tenant() throws Exception {
      String token = "current-access";
      givenValidToken(token, userInfoWithJti());
      when(tokenService.getAccessTokenRemainingTtl(token)).thenReturn(3600L);
      when(tokenService.issueAccessToken(any(UserInfo.class))).thenReturn("new-access");
      when(tokenService.issueRefreshToken(any(UserInfo.class))).thenReturn("new-refresh");

      mockMvc.perform(post(ENDPOINT)
              .header("Authorization", "Bearer " + token)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"targetTenantId\":\"1\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.accessToken").value("new-access"))
          .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"))
          .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
          .andExpect(jsonPath("$.data.targetTenantId").value("1"));

      // extras 含 jti → 旧 token 写入黑名单，TTL 取剩余有效期
      verify(redisStringOps).set(eq("auth:token:blacklist:jti-1"), eq("1"), eq(3600L));
    }

    @Test
    @DisplayName("反例：缺少 Authorization 头 → HTTP 401 + TOKEN_INVALID")
    void should_reject_missing_authorization() throws Exception {
      adviceMockMvc.perform(post(ENDPOINT)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"targetTenantId\":\"2\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.TOKEN_INVALID.getCode()));
    }

    @Test
    @DisplayName("反例：Authorization 非 Bearer 格式 → HTTP 401 + TOKEN_INVALID")
    void should_reject_non_bearer_authorization() throws Exception {
      adviceMockMvc.perform(post(ENDPOINT)
              .header("Authorization", "Basic abc")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"targetTenantId\":\"2\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.TOKEN_INVALID.getCode()));
    }

    @Test
    @DisplayName("反例：token 无效 → HTTP 401 + TOKEN_INVALID")
    void should_reject_invalid_token() throws Exception {
      when(tokenService.parseAccessToken("bad-token")).thenReturn(null);

      adviceMockMvc.perform(post(ENDPOINT)
              .header("Authorization", "Bearer bad-token")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"targetTenantId\":\"2\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.TOKEN_INVALID.getCode()));
    }

    @Test
    @DisplayName("现状固定：extras 为 Map 时跨租户切换被拒（accessible_tenants 分支不可达）")
    void should_reject_cross_tenant_switch() throws Exception {
      String token = "current-access";
      givenValidToken(token, userInfoWithJti());

      adviceMockMvc.perform(post(ENDPOINT)
              .header("Authorization", "Bearer " + token)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"targetTenantId\":\"2\"}"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.SSO_DOMAIN_NOT_TRUSTED.getCode()));

      // 被拒时不得签发新 token，也不得写黑名单
      verify(tokenService, never()).issueAccessToken(any(UserInfo.class));
      verify(redisStringOps, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("反例：目标租户为空 → HTTP 400 + PARAM_INVALID")
    void should_reject_blank_target_tenant() throws Exception {
      String token = "current-access";
      givenValidToken(token, userInfoWithJti());

      adviceMockMvc.perform(post(ENDPOINT)
              .header("Authorization", "Bearer " + token)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"targetTenantId\":\"\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.PARAM_INVALID.getCode()));
    }
  }

}
