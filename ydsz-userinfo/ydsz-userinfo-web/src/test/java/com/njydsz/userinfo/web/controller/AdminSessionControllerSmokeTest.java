package com.njydsz.userinfo.web.controller;

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
import com.njydsz.userinfo.domain.vo.UserSessionVO;
import com.njydsz.userinfo.server.auth.UserBanService;
import com.njydsz.userinfo.server.auth.UserSessionAdminService;

/**
 * {@link AdminSessionController} Smoke Test。
 *
 * <p>验证封禁/会话治理查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class AdminSessionControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private UserBanService userBanService;

  @Mock
  private UserSessionAdminService userSessionAdminService;

  @InjectMocks
  private AdminSessionController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /admin/users/{userId}/ban-info")
  class GetBanInfo {

    @Test
    @DisplayName("should return 200 when ban info query succeeds")
    void should_return_200_when_ban_info_query_succeeds() throws Exception {
      when(userBanService.getBanInfo("user-001")).thenReturn(null);

      mockMvc.perform(get("/admin/users/{userId}/ban-info", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /admin/users/{userId}/sessions")
  class GetUserSessions {

    @Test
    @DisplayName("should return 200 when user sessions query succeeds")
    void should_return_200_when_user_sessions_query_succeeds() throws Exception {
      UserSessionVO session = new UserSessionVO();
      session.setUsername("admin");
      session.setLoginIp("10.0.0.1");
      when(userSessionAdminService.listUserSessions("user-001")).thenReturn(List.of(session));

      mockMvc.perform(get("/admin/users/{userId}/sessions", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].username").value("admin"));
    }
  }

  @Nested
  @DisplayName("GET /admin/sessions")
  class GetAllActiveSessions {

    @Test
    @DisplayName("should return 200 when all active sessions query succeeds")
    void should_return_200_when_all_active_sessions_query_succeeds() throws Exception {
      UserSessionVO session = new UserSessionVO();
      session.setUsername("admin");
      when(userSessionAdminService.listAllActiveSessions(1, 20)).thenReturn(List.of(session));

      mockMvc.perform(get("/admin/sessions").param("page", "1").param("size", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].username").value("admin"));
    }
  }

  @Nested
  @DisplayName("GET /admin/sessions/statistics")
  class GetSessionStatistics {

    @Test
    @DisplayName("should return 200 when session statistics query succeeds")
    void should_return_200_when_session_statistics_query_succeeds() throws Exception {
      when(userSessionAdminService.getSessionStatistics()).thenReturn(null);

      mockMvc.perform(get("/admin/sessions/statistics"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
