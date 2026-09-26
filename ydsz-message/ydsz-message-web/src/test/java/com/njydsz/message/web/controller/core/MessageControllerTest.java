package com.njydsz.message.web.controller.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.service.core.MessageService;

/**
 * MessageController 纯 Mockito 单元测试（无 Spring 容器）。
 *
 * <p>因 Controller 依赖 @AuthApiPermission / @Idempotent / @RateLimit 等多个 AOP 切面，
 * 完整 @WebMvcTest 需要加载 Security + Redis 等重量级上下文，故退化为 Mockito 单元测试，
 * 验证 Controller 正确委托 Service 并封装响应。
 */
class MessageControllerTest {

  @InjectMocks private MessageController messageController;

  @Mock private MessageService mockMessageService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @DisplayName("pageLog: 正常返回 A00000 及分页结构")
  void pageLog_success() {
    // arrange
    PageResponse<List<MsgLogVO>> emptyPage = PageResponse.empty(1L, 20L);
    when(mockMessageService.pageLog(any(MessageLogQueryDTO.class))).thenReturn(emptyPage);

    // act
    YdszResponse<PageResponse<List<MsgLogVO>>> response =
        messageController.pageLog(new MessageLogQueryDTO());

    // assert: code / success flag / data structure
    assertThat(response).isNotNull();
    assertThat(response.getCode()).isEqualTo("A00000");
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getTotal()).isEqualTo(0L);
    verify(mockMessageService).pageLog(any(MessageLogQueryDTO.class));
  }

  @Test
  @DisplayName("cancelScheduled: 正常返回 A00000")
  void cancelScheduled_success() {
    // arrange
    com.njydsz.message.domain.vo.MessageSendResultVO mockResult =
        new com.njydsz.message.domain.vo.MessageSendResultVO();
    mockResult.setStatus("CANCELLED");
    when(mockMessageService.cancelScheduledMessage("msg-001")).thenReturn(mockResult);

    // act
    YdszResponse<com.njydsz.message.domain.vo.MessageSendResultVO> response =
        messageController.cancelScheduled("msg-001");

    // assert
    assertThat(response).isNotNull();
    assertThat(response.getCode()).isEqualTo("A00000");
    assertThat(response.getData()).isNotNull();
    verify(mockMessageService).cancelScheduledMessage("msg-001");
  }
}
