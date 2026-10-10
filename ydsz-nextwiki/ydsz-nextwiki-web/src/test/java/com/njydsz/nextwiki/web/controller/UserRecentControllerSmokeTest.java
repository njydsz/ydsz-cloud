package com.njydsz.nextwiki.web.controller;

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
import com.njydsz.nextwiki.domain.vo.UserRecentVO;
import com.njydsz.nextwiki.server.service.UserRecentApplicationService;

/**
 * {@link UserRecentController} Smoke Test。
 *
 * <p>验证用户最近访问端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class UserRecentControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private UserRecentApplicationService userRecentApplicationService;

  @org.mockito.InjectMocks
  private UserRecentController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/recent")
  class ListRecent {

    @Test
    @DisplayName("should return 200 when list recent succeeds")
    void should_return_200_when_list_recent_succeeds() throws Exception {
      when(userRecentApplicationService.listRecent("user-001", 20)).thenReturn(List.of());

      mockMvc.perform(get("/nextwiki/recent")
              .header(AuthHeaderConstants.X_USER_ID, "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
