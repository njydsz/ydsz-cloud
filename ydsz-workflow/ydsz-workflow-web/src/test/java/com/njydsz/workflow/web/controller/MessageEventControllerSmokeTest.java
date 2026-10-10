package com.njydsz.workflow.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.server.message.MessageEventService;

/**
 * {@link MessageEventController} Smoke Test。
 *
 * <p>验证消息事件核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * <p>注意：MessageEventController 仅有 POST 端点，无 GET 端点，因此测试覆盖 POST /workflow/message-event/publish。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class MessageEventControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private MessageEventService messageEventService;

  @org.mockito.InjectMocks
  private MessageEventController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("POST /workflow/message-event/publish")
  class PublishMessage {

    @Test
    @DisplayName("should return 200 when publish message event succeeds")
    void should_return_200_when_publish_message_event_succeeds() throws Exception {
      when(messageEventService.publishMessageEvent(anyString(), any())).thenReturn(3);

      String body = "{\"messageName\":\"order_paid\",\"correlationKeys\":{\"orderId\":\"123\"}}";

      mockMvc.perform(post("/workflow/message-event/publish")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
