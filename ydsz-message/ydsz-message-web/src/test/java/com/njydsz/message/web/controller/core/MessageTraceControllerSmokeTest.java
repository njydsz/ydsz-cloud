package com.njydsz.message.web.controller.core;

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

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.domain.vo.MsgTraceVO;
import com.njydsz.message.server.service.core.MessageTraceService;

/**
 * {@link MessageTraceController} Smoke Test。
 *
 * <p>验证消息追踪端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class MessageTraceControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private MessageTraceService messageTraceService;

  @org.mockito.InjectMocks
  private MessageTraceController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/trace/msg/{msgId}")
  class GetByMsgId {

    @Test
    @DisplayName("should return 200 when get by msgId succeeds")
    void should_return_200_when_get_by_msg_id_succeeds() throws Exception {
      when(messageTraceService.getTraceByMsgId("msg-001")).thenReturn(List.of());

      mockMvc.perform(get("/message/trace/msg/{msgId}", "msg-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
