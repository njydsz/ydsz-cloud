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
import com.njydsz.nextwiki.server.service.ShareApplicationService;

/**
 * {@link ShareController} Smoke Test。
 *
 * <p>验证文件分享管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class ShareControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private ShareApplicationService shareApplicationService;

  @org.mockito.InjectMocks
  private ShareController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/shares/my")
  class MyShares {

    @Test
    @DisplayName("should return 200 when list my shares succeeds")
    void should_return_200_when_list_my_shares_succeeds() throws Exception {
      when(shareApplicationService.findByUserId("user-001")).thenReturn(List.of());

      mockMvc.perform(get("/nextwiki/shares/my")
              .header(AuthHeaderConstants.X_USER_ID, "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /nextwiki/shares/received")
  class ReceivedShares {

    @Test
    @DisplayName("should return 200 when list received shares succeeds")
    void should_return_200_when_list_received_shares_succeeds() throws Exception {
      when(shareApplicationService.getReceivedShares("user-001")).thenReturn(List.of());

      mockMvc.perform(get("/nextwiki/shares/received")
              .header(AuthHeaderConstants.X_USER_ID, "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
