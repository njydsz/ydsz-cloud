package com.njydsz.message.web.controller.receipt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.server.service.receipt.RecallService;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RecallController} Smoke Test。
 *
 * <p>验证消息撤回四个端点的真实 HTTP 路径/方法、参数/请求体绑定、响应字段与服务调用。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>POST /notification — 撤回站内通知（userId Query + dto.id body）成功 true / 失败 false
 *   <li>POST /message/{logId} — 按日志 ID 撤回，路径变量绑定
 *   <li>POST /msg/{msgId} — 按 msgId 撤回，路径变量绑定
 *   <li>POST /batch — 批量撤回（bizType + bizId body），返回撤回条数
 *   <li>请求体非法 JSON → 400（服务零调用）
 *   <li>缺少 Query 参数 userId → 400（服务零调用）
 * </ul>
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RecallControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock private RecallService recallService;

  @InjectMocks private RecallController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("POST /message/recall/notification")
  class RecallNotification {

    @Test
    @DisplayName("撤回成功 → 200 + data=true，userId 与 dto.id 透传")
    void should_recall_notification_success() throws Exception {
      when(recallService.recallNotification("user-001", "notif-001")).thenReturn(true);

      mockMvc
          .perform(
              post("/message/recall/notification")
                  .param("userId", "user-001")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"id\":\"notif-001\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(recallService).recallNotification("user-001", "notif-001");
    }

    @Test
    @DisplayName("撤回失败（不存在/超时/无权限）→ 200 + data=false")
    void should_return_false_when_recall_fails() throws Exception {
      when(recallService.recallNotification("user-002", "notif-404")).thenReturn(false);

      mockMvc
          .perform(
              post("/message/recall/notification")
                  .param("userId", "user-002")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"id\":\"notif-404\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("缺少 userId Query 参数 → 400（服务零调用）")
    void should_reject_missing_user_id_param() throws Exception {
      mockMvc
          .perform(
              post("/message/recall/notification")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"id\":\"notif-001\"}"))
          .andExpect(status().isBadRequest());

      verify(recallService, never())
          .recallNotification(org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("请求体为空 → 400（服务零调用）")
    void should_reject_empty_body() throws Exception {
      mockMvc
          .perform(
              post("/message/recall/notification")
                  .param("userId", "user-001")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(""))
          .andExpect(status().isBadRequest());

      verify(recallService, never())
          .recallNotification(org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.anyString());
    }
  }

  @Nested
  @DisplayName("POST /message/recall/message/{logId}")
  class RecallMessage {

    @Test
    @DisplayName("按日志 ID 撤回成功 → 200 + data=true")
    void should_recall_message_by_log_id() throws Exception {
      when(recallService.recallMessage("log-001")).thenReturn(true);

      mockMvc
          .perform(post("/message/recall/message/{logId}", "log-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(recallService).recallMessage("log-001");
    }

    @Test
    @DisplayName("按日志 ID 撤回失败 → 200 + data=false")
    void should_return_false_when_log_recall_fails() throws Exception {
      when(recallService.recallMessage("log-404")).thenReturn(false);

      mockMvc
          .perform(post("/message/recall/message/{logId}", "log-404"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(false));
    }
  }

  @Nested
  @DisplayName("POST /message/recall/msg/{msgId}")
  class RecallByMsgId {

    @Test
    @DisplayName("按 msgId 撤回成功 → 200 + data=true")
    void should_recall_by_msg_id() throws Exception {
      when(recallService.recallByMsgId("msg-001")).thenReturn(true);

      mockMvc
          .perform(post("/message/recall/msg/{msgId}", "msg-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(recallService).recallByMsgId("msg-001");
    }

    @Test
    @DisplayName("超出撤回窗口 → 200 + data=false")
    void should_return_false_when_window_exceeded() throws Exception {
      when(recallService.recallByMsgId("msg-old")).thenReturn(false);

      mockMvc
          .perform(post("/message/recall/msg/{msgId}", "msg-old"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(false));
    }
  }

  @Nested
  @DisplayName("POST /message/recall/batch")
  class RecallBatch {

    @Test
    @DisplayName("批量撤回 → 200 + data=撤回条数")
    void should_recall_batch() throws Exception {
      when(recallService.recallBatch("ORDER", "order-001")).thenReturn(3);

      mockMvc
          .perform(
              post("/message/recall/batch")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"bizType\":\"ORDER\",\"bizId\":\"order-001\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(3));

      verify(recallService).recallBatch("ORDER", "order-001");
    }

    @Test
    @DisplayName("无可撤回消息 → 200 + data=0")
    void should_return_zero_when_nothing_to_recall() throws Exception {
      when(recallService.recallBatch("ORDER", "order-empty")).thenReturn(0);

      mockMvc
          .perform(
              post("/message/recall/batch")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"bizType\":\"ORDER\",\"bizId\":\"order-empty\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @DisplayName("请求体非法 JSON → 400（服务零调用）")
    void should_reject_malformed_json() throws Exception {
      mockMvc
          .perform(
              post("/message/recall/batch")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{bad"))
          .andExpect(status().isBadRequest());

      verify(recallService, never())
          .recallBatch(org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.anyString());
    }
  }
}
