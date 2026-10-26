package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.server.auth.CaptchaService;

/**
 * {@link CaptchaController} Smoke Test。
 *
 * <p>验证验证码生成端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class CaptchaControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private CaptchaService captchaService;

  @InjectMocks
  private CaptchaController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /captcha/generate")
  class Generate {

    @Test
    @DisplayName("should return 200 when generate succeeds")
    void should_return_200_when_generate_succeeds() throws Exception {
      // captchaKey 由 Controller 内部 UUID.randomUUID() 生成，无法预先声明固定值
      when(captchaService.generateCaptcha(anyString())).thenReturn("base64img");

      mockMvc.perform(get("/captcha/generate"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.image").value("base64img"));
    }
  }
}
