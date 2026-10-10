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

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.auth.MfaService;
import com.njydsz.userinfo.server.service.UserAccountService;

/**
 * {@link UserProfileController} Smoke Test。
 *
 * <p>验证用户资料查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class UserProfileControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private UserAccountService userAccountService;

  @org.mockito.Mock
  private MfaService mfaService;

  @org.mockito.InjectMocks
  private UserProfileController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(userAccountService.getById("user-001")).thenReturn(new UserAccountVO());
    when(mfaService.isMfaEnabled("user-001")).thenReturn(false);
  }

  @Nested
  @DisplayName("GET /profile/mfa/status")
  class GetMfaStatus {

    @Test
    @DisplayName("should return 200 when MFA status query succeeds")
    void should_return_200_when_mfa_status_query_succeeds() throws Exception {
      mockMvc.perform(get("/profile/mfa/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
