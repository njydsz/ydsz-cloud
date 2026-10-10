package com.njydsz.message.web.controller.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.domain.dto.NotificationQueryDTO;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.server.realtime.RealtimePushService;
import com.njydsz.message.server.service.core.NotificationService;
import com.njydsz.message.server.service.receipt.RecallService;

/**
 * {@link NotificationController} Smoke Test。
 *
 * <p>验证站内通知端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class NotificationControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private NotificationService notificationService;

  @org.mockito.Mock
  private RecallService recallService;

  @org.mockito.Mock
  private RealtimePushService realtimePushService;

  @org.mockito.InjectMocks
  private NotificationController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/notifications/inbox")
  class Inbox {

    @Test
    @DisplayName("should return 200 when inbox query succeeds")
    void should_return_200_when_inbox_query_succeeds() throws Exception {
      MsgNotificationVO vo = new MsgNotificationVO();
      vo.setId("notif-001");
      PageResponse<List<MsgNotificationVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(notificationService.inbox(any(String.class), any(NotificationQueryDTO.class)))
          .thenReturn(pageResult);

      mockMvc.perform(get("/message/notifications/inbox")
              .header(AuthHeaderConstants.X_USER_ID, "user-001")
              .param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
