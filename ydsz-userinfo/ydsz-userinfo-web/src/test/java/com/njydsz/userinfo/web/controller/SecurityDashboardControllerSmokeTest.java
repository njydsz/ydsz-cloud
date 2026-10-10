package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.server.auth.SecurityDashboardService;
import com.njydsz.userinfo.server.auth.SessionActivityService;

/**
 * {@link SecurityDashboardController} Smoke Test。
 *
 * <p>验证安全仪表盘查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class SecurityDashboardControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private SecurityDashboardService securityDashboardService;

  @org.mockito.Mock
  private SessionActivityService sessionActivityService;

  @org.mockito.InjectMocks
  private SecurityDashboardController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(securityDashboardService.getDashboard()).thenReturn(null);
    when(securityDashboardService.getLoginSuccessRate(any(), any())).thenReturn(List.of());
    when(sessionActivityService.getActivityOverview()).thenReturn(null);
  }

  @Nested
  @DisplayName("GET /admin/security/dashboard")
  class GetDashboard {

    @Test
    @DisplayName("should return 200 when dashboard query succeeds")
    void should_return_200_when_dashboard_query_succeeds() throws Exception {
      mockMvc.perform(get("/admin/security/dashboard"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /admin/security/login-success-rate")
  class GetLoginSuccessRate {

    @Test
    @DisplayName("should return 200 when login success rate query succeeds")
    void should_return_200_when_login_success_rate_query_succeeds() throws Exception {
      mockMvc.perform(get("/admin/security/login-success-rate"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T any(Class<T> clazz) {
    return org.mockito.ArgumentMatchers.any(clazz);
  }
}
