package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.dto.LoginDTO;
import com.njydsz.userinfo.domain.dto.SendVerifyCodeDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.auth.AuthService;
import com.njydsz.userinfo.server.auth.MfaService;
import com.njydsz.userinfo.server.auth.SecondaryAuthService;
import com.njydsz.userinfo.server.auth.WebAuthnService;

/**
 * {@link AuthController} 契约测试（WebMvcTest + MockitoBean）。
 *
 * <h3>Boot 4.x 迁移说明</h3>
 * <ul>
 *   <li>{@code @WebMvcTest} 由 {@code spring-boot-webmvc-test} 独立制品提供 ({@code org.springframework.boot.webmvc.test.autoconfigure})</li>
 *   <li>{@code @MockBean} 已被废弃，由 {@code org.springframework.test.context.bean.override.mockito.MockitoBean} 替代</li>
 *   <li>Gateway Token 解析未在 Web 层配置，因此用例聚焦 Bean Validation / 业务异常码路径</li>
 * </ul>
 */
@WebMvcTest(controllers = AuthController.class)
class AuthControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private MfaService mfaService;
  @MockitoBean private SecondaryAuthService secondaryAuthService;
  @MockitoBean private WebAuthnService webAuthnService;
  @MockitoBean private TokenService tokenService;
  @MockitoBean private RedisStringOps redisStringOps;

  @BeforeEach
  void setUp() {
    // 给所有 token 解析调用兜底返回有效 UserInfo，避免 Boot 容器注入失败
    UserInfo stubUser = new UserInfo();
    stubUser.setUserId("u_001");
    stubUser.setUsername("admin");
    when(tokenService.parseAccessToken(anyString())).thenReturn(stubUser);
    when(tokenService.validateAccessToken(anyString())).thenReturn(Boolean.TRUE);
  }

  @Nested
  @DisplayName("POST /auth/login")
  class Login {

    /**
     * 登录名为空 → Bean Validation 触发 HTTP 400。
     *
     * <p>校验注解 {@code @NotBlank} 应用在 {@link LoginDTO#getUsername()} 上。
     */
    @Test
    @DisplayName("should return 400 when username is blank")
    void blankUsername() throws Exception {
      LoginDTO dto = new LoginDTO();
      dto.setUsername("");
      dto.setPassword("12345678");
      mvc.perform(
              post("/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }

    /**
     * 业务层抛出 {@link UserInfoExceptionCode#TOKEN_INVALID}（HTTP 401），
     * 由 {@code UserinfoExceptionHandler} 写回 200 + 错误码包装体。
     */
    @Test
    @DisplayName("should return business code envelope when credential invalid")
    void invalidCredential() throws Exception {
      LoginDTO dto = new LoginDTO();
      dto.setUsername("admin");
      dto.setPassword("wrong_password");
      doThrow(new BusinessException(UserInfoExceptionCode.TOKEN_INVALID))
          .when(authService)
          .login(any(), any());
      mvc.perform(
              post("/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(YdszJson.toJson(dto)))
          .andExpect(status().isOk())
          .andDo(print());
    }
  }

  @Nested
  @DisplayName("POST /auth/mfa/send-code")
  class SendCode {

    /** target 未传 → @NotBlank 校验 → HTTP 400。 */
    @Test
    @DisplayName("should return 400 when target is blank")
    void blankTarget() throws Exception {
      SendVerifyCodeDTO dto = new SendVerifyCodeDTO();
      dto.setType("FORGOT_PASSWORD");
      dto.setTargetType("PHONE");
      dto.setTarget("");
      mvc.perform(
              post("/auth/mfa/send-code")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }

    /** captchaKey / captcha 同时缺失 → HTTP 400（P0-5 防短信轰炸字段）。 */
    @Test
    @DisplayName("should return 400 when captchaKey missing")
    void missingCaptchaKey() throws Exception {
      SendVerifyCodeDTO dto = new SendVerifyCodeDTO();
      dto.setType("FORGOT_PASSWORD");
      dto.setTargetType("PHONE");
      dto.setTarget("13800000000");
      // captchaKey / captcha are @NotBlank
      mvc.perform(
              post("/auth/mfa/send-code")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }
  }
}
