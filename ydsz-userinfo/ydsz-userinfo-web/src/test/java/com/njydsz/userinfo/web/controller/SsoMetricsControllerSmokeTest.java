package com.njydsz.userinfo.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

  @org.mockito.Mock
  private RedisStringOps redisStringOps;

  @org.mockito.InjectMocks
  private SsoMetricsController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(redisStringOps.get(org.mockito.ArgumentMatchers.anyString(), Class.class)).thenReturn(null);
  }

  @Nested
  @DisplayName("GET /sso/metrics/overview")
  class GetOverview {

    @Test
    @DisplayName("should return 200 when overview query succeeds")
    void should_return_200_when_overview_query_succeeds() throws Exception {
      mockMvc.perform(get("/sso/metrics/overview"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
