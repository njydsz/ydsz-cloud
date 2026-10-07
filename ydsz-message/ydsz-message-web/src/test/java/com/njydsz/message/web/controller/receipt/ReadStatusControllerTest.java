package com.njydsz.message.web.controller.receipt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.njydsz.message.server.service.receipt.ReadStatusSyncService;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * {@link ReadStatusController} 的 MockMvc 集成测试。
 *
 * <p>验证消息已读状态同步 API：标记已读 / 批量已读 / 通知已读 / 全部已读 / 未读数量查询。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ExtendWith(MockitoExtension.class)
class ReadStatusControllerTest {

  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock
  private ReadStatusSyncService readStatusSyncService;

  @InjectMocks
  private ReadStatusController readStatusController;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(readStatusController).build();
  }

  @Nested
  @DisplayName("POST /message/read-status/read/{msgId} 标记已读接口测试")
  class MarkReadEndpoint {

    @Test
    @DisplayName("标记消息已读应返回 true")
    void markRead_shouldReturnTrue() throws Exception {
      when(readStatusSyncService.markRead(eq("msg-001"), eq("user-001"))).thenReturn(true);

      mockMvc.perform(post("/message/read-status/read/msg-001")
              .param("userId", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(true));
    }
  }

  @Nested
  @DisplayName("POST /message/read-status/readBatch 批量已读接口测试")
  class MarkReadBatchEndpoint {

    @Test
    @DisplayName("批量标记消息已读应返回标记条数")
    void markReadBatch_shouldReturnCount() throws Exception {
      when(readStatusSyncService.markReadBatch(any(List.class), eq("user-001"))).thenReturn(3);

      mockMvc.perform(post("/message/read-status/readBatch")
              .param("userId", "user-001")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(List.of("msg-001", "msg-002", "msg-003"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(3));
    }
  }

  @Nested
  @DisplayName("POST /message/read-status/notification/{notificationId} 通知已读接口测试")
  class MarkNotificationReadEndpoint {

    @Test
    @DisplayName("标记站内通知已读应返回 true")
    void markNotificationRead_shouldReturnTrue() throws Exception {
      when(readStatusSyncService.markNotificationRead(eq("notif-001"), eq("user-001"))).thenReturn(true);

      mockMvc.perform(post("/message/read-status/notification/notif-001")
              .param("userId", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(true));
    }
  }

  @Nested
  @DisplayName("POST /message/read-status/notification/readAll 全部已读接口测试")
  class MarkAllReadEndpoint {

    @Test
    @DisplayName("全部通知标记已读应返回标记条数")
    void markAllRead_shouldReturnCount() throws Exception {
      when(readStatusSyncService.markAllNotificationsRead(eq("user-001"), any())).thenReturn(10);

      mockMvc.perform(post("/message/read-status/notification/readAll")
              .param("userId", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(10));
    }

    @Test
    @DisplayName("指定 bizType 全部已读应返回标记条数")
    void markAllReadWithBizType_shouldReturnCount() throws Exception {
      when(readStatusSyncService.markAllNotificationsRead(eq("user-001"), eq("ORDER"))).thenReturn(5);

      mockMvc.perform(post("/message/read-status/notification/readAll")
              .param("userId", "user-001")
              .param("bizType", "ORDER"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(5));
    }
  }

  @Nested
  @DisplayName("GET /message/read-status/unreadCount 未读数量接口测试")
  class GetUnreadCountEndpoint {

    @Test
    @DisplayName("查询未读数量应返回 total 和 byChannel")
    void getUnreadCount_shouldReturnCounts() throws Exception {
      when(readStatusSyncService.getUnreadCount("user-001")).thenReturn(15L);
      when(readStatusSyncService.getUnreadCountByChannel("user-001", "SMS")).thenReturn(3L);

      mockMvc.perform(get("/message/read-status/unreadCount")
              .param("userId", "user-001")
              .param("channel", "SMS"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.total").value(15))
          .andExpect(jsonPath("$.data.byChannel").value(3));
    }

    @Test
    @DisplayName("不传 channel 时 byChannel 等于 total")
    void getUnreadCountWithoutChannel_shouldReturnSameValue() throws Exception {
      when(readStatusSyncService.getUnreadCount("user-001")).thenReturn(10L);

      mockMvc.perform(get("/message/read-status/unreadCount")
              .param("userId", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.total").value(10))
          .andExpect(jsonPath("$.data.byChannel").value(10));
    }
  }
}
