package com.njydsz.message.web.controller.receipt;

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
import com.njydsz.message.server.service.receipt.ReadStatusSyncService;

/**
 * {@link ReadStatusController} Smoke Test。
 *
 * <p>验证已读状态端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class ReadStatusControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private ReadStatusSyncService readStatusSyncService;

  @org.mockito.InjectMocks
  private ReadStatusController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/read-status/unreadCount")
  class GetUnreadCount {

    @Test
    @DisplayName("should return 200 when get unread count succeeds")
    void should_return_200_when_get_unread_count_succeeds() throws Exception {
      when(readStatusSyncService.getUnreadCount("user-001")).thenReturn(0L);

      mockMvc.perform(get("/message/read-status/unreadCount").param("userId", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
