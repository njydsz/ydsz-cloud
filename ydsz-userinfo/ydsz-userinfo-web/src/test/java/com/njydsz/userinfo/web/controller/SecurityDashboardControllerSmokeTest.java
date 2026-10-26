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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.vo.SessionActivityVO;
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

  @Mock
  private SecurityDashboardService securityDashboardService;

  @Mock
  private SessionActivityService sessionActivityService;

  @InjectMocks
  private SecurityDashboardController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /admin/security/dashboard")
  class GetDashboard {

    @Test
    @DisplayName("should return 200 when dashboard query succeeds")
    void should_return_200_when_dashboard_query_succeeds() throws Exception {
      when(securityDashboardService.getDashboard()).thenReturn(null);

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
      // start/end 由 Controller 内部按「今天 / 默认回溯天数」计算，无法用固定值声明，故使用匹配器
      when(securityDashboardService.getLoginSuccessRate(any(), any())).thenReturn(List.of());

      mockMvc.perform(get("/admin/security/login-success-rate"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /admin/security/session-activity")
  class GetSessionActivity {

    @Test
    @DisplayName("should return 200 when session activity query succeeds")
    void should_return_200_when_session_activity_query_succeeds() throws Exception {
      when(sessionActivityService.getActivityOverview())
          .thenReturn(new SessionActivityVO(5, 3, 12.5D));

      mockMvc.perform(get("/admin/security/session-activity"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.totalActiveSessions").value(5));
    }
  }
}
