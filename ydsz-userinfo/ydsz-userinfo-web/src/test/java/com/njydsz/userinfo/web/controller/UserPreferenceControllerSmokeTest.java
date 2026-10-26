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

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.vo.UserPreferenceVO;
import com.njydsz.userinfo.server.service.UserPreferenceService;

/**
 * {@link UserPreferenceController} Smoke Test。
 *
 * <p>验证用户偏好查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * <p>该端点通过 {@code SecurityUtils.getCurrentUserId()} 读取登录态，未写入登录上下文时
 * 抛 401 {@code SysException}，因此测试必须先装配当前登录用户。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class UserPreferenceControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private UserPreferenceService userPreferenceService;

  @InjectMocks
  private UserPreferenceController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Nested
  @DisplayName("GET /user/preferences")
  class Get {

    @Test
    @DisplayName("should return 200 when get preferences succeeds")
    void should_return_200_when_get_preferences_succeeds() throws Exception {
      LoginUser loginUser = new LoginUser();
      loginUser.setUserId("user-001");
      AuthContextUtils.setCurrent(loginUser);

      UserPreferenceVO preference = new UserPreferenceVO();
      preference.setTheme("dark");
      when(userPreferenceService.get("user-001")).thenReturn(preference);

      mockMvc.perform(get("/user/preferences"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.theme").value("dark"));
    }
  }
}
