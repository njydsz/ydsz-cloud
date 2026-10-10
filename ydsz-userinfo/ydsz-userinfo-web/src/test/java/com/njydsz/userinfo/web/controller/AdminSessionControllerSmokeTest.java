package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

  @org.mockito.Mock
  private UserBanService userBanService;

  @org.mockito.Mock
  private UserSessionAdminService userSessionAdminService;

  @org.mockito.InjectMocks
  private AdminSessionController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(userBanService.getBanInfo(anyString())).thenReturn(null);
    when(userSessionAdminService.listUserSessions(anyString())).thenReturn(List.of());
    when(userSessionAdminService.listAllActiveSessions(any(Integer.class), any(Integer.class)))
        .thenReturn(List.of());
    when(userSessionAdminService.getSessionStatistics()).thenReturn(null);
  }

  @Nested
  @DisplayName("GET /admin/users/{userId}/ban-info")
  class GetBanInfo {

    @Test
    @DisplayName("should return 200 when ban info query succeeds")
    void should_return_200_when_ban_info_query_succeeds() throws Exception {
      mockMvc.perform(get("/admin/users/{userId}/ban-info", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /admin/sessions/statistics")
  class GetSessionStatistics {

    @Test
    @DisplayName("should return 200 when session statistics query succeeds")
    void should_return_200_when_session_statistics_query_succeeds() throws Exception {
      mockMvc.perform(get("/admin/sessions/statistics"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @SuppressWarnings("all")
  private static <T> T any(Class<T> clazz) {
    return org.mockito.ArgumentMatchers.any(clazz);
  }
}
