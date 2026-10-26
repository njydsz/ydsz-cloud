package com.njydsz.userinfo.web.controller;

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

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link SsoMetricsController} Smoke Test。
 *
 * <p>验证 SSO 指标查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class SsoMetricsControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private RedisStringOps redisStringOps;

  @InjectMocks
  private SsoMetricsController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /sso/metrics/overview")
  class GetOverview {

    @Test
    @DisplayName("should return 200 when overview query succeeds")
    void should_return_200_when_overview_query_succeeds() throws Exception {
      when(redisStringOps.get("userinfo:cas:tgt:active:count", String.class)).thenReturn("7");
      when(redisStringOps.get("userinfo:cas:st:active:count", String.class)).thenReturn("3");
      when(redisStringOps.get("userinfo:oauth2:token:active:count", String.class)).thenReturn("5");

      mockMvc.perform(get("/sso/metrics/overview"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.casActiveTgt").value(7))
          .andExpect(jsonPath("$.data.casActiveSt").value(3))
          .andExpect(jsonPath("$.data.oauth2ActiveTokens").value(5));
    }
  }
}
