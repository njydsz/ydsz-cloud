package com.njydsz.message.web.controller.internal;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.domain.dto.MessageItemRequestDTO;
import com.njydsz.message.domain.dto.MessageSendDTO;
import com.njydsz.message.domain.enums.core.SendStrategyEnum;
import com.njydsz.message.domain.vo.MessageSendResultVO;
import com.njydsz.message.server.service.core.MessageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link InternalMessageApiController} Smoke Test。
 *
 * <p>验证内部 Feign 入口 {@code POST /internal/message/send} 的真实 HTTP 路径/方法、请求体绑定、
 * 响应字段、服务调用与失败分支，防御接口签名变更未被发现的生产事件。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>成功发送：{@code result.isSuccess()=true} → 200 + {@code data=traceId}
 *   <li>失败发送：{@code result.isSuccess()=false} → 200 + {@code code=errorCode} + {@code msg=userMessage}
 *   <li>请求体绑定：JSON 字段（strategy/channel/receiver/templateCode）正确映射到 {@link MessageSendDTO}
 *   <li>参数透传：DTO → {@link MessageItemRequestDTO} 的 BeanUpdateUtil 拷贝字段可被服务层断言
 *   <li>请求体缺失 / 非法 JSON → 400（由框架绑定层拒绝）
 * </ul>
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class InternalMessageApiControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock private MessageService messageService;

  @InjectMocks private InternalMessageApiController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("POST /internal/message/send")
  class SendMessage {

    @Test
    @DisplayName("成功发送 → 200 + data 为 traceId，且服务收到透传的 DTO 字段")
    void should_return_trace_id_and_delegate_to_service() throws Exception {
      MessageSendResultVO result = MessageSendResultVO.ok("INAPP", "trace-001");
      ArgumentCaptor<MessageItemRequestDTO> captor =
          ArgumentCaptor.forClass(MessageItemRequestDTO.class);
      when(messageService.send(captor.capture())).thenReturn(result);

      mockMvc
          .perform(
              post("/internal/message/send")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"strategy\":\"SYNC\",\"channel\":\"INAPP\",\"receiver\":\"user-001\","
                          + "\"templateCode\":\"ORDER_NOTIFY\",\"content\":\"hello\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("trace-001"));

      MessageItemRequestDTO passed = captor.getValue();
      assertThat(passed).isNotNull();
      assertThat(passed.getChannel()).isEqualTo("INAPP");
      assertThat(passed.getReceiver()).isEqualTo("user-001");
      assertThat(passed.getTemplateCode()).isEqualTo("ORDER_NOTIFY");
      assertThat(passed.getContent()).isEqualTo("hello");
    }

    @Test
    @DisplayName("发送失败 → 200 + code=errorCode + msg=userMessage（服务返回失败结果，不抛异常）")
    void should_return_error_envelope_when_service_result_not_success() throws Exception {
      MessageSendResultVO fail =
          MessageSendResultVO.fail("B91103", "发送失败", "developer-detail", 5);
      when(messageService.send(any(MessageItemRequestDTO.class))).thenReturn(fail);

      mockMvc
          .perform(
              post("/internal/message/send")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"channel\":\"SMS\",\"receiver\":\"13800000000\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("B91103"))
          .andExpect(jsonPath("$.msg").value("发送失败"));
    }

    @Test
    @DisplayName("请求体缺失 strategy 字段 → 仍绑定为默认 SYNC（@NotNull 默认值），服务被调用")
    void should_default_strategy_when_field_absent() throws Exception {
      when(messageService.send(any(MessageItemRequestDTO.class)))
          .thenReturn(MessageSendResultVO.ok("INAPP", "trace-002"));

      mockMvc
          .perform(
              post("/internal/message/send")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"channel\":\"EMAIL\",\"receiver\":\"a@b.com\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("trace-002"));
    }

    @Test
    @DisplayName("请求体为空 → 400（HttpMessageNotReadable，框架绑定层拒绝，服务零调用）")
    void should_reject_empty_body() throws Exception {
      mockMvc
          .perform(post("/internal/message/send").contentType(MediaType.APPLICATION_JSON).content(""))
          .andExpect(status().isBadRequest());

      verify(messageService, never()).send(any(MessageItemRequestDTO.class));
    }

    @Test
    @DisplayName("请求体非法 JSON → 400（解析失败，服务零调用）")
    void should_reject_malformed_json() throws Exception {
      mockMvc
          .perform(
              post("/internal/message/send")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{not-json"))
          .andExpect(status().isBadRequest());

      verify(messageService, never()).send(any(MessageItemRequestDTO.class));
    }

    @Test
    @DisplayName("服务抛异常 → 异常冒泡（standalone 无 advice），服务调用已发生")
    void should_propagate_service_exception() throws Exception {
      when(messageService.send(any(MessageItemRequestDTO.class)))
          .thenThrow(new RuntimeException("channel unavailable"));

      assertThrows(
          Exception.class,
          () ->
              mockMvc.perform(
                  post("/internal/message/send")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"channel\":\"SMS\",\"receiver\":\"13800000000\"}")));

      verify(messageService).send(any(MessageItemRequestDTO.class));
    }

    @Test
    @DisplayName("strategy 字段绑定枚举：非法值 → 400（类型不匹配，服务零调用）")
    void should_reject_invalid_strategy_enum() throws Exception {
      mockMvc
          .perform(
              post("/internal/message/send")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"strategy\":\"NOT_A_STRATEGY\"}"))
          .andExpect(status().isBadRequest());

      verify(messageService, never()).send(any(MessageItemRequestDTO.class));
    }

    @Test
    @DisplayName("strategy 字段绑定枚举：BATCH 合法值可正常绑定")
    void should_accept_batch_strategy() throws Exception {
      when(messageService.send(any(MessageItemRequestDTO.class)))
          .thenReturn(MessageSendResultVO.ok("INAPP", "trace-batch"));

      mockMvc
          .perform(
              post("/internal/message/send")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"strategy\":\"BATCH\",\"batchId\":\"batch-001\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value("trace-batch"));
    }

    @Test
    @DisplayName("MessageSendDTO 默认 strategy 字段为 SYNC（防御 DTO 默认值被误改）")
    void dto_default_strategy_should_be_sync() {
      MessageSendDTO dto = new MessageSendDTO();
      assertThat(dto.getStrategy()).isEqualTo(SendStrategyEnum.SYNC);
    }

    @Test
    @DisplayName("params 请求体字段绑定到 Map 并透传到服务层")
    void should_bind_params_map() throws Exception {
      ArgumentCaptor<MessageItemRequestDTO> captor =
          ArgumentCaptor.forClass(MessageItemRequestDTO.class);
      when(messageService.send(captor.capture()))
          .thenReturn(MessageSendResultVO.ok("INAPP", "trace-params"));

      mockMvc
          .perform(
              post("/internal/message/send")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"channel\":\"SMS\",\"receiver\":\"13800000000\","
                          + "\"params\":{\"code\":\"1234\",\"name\":\"Alice\"}}"))
          .andExpect(status().isOk());

      Map<String, Object> params = captor.getValue().getParams();
      assertThat(params).isNotNull();
      assertThat(params).containsEntry("code", "1234").containsEntry("name", "Alice");
    }
  }
}
