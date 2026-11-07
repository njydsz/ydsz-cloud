package com.njydsz.userinfo.web.controller.deviceauth;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.config.UserInfoProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link DeviceAuthorizationController} HTTP 契约测试（RFC 8628 设备授权码模式）。
 *
 * <p>覆盖 {@code POST /oauth2/device_authorization}：真实路径、参数绑定、成功响应字段
 * （device_code / user_code / verification_uri / expires_in / interval），
 * 以及 clientId 未注册、参数缺失的错误信封反例。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class DeviceAuthorizationControllerTest extends BaseControllerMockTest {

  private static final String ENDPOINT = "/oauth2/device_authorization";
  private static final String CLIENT_ID = "device-client";

  @Mock private RedisStringOps redisStringOps;

  private UserInfoProperties properties;
  private DeviceAuthorizationController controller;
  private MockMvc mockMvc;
  private MockMvc adviceMockMvc;

  @BeforeEach
  void setUp() {
    properties = new UserInfoProperties();
    UserInfoProperties.OAuth2Client client = new UserInfoProperties.OAuth2Client();
    client.setClientSecret("secret");
    // getter 返回不可变视图，必须通过 setter 写入
    properties.setOauth2Clients(Map.of(CLIENT_ID, client));

    controller = new DeviceAuthorizationController(redisStringOps, properties);
    mockMvc = standaloneSetup(controller);
    // 错误信封需要 advice + 项目统一 converter
    adviceMockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new MvcExceptionHandler(
                    new MockEnvironment(), null, absentProvider(), absentProvider(), absentProvider()))
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

  @Nested
  @DisplayName("POST /oauth2/device_authorization")
  class DeviceAuthorization {

    @Test
    @DisplayName("已注册客户端：返回 device_code / user_code 与轮询参数")
    void should_return_device_authorization_response() throws Exception {
      mockMvc.perform(post(ENDPOINT)
              .param("client_id", CLIENT_ID)
              .param("scope", "openid profile"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.deviceCode").isString())
          .andExpect(jsonPath("$.data.userCode")
              .value(org.hamcrest.Matchers.matchesPattern("[A-Z2-9]{4}-[A-Z2-9]{4}")))
          .andExpect(jsonPath("$.data.verificationUri").value("/device-login"))
          .andExpect(jsonPath("$.data.verificationUriComplete")
              .value(org.hamcrest.Matchers.startsWith("/device-login?user_code=")))
          .andExpect(jsonPath("$.data.expiresIn").value(900))
          .andExpect(jsonPath("$.data.interval").value(5));

      ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
      verify(redisStringOps, Mockito.atLeastOnce())
          .set(keyCaptor.capture(), anyString(), anyLong());
      assertThat(keyCaptor.getAllValues())
          .anySatisfy(key -> assertThat(key).startsWith("oauth2:device:code:"))
          .anySatisfy(key -> assertThat(key).startsWith("oauth2:device:usercode:"));
    }

    @Test
    @DisplayName("反例：client_id 未注册 → HTTP 401 + OAUTH2_CLIENT_INVALID")
    void should_reject_unregistered_client() throws Exception {
      adviceMockMvc.perform(post(ENDPOINT).param("client_id", "unknown-client"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID.getCode()));
    }

    @Test
    @DisplayName("反例：缺少 client_id 参数 → HTTP 400 参数缺失错误信封")
    void should_reject_missing_client_id() throws Exception {
      adviceMockMvc.perform(post(ENDPOINT))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").exists());
    }
  }

}
