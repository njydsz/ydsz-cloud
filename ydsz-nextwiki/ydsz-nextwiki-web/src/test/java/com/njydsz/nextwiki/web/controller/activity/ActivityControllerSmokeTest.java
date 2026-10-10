package com.njydsz.nextwiki.web.controller.activity;

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

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.server.service.ActivityQueryService;

/**
 * {@link ActivityController} Smoke Test。
 *
 * <p>验证用户活动流端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class ActivityControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private ActivityQueryService activityQueryService;

  @org.mockito.InjectMocks
  private ActivityController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/activities")
  class ListActivities {

    @Test
    @DisplayName("should return 200 when list activities succeeds")
    void should_return_200_when_list_activities_succeeds() throws Exception {
      when(activityQueryService.listActivities("user-001", "1", 1, 20))
          .thenReturn(List.of());

      mockMvc.perform(get("/nextwiki/activities")
              .header(AuthHeaderConstants.X_USER_ID, "user-001")
              .param("page", "1").param("limit", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
