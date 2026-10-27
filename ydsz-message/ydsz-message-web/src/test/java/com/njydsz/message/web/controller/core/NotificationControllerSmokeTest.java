package com.njydsz.message.web.controller.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.RequestContext;
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
 * <p>inbox 端点通过 {@code AuthContextUtils.getUserId()} 读取登录态，未写入登录上下文时抛 401
 * {@code SysException}，因此该用例需先装配当前登录用户。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class NotificationControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private NotificationService notificationService;

  @Mock
  private RecallService recallService;

  @Mock
  private RealtimePushService realtimePushService;

  @InjectMocks
  private NotificationController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Nested
  @DisplayName("GET /message/notifications/inbox")
  class Inbox {

    @Test
    @DisplayName("should return 200 when inbox query succeeds")
    void should_return_200_when_inbox_query_succeeds() throws Exception {
      LoginUser loginUser = new LoginUser();
      loginUser.setUserId("user-001");
      AuthContextUtils.setCurrent(loginUser);

      MsgNotificationVO vo = new MsgNotificationVO();
      vo.setId("notif-001");
      vo.setTitle("发布上线通知");
      PageResponse<List<MsgNotificationVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(notificationService.inbox(eq("user-001"), any(NotificationQueryDTO.class)))
          .thenReturn(pageResult);

      mockMvc.perform(get("/message/notifications/inbox")
              .header(AuthHeaderConstants.X_USER_ID, "user-001")
              .param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.total").value(1))
          .andExpect(jsonPath("$.data.data[0].id").value("notif-001"));
    }
  }
}
