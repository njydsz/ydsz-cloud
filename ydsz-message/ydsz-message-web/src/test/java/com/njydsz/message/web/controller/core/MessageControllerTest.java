package com.njydsz.message.web.controller.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.dto.MessageSendDTO;
import com.njydsz.message.domain.enums.core.SendStrategyEnum;
import com.njydsz.message.domain.vo.MessageSendResultVO;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.service.core.MessageService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@link MessageController} 的 MockMvc 集成测试。
 *
 * <p>使用 {@code MockMvcBuilders.standaloneSetup()} 构建独立的 MockMvc 实例，
 * 通过 Mockito {@code @Mock} 模拟 Service 依赖，验证 HTTP 请求路由与响应格式。
 *
 * <p>注意：Spring Boot 4.x 移除了 {@code @WebMvcTest} 切片注解，
 * 退化使用独立的 MockMvc standalone 设置以获得等效测试效果。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock
  private MessageService messageService;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private MessageController messageController;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(messageController).build();
  }

  @Nested
  @DisplayName("/message/send 接口测试")
  class SendEndpoint {

    @Test
    @DisplayName("同步发送消息应返回 200 且 data.success=true")
    void sendSync_shouldReturnSuccess() throws Exception {
      MessageSendResultVO result = MessageSendResultVO.ok("SMS", "trace-001");
      when(messageService.send(any())).thenReturn(result);

      MessageSendDTO dto = new MessageSendDTO();
      dto.setStrategy(SendStrategyEnum.SYNC);
      dto.setChannel("SMS");
      dto.setReceiver("1*********0");
      dto.setContent("测试消息");

      mockMvc.perform(post("/message/send")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(dto)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(200))
          .andExpect(jsonPath("$.data.isSuccess").value(true))
          .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("异步发送消息应返回 200 且 msg=ASYNC_QUEUED")
    void sendAsync_shouldReturnQueued() throws Exception {
      MessageSendResultVO result = MessageSendResultVO.ok("EMAIL", "trace-002");
      when(messageService.sendAsync(any())).thenReturn(result);

      MessageSendDTO dto = new MessageSendDTO();
      dto.setStrategy(SendStrategyEnum.ASYNC);
      dto.setChannel("EMAIL");
      dto.setReceiver("u***example.com");
      dto.setSubject("异步测试");
      dto.setContent("异步内容");

      mockMvc.perform(post("/message/send")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(dto)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.msg").value("ASYNC_QUEUED"))
          .andExpect(jsonPath("$.data.isSuccess").value(true));
    }

    @Test
    @DisplayName("缺少 strategy 应默认走 SYNC 策略且发送成功")
    void sendWithoutStrategy_shouldDefaultToSync() throws Exception {
      MessageSendResultVO result = MessageSendResultVO.ok("SMS", "trace-003");
      when(messageService.send(any())).thenReturn(result);

      MessageSendDTO dto = new MessageSendDTO();
      dto.setChannel("SMS");
      dto.setReceiver("1*********0");
      dto.setContent("无策略测试");

      mockMvc.perform(post("/message/send")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(dto)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }
  }

  @Nested
  @DisplayName("/message/log/page 接口测试")
  class PageLogEndpoint {

    @Test
    @DisplayName("分页查询发送日志应返回分页结果")
    void pageLog_shouldReturnPagedResult() throws Exception {
      MsgLogVO logVo = new MsgLogVO();
      logVo.setMsgId("msg-001");
      logVo.setChannel("SMS");
      logVo.setReceiver("1*********0");
      logVo.setStatus("SUCCESS");

      PageResponse<List<MsgLogVO>> pageResponse = new PageResponse<>();
      pageResponse.setData(List.of(logVo));
      pageResponse.setTotal(1L);

      when(messageService.pageLog(any(MessageLogQueryDTO.class))).thenReturn(pageResponse);

      mockMvc.perform(get("/message/log/page")
              .param("pageNum", "1")
              .param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.total").value(1))
          .andExpect(jsonPath("$.data.data[0].msgId").value("msg-001"));
    }
  }

  @Nested
  @DisplayName("/message/cancelScheduled 接口测试")
  class CancelScheduledEndpoint {

    @Test
    @DisplayName("取消定时消息应返回操作结果")
    void cancelScheduled_shouldReturnResult() throws Exception {
      MessageSendResultVO result = MessageSendResultVO.ok("SMS", "trace-cancel");
      when(messageService.cancelScheduledMessage("msg-scheduled-001")).thenReturn(result);

      mockMvc.perform(post("/message/cancelScheduled")
              .param("msgId", "msg-scheduled-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.isSuccess").value(true));
    }
  }

  @Nested
  @DisplayName("/message/batch/{batchId}/progress 接口测试")
  class BatchProgressEndpoint {

    @Test
    @DisplayName("查询批次进度应返回分页日志")
    void batchProgress_shouldReturnPagedResult() throws Exception {
      MsgLogVO logVo = new MsgLogVO();
      logVo.setMsgId("msg-batch-001");
      logVo.setBatchId("batch-001");
      logVo.setStatus("SUCCESS");

      PageResponse<List<MsgLogVO>> pageResponse = new PageResponse<>();
      pageResponse.setData(List.of(logVo));
      pageResponse.setTotal(1L);

      when(messageService.pageLog(any(MessageLogQueryDTO.class))).thenReturn(pageResponse);

      mockMvc.perform(get("/message/batch/batch-001/progress")
              .param("page", "1")
              .param("size", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.total").value(1));
    }
  }
}
