package com.njydsz.message.web.controller.core;

import static org.mockito.ArgumentMatchers.any;
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

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.service.core.MessageService;

/**
 * {@link MessageController} Smoke Test。
 *
 * <p>验证消息发送日志端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class MessageControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private MessageService messageService;

  @org.mockito.InjectMocks
  private MessageController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/log/page")
  class PageLog {

    @Test
    @DisplayName("should return 200 when page log query succeeds")
    void should_return_200_when_page_log_query_succeeds() throws Exception {
      MsgLogVO vo = new MsgLogVO();
      vo.setMsgId("msg-001");
      PageResponse<List<MsgLogVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(messageService.pageLog(any(MessageLogQueryDTO.class))).thenReturn(pageResult);

      mockMvc.perform(get("/message/log/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
