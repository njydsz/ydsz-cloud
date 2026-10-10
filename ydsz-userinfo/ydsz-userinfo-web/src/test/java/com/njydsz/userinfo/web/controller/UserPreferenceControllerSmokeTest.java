package com.njydsz.userinfo.web.controller;

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

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.vo.UserPreferenceVO;
import com.njydsz.userinfo.server.service.UserPreferenceService;

/**
 * {@link UserPreferenceController} Smoke Test。
 *
 * <p>验证用户偏好查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class UserPreferenceControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private UserPreferenceService userPreferenceService;

  @org.mockito.InjectMocks
  private UserPreferenceController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(userPreferenceService.get(anyString())).thenReturn(new UserPreferenceVO());
  }

  @Nested
  @DisplayName("GET /user/preferences")
  class Get {

    @Test
    @DisplayName("should return 200 when get preferences succeeds")
    void should_return_200_when_get_preferences_succeeds() throws Exception {
      mockMvc.perform(get("/user/preferences"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
