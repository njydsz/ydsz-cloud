package com.njydsz.message.web.controller.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.NotificationSendDTO;
import com.njydsz.message.domain.vo.MessageSendResultVO;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.server.realtime.RealtimePushService;
import com.njydsz.message.server.service.core.NotificationService;
import com.njydsz.message.server.service.receipt.RecallService;

/**
 * NotificationController 集成测试。
 *
 * <p>使用 {@code MockMvcBuilders.standaloneSetup()} 构建轻量 Web 层测试。
 * 由于 Controller 内部调用 {@code AuthContextUtils.getUserId()}（{@code TransmittableThreadLocal}），测试通过
 * {@link #authFilter()} 注入登录用户上下文。通过 Mockito {@code @Mock} 模拟通知/撤回/推送三个服务依赖。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

  /** 测试用用户 ID */
  private static final String TEST_USER_ID = "user-001";

  private MockMvc mockMvc;

  @Mock
  private NotificationService notificationService;

  @Mock
  private RecallService recallService;

  @Mock
  private RealtimePushService realtimePushService;

  @InjectMocks
  private NotificationController notificationController;

  private MsgNotificationVO sampleNotification;

  /**
   * 构建认证过滤器 — 在请求处理前注入登录用户上下文。
   *
   * <p>将 {@link LoginUser} 写入 {@link RequestContext}（{@code TransmittableThreadLocal}），使 Controller 内
   * {@code AuthContextUtils.getUserId()} 能正确获取测试用户 ID。 *
   * @return 注入登录用户的 Servlet 过滤器
   */
  private Filter authFilter() {
    return (request, response, chain) -> {
      try {
        LoginUser loginUser = LoginUser.builder()
            .userId(TEST_USER_ID)
            .username("testUser")
            .tenantId("1")
            .build();
        RequestContext.put(BizContextKeys.KEY_LOGIN_USER, loginUser);
        RequestContext.setUserId(TEST_USER_ID);
        chain.doFilter(request, response);
      } finally {
        RequestContext.clear();
      }
    };
  }

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
        .addFilter(authFilter())
        .build();

    sampleNotification = new MsgNotificationVO();
    sampleNotification.setId("notif-001");
    sampleNotification.setTitle("系统通知");
    sampleNotification.setContent("您有一个新的待办事项");
    sampleNotification.setLevel("INFO");
    sampleNotification.setCategory("SYSTEM");
    sampleNotification.setSenderId("SYSTEM");
    sampleNotification.setReceiverId(TEST_USER_ID);
    sampleNotification.setBizType("TODO");
    sampleNotification.setCreatedAt(LocalDateTime.of(2026, 10, 1, 10, 0));
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Nested
  @DisplayName("POST /message/notifications/send - send")
  class Send {

    @Test
    @DisplayName("发送站内通知成功时返回 200 + 发送条数")
    void shouldReturnSentCount() throws Exception {
      NotificationSendDTO dto = new NotificationSendDTO();
      dto.setTitle("系统通知");
      dto.setContent("您有一个新消息");
      dto.setReceiverId(TEST_USER_ID);

      when(notificationService.send(any(NotificationSendDTO.class))).thenReturn(1);

      mockMvc.perform(post("/message/notifications/send")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"title\":\"系统通知\",\"content\":\"您有一个新消息\","
                  + "\"receiverId\":\"user-001\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").value(1));

      verify(notificationService, times(1)).send(any(NotificationSendDTO.class));
    }
  }

  @Nested
  @DisplayName("GET /message/notifications/inbox - inbox")
  class Inbox {

    @Test
    @DisplayName("收件箱分页返回 PageResponse 包装数据")
    void shouldReturnInboxPagedResult() throws Exception {
      @SuppressWarnings("unchecked")
      PageResponse<List<MsgNotificationVO>> pageResult = new PageResponse<>();
      pageResult.setData(List.of(sampleNotification));
      pageResult.setTotal(1L);
      pageResult.setPageNum(1L);
      pageResult.setPageSize(20L);

      when(notificationService.inbox(eq(TEST_USER_ID), any())).thenReturn(pageResult);

      mockMvc.perform(get("/message/notifications/inbox")
              .param("pageNum", "1")
              .param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.total").value(1))
          .andExpect(jsonPath("$.data.data[0].id").value("notif-001"))
          .andExpect(jsonPath("$.data.data[0].title").value("系统通知"));

      verify(notificationService, times(1)).inbox(eq(TEST_USER_ID), any());
    }
  }

  @Nested
  @DisplayName("GET /message/notifications/unreadCount - countUnread")
  class CountUnread {

    @Test
    @DisplayName("查询未读数返回 200 + 数量")
    void shouldReturnUnreadCount() throws Exception {
      when(notificationService.countUnread(TEST_USER_ID)).thenReturn(5L);

      mockMvc.perform(get("/message/notifications/unreadCount"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").value(5));

      verify(notificationService, times(1)).countUnread(TEST_USER_ID);
    }
  }

  @Nested
  @DisplayName("POST /message/notifications/{id}/read - markRead")
  class MarkRead {

    @Test
    @DisplayName("标记单条已读成功返回 200 + true")
    void shouldReturnTrueOnMarkRead() throws Exception {
      when(notificationService.markRead(TEST_USER_ID, "notif-001")).thenReturn(true);

      mockMvc.perform(post("/message/notifications/{id}/read", "notif-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").value(true));

      verify(notificationService, times(1)).markRead(TEST_USER_ID, "notif-001");
    }
  }

  @Nested
  @DisplayName("POST /message/notifications/readAll - markAllRead")
  class MarkAllRead {

    @Test
    @DisplayName("全部标记已读成功返回 200 + 条数")
    void shouldReturnAffectedCount() throws Exception {
      when(notificationService.markAllRead(TEST_USER_ID)).thenReturn(10);

      mockMvc.perform(post("/message/notifications/readAll"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").value(10));

      verify(notificationService, times(1)).markAllRead(TEST_USER_ID);
    }
  }

  @Nested
  @DisplayName("DELETE /message/notifications - delete")
  class Delete {

    @Test
    @DisplayName("删除通知成功时返回 200 + Void")
    void shouldReturnSuccessOnDelete() throws Exception {
      mockMvc.perform(delete("/message/notifications")
              .contentType(MediaType.APPLICATION_JSON)
              .content("[\"notif-001\",\"notif-002\"]"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(notificationService, times(1)).delete(eq(TEST_USER_ID), any());
    }
  }

  @Nested
  @DisplayName("POST /message/notifications/{id}/recall - recall")
  class Recall {

    @Test
    @DisplayName("撤回通知成功时返回 200 + true")
    void shouldReturnTrueOnRecall() throws Exception {
      when(recallService.recallNotification(TEST_USER_ID, "notif-001")).thenReturn(true);

      mockMvc.perform(post("/message/notifications/{id}/recall", "notif-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").value(true));

      verify(recallService, times(1)).recallNotification(TEST_USER_ID, "notif-001");
    }

    @Test
    @DisplayName("撤回失败时返回 200 + false")
    void shouldReturnFalseOnRecallFailure() throws Exception {
      when(recallService.recallNotification(TEST_USER_ID, "notif-404")).thenReturn(false);

      mockMvc.perform(post("/message/notifications/{id}/recall", "notif-404"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").value(false));
    }
  }

  @Nested
  @DisplayName("POST /message/notifications/push-realtime - pushRealtime")
  class PushRealtime {

    @Test
    @DisplayName("单播实时推送成功返回 200 + 推送结果")
    void shouldReturnPushResult() throws Exception {
      mockMvc.perform(post("/message/notifications/push-realtime")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"userId\":\"user-001\",\"type\":\"NOTIFICATION\","
                  + "\"data\":{\"title\":\"新消息\"}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.isSuccess").value(true));
    }
  }

  @Nested
  @DisplayName("POST /message/notifications/broadcast - broadcast")
  class Broadcast {

    @Test
    @DisplayName("广播推送成功返回 200 + 推送结果")
    void shouldReturnBroadcastResult() throws Exception {
      mockMvc.perform(post("/message/notifications/broadcast")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"topic\":\"global-alert\","
                  + "\"data\":{\"message\":\"系统维护通知\"}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.isSuccess").value(true));
    }
  }
}
