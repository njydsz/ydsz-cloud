package com.njydsz.message.web.controller.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.service.core.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link MessageController} Smoke Test - 发送日志分页查询（轻量级 MockMvc 独立容器模式）。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>正常分页列表接口 HTTP 200 + 响应体 code='A00000'
 * </ul>
 *
 * @author ydsz-smoke-test
 * @since 26.10.01
 */
class MessageLogSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private MessageService messageService;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private MessageController messageController;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(messageController);
  }

  @Test
  @DisplayName("GET /message/log/page 应返回 HTTP 200 + code=A00000")
  void pageLog_shouldReturn200() throws Exception {
    // Given
    PageResponse<List<MsgLogVO>> pageResponse =
        PageResponse.success(0L, 1L, 10L, List.of(new MsgLogVO()));

    when(messageService.pageLog(any(MessageLogQueryDTO.class))).thenReturn(pageResponse);

    // When
    mockMvc.perform(get("/message/log/page")
            .param("pageNum", "1")
            .param("pageSize", "10"))
        // Then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
  }
}
