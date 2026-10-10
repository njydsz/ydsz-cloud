package com.njydsz.system.web.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.lock.spi.CurrentUserIdResolver;
import com.njydsz.common.safe.idempotent.strategy.RepeatSubmitTokenService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link RepeatSubmitTokenController} Smoke Test。
 *
 * <p>验证防重复提交 Token 获取端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RepeatSubmitTokenControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RepeatSubmitTokenService tokenService;

  @org.mockito.Mock
  private CurrentUserIdResolver userIdResolver;

  @org.mockito.InjectMocks
  private RepeatSubmitTokenController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /repeat-submit/token")
  class GetToken {

    @Test
    @DisplayName("should return 200 when token generation succeeds")
    void should_return_200_when_token_generation_succeeds() throws Exception {
      when(userIdResolver.getCurrentUserId()).thenReturn("user-001");
      when(tokenService.generateToken(anyString(), anyLong())).thenReturn("token-abc123");

      mockMvc.perform(get("/repeat-submit/token").param("ttlMillis", "60000"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
