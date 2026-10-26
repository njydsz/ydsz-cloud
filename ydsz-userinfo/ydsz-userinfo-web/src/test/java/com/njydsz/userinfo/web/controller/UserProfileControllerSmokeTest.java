package com.njydsz.userinfo.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.auth.MfaService;
import com.njydsz.userinfo.server.service.UserAccountService;

/**
 * {@link UserProfileController} Smoke Test。
 *
 * <p>验证用户资料查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * <p>端点通过 {@code RequestContext.getUserId()} 取当前用户，故测试需先写入请求上下文，
 * 桩参数才能与生产调用完全一致。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class UserProfileControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private UserAccountService userAccountService;

  @Mock
  private MfaService mfaService;

  @InjectMocks
  private UserProfileController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Nested
  @DisplayName("GET /profile/me")
  class GetCurrentUserProfile {

    @Test
    @DisplayName("should return 200 when current user profile query succeeds")
    void should_return_200_when_current_user_profile_query_succeeds() throws Exception {
      RequestContext.setUserId("user-001");

      UserAccountVO user = new UserAccountVO();
      user.setId("user-001");
      user.setUsername("admin");
      when(userAccountService.getById("user-001")).thenReturn(user);

      mockMvc.perform(get("/profile/me"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.username").value("admin"));
    }
  }

  @Nested
  @DisplayName("GET /profile/mfa/status")
  class GetMfaStatus {

    @Test
    @DisplayName("should return 200 when MFA status query succeeds")
    void should_return_200_when_mfa_status_query_succeeds() throws Exception {
      RequestContext.setUserId("user-001");

      when(mfaService.isMfaEnabled("user-001")).thenReturn(false);

      mockMvc.perform(get("/profile/mfa/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(false));
    }
  }
}
