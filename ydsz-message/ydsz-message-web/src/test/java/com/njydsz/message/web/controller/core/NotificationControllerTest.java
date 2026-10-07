package com.njydsz.message.web.controller.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.NotificationSendDTO;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.server.realtime.RealtimePushService;
import com.njydsz.message.server.service.core.NotificationService;
import com.njydsz.message.server.service.receipt.RecallService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link NotificationController} 的 MockMvc 集成测试。
 *
 * <p>验证站内通知的完整生命周期 API：发送 / 收件箱 / 未读计数 / 已读标记 / 删除 / 撤回。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private NotificationService notificationService;

  @MockBean
  private RecallService recallService;

  @MockBean
  private RealtimePushService realtimePushService;

  @Nested
  @DisplayName("/message/notifications/send 接口测试")
  class SendEndpoint {

    @Test
    @DisplayName("发送站内通知应返回发送条数")
    void send_shouldReturnSentCount() throws Exception {
      when(notificationService.send(any(NotificationSendDTO.class))).thenReturn(1);

      NotificationSendDTO dto = new NotificationSendDTO();
      dto.setTitle("测试标题");
      dto.setContent("测试内容");
      dto.setReceiverId("user-001");
      dto.setSenderId("system");
      dto.setLevel("INFO");

      mockMvc.perform(post("/message/notifications/send")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(dto)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(1));
    }
  }

  @Nested
  @DisplayName("/message/notifications/inbox 接口测试")
  class InboxEndpoint {

    @Test
    @DisplayName("收件箱分页查询应返回通知列表")
    void inbox_shouldReturnPagedNotifications() throws Exception {
      MsgNotificationVO vo = new MsgNotificationVO();
      vo.setId("notif-001");
      vo.setTitle("测试通知");
      vo.setContent("测试内容");
      vo.setLevel("INFO");

      PageResponse<List<MsgNotificationVO>> pageResponse = new PageResponse<>();
      pageResponse.setData(List.of(vo));
      pageResponse.setTotal(1L);

      when(notificationService.inbox(any(), any())).thenReturn(pageResponse);

      mockMvc.perform(get("/message/notifications/inbox")
              .param("pageNum", "1")
              .param("pageSize", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.total").value(1))
          .andExpect(jsonPath("$.data.data[0].id").value("notif-001"));
    }
  }

  @Nested
  @DisplayName("/message/notifications/unreadCount 接口测试")
  class UnreadCountEndpoint {

    @Test
    @DisplayName("查询未读数量应返回计数值")
    void countUnread_shouldReturnCount() throws Exception {
      when(notificationService.countUnread(any())).thenReturn(5L);

      mockMvc.perform(get("/message/notifications/unreadCount"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(5));
    }
  }

  @Nested
  @DisplayName("/message/notifications/{id}/read 接口测试")
  class MarkReadEndpoint {

    @Test
    @DisplayName("标记单条已读应返回 true")
    void markRead_shouldReturnTrue() throws Exception {
      when(notificationService.markRead(any(), eq("notif-001"))).thenReturn(true);

      mockMvc.perform(post("/message/notifications/notif-001/read"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(true));
    }
  }

  @Nested
  @DisplayName("/message/notifications/readAll 接口测试")
  class MarkAllReadEndpoint {

    @Test
    @DisplayName("全部标记已读应返回标记条数")
    void markAllRead_shouldReturnCount() throws Exception {
      when(notificationService.markAllRead(any())).thenReturn(3);

      mockMvc.perform(post("/message/notifications/readAll"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(3));
    }
  }

  @Nested
  @DisplayName("/message/notifications 删除接口测试")
  class DeleteEndpoint {

    @Test
    @DisplayName("删除通知应返回操作成功")
    void delete_shouldReturnSuccess() throws Exception {
      doNothing().when(notificationService).delete(any(), any());

      mockMvc.perform(delete("/message/notifications")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(List.of("notif-001", "notif-002"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(200));
    }
  }

  @Nested
  @DisplayName("/message/notifications/{id}/recall 接口测试")
  class RecallEndpoint {

    @Test
    @DisplayName("撤回通知应返回 true")
    void recall_shouldReturnTrue() throws Exception {
      when(recallService.recallNotification(any(), eq("notif-001"))).thenReturn(true);

      mockMvc.perform(post("/message/notifications/notif-001/recall"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(true));
    }
  }
}
