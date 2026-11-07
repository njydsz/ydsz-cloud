package com.njydsz.message.web.controller.core;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.domain.dto.NotificationQueryDTO;
import com.njydsz.message.domain.enums.receipt.ReadStatusEnum;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.server.realtime.RealtimePushService;
import com.njydsz.message.server.service.core.NotificationService;
import com.njydsz.message.server.service.receipt.RecallService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link NotificationController} Smoke Test。
 *
 * <p>验证站内通知全部端点的真实 HTTP 路径/方法、参数/请求体绑定、响应字段、服务调用与关键状态变更/负例，
 * 防御接口签名变更未被发现的生产事件。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>POST /send — 发送通知（成功条数 / 服务异常）
 *   <li>GET /inbox — 收件箱分页（含登录态）
 *   <li>GET /unreadCount — 未读数
 *   <li>POST /{id}/read — 标记单条已读（true/false）
 *   <li>POST /readAll — 全部已读（条数）
 *   <li>DELETE — 删除通知
 *   <li>POST /{id}/recall — 撤回通知（true/false）
 *   <li>POST /push — 单推（Query userId+type + body）
 *   <li>POST /broadcast — 广播（body topic+data）
 *   <li>POST /push-realtime — 单播（body userId+type+data）
 *   <li>负例：未登录访问 inbox/unreadCount/read/readAll/delete/recall → 401
 *   <li>负例：请求体非法 JSON → 400
 * </ul>
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class NotificationControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock private NotificationService notificationService;

  @Mock private RecallService recallService;

  @Mock private RealtimePushService realtimePushService;

  @InjectMocks private NotificationController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  private void login(String userId) {
    LoginUser loginUser = new LoginUser();
    loginUser.setUserId(userId);
    loginUser.setTenantId("1");
    AuthContextUtils.setCurrent(loginUser);
  }

  @Nested
  @DisplayName("POST /message/notifications/send")
  class Send {

    @Test
    @DisplayName("发送成功 → 200 + data=发送条数，服务收到 DTO")
    void should_send_notification() throws Exception {
      when(notificationService.send(any())).thenReturn(1);

      mockMvc
          .perform(
              post("/message/notifications/send")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"receiverId\":\"user-001\",\"title\":\"通知\","
                          + "\"content\":\"内容\",\"level\":\"INFO\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    @DisplayName("群发（receiverIds 多元素）→ 200 + data=条数")
    void should_send_batch_notification() throws Exception {
      when(notificationService.send(any())).thenReturn(3);

      mockMvc
          .perform(
              post("/message/notifications/send")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"receiverIds\":[\"u1\",\"u2\",\"u3\"],\"title\":\"通知\","
                          + "\"content\":\"内容\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(3));
    }

    @Test
    @DisplayName("收件人不存在 → 200 + data=0")
    void should_return_zero_when_receiver_absent() throws Exception {
      when(notificationService.send(any())).thenReturn(0);

      mockMvc
          .perform(
              post("/message/notifications/send")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"receiverId\":\"ghost\",\"title\":\"t\",\"content\":\"c\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @DisplayName("请求体非法 JSON → 400（服务零调用）")
    void should_reject_malformed_json() throws Exception {
      mockMvc
          .perform(
              post("/message/notifications/send")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{bad"))
          .andExpect(status().isBadRequest());

      verify(notificationService, never()).send(any());
    }
  }

  @Nested
  @DisplayName("GET /message/notifications/inbox")
  class Inbox {

    @Test
    @DisplayName("已登录 → 200 + 分页数据，userId 透传")
    void should_return_200_when_inbox_query_succeeds() throws Exception {
      login("user-001");

      MsgNotificationVO vo = new MsgNotificationVO();
      vo.setId("notif-001");
      vo.setTitle("发布上线通知");
      PageResponse<List<MsgNotificationVO>> pageResult =
          PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(notificationService.inbox(eq("user-001"), any(NotificationQueryDTO.class)))
          .thenReturn(pageResult);

      mockMvc
          .perform(
              get("/message/notifications/inbox")
                  .header(AuthHeaderConstants.X_USER_ID, "user-001")
                  .param("pageNum", "1")
                  .param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.total").value(1))
          .andExpect(jsonPath("$.data.data[0].id").value("notif-001"));
    }

    @Test
    @DisplayName("未登录 → 401（AuthContextUtils.getUserId 抛 SysException）")
    void should_return_401_when_not_logged_in() throws Exception {
      mockMvc.perform(get("/message/notifications/inbox")).andExpect(status().isUnauthorized());

      verify(notificationService, never()).inbox(any(), any());
    }

    @Test
    @DisplayName("查询参数绑定：readStatus/category/level 透传到 DTO")
    void should_bind_query_params() throws Exception {
      login("user-001");
      when(notificationService.inbox(eq("user-001"), any(NotificationQueryDTO.class)))
          .thenReturn(PageResponse.success(0L, 1L, 20L, List.of()));

      mockMvc
          .perform(
              get("/message/notifications/inbox")
                  .param("readStatus", "UNREAD")
                  .param("category", "ORDER")
                  .param("level", "INFO"))
          .andExpect(status().isOk());

      ArgumentCaptor<NotificationQueryDTO> captor =
          ArgumentCaptor.forClass(NotificationQueryDTO.class);
      verify(notificationService).inbox(eq("user-001"), captor.capture());
      NotificationQueryDTO query = captor.getValue();
      assertThat(query.getReadStatus()).isEqualTo(ReadStatusEnum.UNREAD);
      assertThat(query.getCategory()).isEqualTo("ORDER");
      assertThat(query.getLevel()).isEqualTo("INFO");
    }
  }

  @Nested
  @DisplayName("GET /message/notifications/unreadCount")
  class CountUnread {

    @Test
    @DisplayName("已登录 → 200 + data=未读数")
    void should_return_unread_count() throws Exception {
      login("user-001");
      when(notificationService.countUnread("user-001")).thenReturn(5L);

      mockMvc
          .perform(get("/message/notifications/unreadCount"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    @DisplayName("未登录 → 401")
    void should_return_401_when_not_logged_in() throws Exception {
      mockMvc.perform(get("/message/notifications/unreadCount"))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("POST /message/notifications/{id}/read")
  class MarkRead {

    @Test
    @DisplayName("标记成功 → 200 + data=true")
    void should_mark_read_success() throws Exception {
      login("user-001");
      when(notificationService.markRead("user-001", "notif-001")).thenReturn(true);

      mockMvc
          .perform(post("/message/notifications/{id}/read", "notif-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(notificationService).markRead("user-001", "notif-001");
    }

    @Test
    @DisplayName("标记失败（不存在/非接收人）→ 200 + data=false")
    void should_return_false_when_mark_read_fails() throws Exception {
      login("user-001");
      when(notificationService.markRead("user-001", "notif-404")).thenReturn(false);

      mockMvc
          .perform(post("/message/notifications/{id}/read", "notif-404"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("未登录 → 401")
    void should_return_401_when_not_logged_in() throws Exception {
      mockMvc.perform(post("/message/notifications/{id}/read", "notif-001"))
          .andExpect(status().isUnauthorized());

      verify(notificationService, never()).markRead(any(), any());
    }
  }

  @Nested
  @DisplayName("POST /message/notifications/readAll")
  class MarkAllRead {

    @Test
    @DisplayName("全部已读 → 200 + data=标记条数")
    void should_mark_all_read() throws Exception {
      login("user-001");
      when(notificationService.markAllRead("user-001")).thenReturn(4);

      mockMvc
          .perform(post("/message/notifications/readAll"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(4));

      verify(notificationService).markAllRead("user-001");
    }

    @Test
    @DisplayName("无未读通知 → 200 + data=0")
    void should_return_zero_when_no_unread() throws Exception {
      login("user-001");
      when(notificationService.markAllRead("user-001")).thenReturn(0);

      mockMvc
          .perform(post("/message/notifications/readAll"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @DisplayName("未登录 → 401")
    void should_return_401_when_not_logged_in() throws Exception {
      mockMvc.perform(post("/message/notifications/readAll"))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("DELETE /message/notifications")
  class Delete {

    @Test
    @DisplayName("删除成功 → 200 + 无 data，服务收到 userId+ids")
    void should_delete_notifications() throws Exception {
      login("user-001");

      mockMvc
          .perform(
              delete("/message/notifications")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("[\"notif-001\",\"notif-002\"]"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(notificationService).delete(eq("user-001"), eq(List.of("notif-001", "notif-002")));
    }

    @Test
    @DisplayName("空 id 列表 → 200，服务仍被调用")
    void should_handle_empty_id_list() throws Exception {
      login("user-001");

      mockMvc
          .perform(
              delete("/message/notifications")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("[]"))
          .andExpect(status().isOk());

      verify(notificationService).delete("user-001", List.of());
    }

    @Test
    @DisplayName("未登录 → 401（服务零调用）")
    void should_return_401_when_not_logged_in() throws Exception {
      mockMvc
          .perform(
              delete("/message/notifications")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("[\"notif-001\"]"))
          .andExpect(status().isUnauthorized());

      verify(notificationService, never()).delete(any(), any());
    }

    @Test
    @DisplayName("请求体非法 JSON → 400（服务零调用）")
    void should_reject_malformed_json() throws Exception {
      login("user-001");
      mockMvc
          .perform(
              delete("/message/notifications")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{bad"))
          .andExpect(status().isBadRequest());

      verify(notificationService, never()).delete(any(), any());
    }
  }

  @Nested
  @DisplayName("POST /message/notifications/{id}/recall")
  class Recall {

    @Test
    @DisplayName("撤回成功 → 200 + data=true")
    void should_recall_notification() throws Exception {
      login("user-001");
      when(recallService.recallNotification("user-001", "notif-001")).thenReturn(true);

      mockMvc
          .perform(post("/message/notifications/{id}/recall", "notif-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(recallService).recallNotification("user-001", "notif-001");
    }

    @Test
    @DisplayName("撤回失败（超时/非发送者）→ 200 + data=false")
    void should_return_false_when_recall_fails() throws Exception {
      login("user-001");
      when(recallService.recallNotification("user-001", "notif-old")).thenReturn(false);

      mockMvc
          .perform(post("/message/notifications/{id}/recall", "notif-old"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("未登录 → 401")
    void should_return_401_when_not_logged_in() throws Exception {
      mockMvc.perform(post("/message/notifications/{id}/recall", "notif-001"))
          .andExpect(status().isUnauthorized());

      verify(recallService, never()).recallNotification(any(), any());
    }
  }

  @Nested
  @DisplayName("POST /message/notifications/push")
  class Push {

    @Test
    @DisplayName("单推成功 → 200 + success/userId/type")
    void should_push_to_user() throws Exception {
      mockMvc
          .perform(
              post("/message/notifications/push")
                  .param("userId", "user-001")
                  .param("type", "notif")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"data\":{\"key\":\"value\"}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.success").value(true))
          .andExpect(jsonPath("$.data.userId").value("user-001"))
          .andExpect(jsonPath("$.data.type").value("notif"));

      verify(realtimePushService).pushToUser(eq("user-001"), eq("notif"), any());
    }

    @Test
    @DisplayName("缺少 userId Query 参数 → 400（推送零调用）")
    void should_reject_missing_user_id() throws Exception {
      mockMvc
          .perform(
              post("/message/notifications/push")
                  .param("type", "notif")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"data\":{}}"))
          .andExpect(status().isBadRequest());

      verify(realtimePushService, never()).pushToUser(any(), any(), any());
    }

    @Test
    @DisplayName("缺少 type Query 参数 → 400（推送零调用）")
    void should_reject_missing_type() throws Exception {
      mockMvc
          .perform(
              post("/message/notifications/push")
                  .param("userId", "user-001")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"data\":{}}"))
          .andExpect(status().isBadRequest());

      verify(realtimePushService, never()).pushToUser(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("POST /message/notifications/broadcast")
  class Broadcast {

    @Test
    @DisplayName("广播成功 → 200 + topic/traceId")
    void should_broadcast() throws Exception {
      mockMvc
          .perform(
              post("/message/notifications/broadcast")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"topic\":\"ALERT\",\"data\":{\"msg\":\"hello\"}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.success").value(true))
          .andExpect(jsonPath("$.data.traceId").exists());

      verify(realtimePushService).broadcast(eq("ALERT"), any());
    }

    @Test
    @DisplayName("请求体非法 JSON → 400（广播零调用）")
    void should_reject_malformed_json() throws Exception {
      mockMvc
          .perform(
              post("/message/notifications/broadcast")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{bad"))
          .andExpect(status().isBadRequest());

      verify(realtimePushService, never()).broadcast(any(), any());
    }
  }

  @Nested
  @DisplayName("POST /message/notifications/push-realtime")
  class PushRealtime {

    @Test
    @DisplayName("单播成功 → 200 + type/traceId")
    void should_push_realtime() throws Exception {
      mockMvc
          .perform(
              post("/message/notifications/push-realtime")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"userId\":\"user-001\",\"type\":\"TODO_COUNT\",\"data\":{\"count\":3}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.success").value(true))
          .andExpect(jsonPath("$.data.traceId").exists());

      verify(realtimePushService).pushToUser(eq("user-001"), eq("TODO_COUNT"), any());
    }

    @Test
    @DisplayName("请求体非法 JSON → 400（推送零调用）")
    void should_reject_malformed_json() throws Exception {
      mockMvc
          .perform(
              post("/message/notifications/push-realtime")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{bad"))
          .andExpect(status().isBadRequest());

      verify(realtimePushService, never()).pushToUser(any(), any(), any());
    }
  }
}
