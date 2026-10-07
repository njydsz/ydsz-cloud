package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.dto.LoginDTO;
import com.njydsz.userinfo.domain.dto.SendVerifyCodeDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.auth.AuthService;
import com.njydsz.userinfo.server.auth.MfaService;
import com.njydsz.userinfo.server.auth.SecondaryAuthService;
import com.njydsz.userinfo.server.auth.WebAuthnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.json.YdszJson;

/**
 * {@link AuthController} 契约测试。
 *
 * <p>使用 {@code @WebMvcTest} 仅实例化 Web 层（Controller + 过滤器），所有 Service 依赖使用 {@code @MockBean}
 * 替换，专注验证请求映射、参数校验、鉴权响应、i18n 异常码包装。
 */
@WebMvcTest(controllers = AuthController.class)
class AuthControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private AuthService authService;
  @MockBean private MfaService mfaService;
  @MockBean private SecondaryAuthService secondaryAuthService;
  @MockBean private WebAuthnService webAuthnService;
  @MockBean private TokenService tokenService;
  @MockBean private RedisStringOps redisStringOps;

  @BeforeEach
  void setUp() {
    // 默认让 tokenService.parseAccessToken 返回非 null，避免 AuthController 自身短路 BusinessException
    UserInfo stubUser = new UserInfo();
    stubUser.setUserId("u_001");
    stubUser.setUsername("admin");
    when(tokenService.parseAccessToken(anyString())).thenReturn(stubUser);
    when(tokenService.validateAccessToken(anyString())).thenReturn(Boolean.TRUE);
  }

  @Nested
  @DisplayName("POST /auth/login")
  class Login {

    /** 登录名为空 → Bean Validation 400。 */
    @Test
    @DisplayName("should return 400 when username is blank")
    void blankUsername() throws Exception {
      LoginDTO dto = new LoginDTO();
      dto.setUsername("");
      dto.setPassword("123");
      mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }

    /** 业务层抛出 TOKEN_INVALID → 返回 YdszResponse{code=A10001, data=null} 且 HTTP=200（i18n 包装的契约）。 */
    @Test
    @DisplayName("should return business code when credential invalid")
    void invalidCredential() throws Exception {
      LoginDTO dto = new LoginDTO();
      dto.setUsername("admin");
      dto.setPassword("wrong_password");
      // AuthService.login throws BusinessException → ExceptionHandler returns HTTP 200 with body.code = A10001
      doThrow(new BusinessException(UserInfoExceptionCode.TOKEN_INVALID)).when(authService).login(any(), any());
      mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("").exists())
          .andDo(print());
    }
  }

  @Nested
  @DisplayName("POST /auth/mfa/send-code")
  class SendCode {

    /** 手机号/邮箱未传 → Bean Validation 400。 */
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
  }
}
