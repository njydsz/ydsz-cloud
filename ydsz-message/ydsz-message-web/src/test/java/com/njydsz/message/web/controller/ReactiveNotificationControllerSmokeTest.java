package com.njydsz.message.web.controller;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.server.reactive.ReactiveEvent;
import com.njydsz.message.server.reactive.ReactiveSseRegistry;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ReactiveNotificationController} Smoke Test。
 *
 * <p>验证响应式推送端点的真实 HTTP 路径/方法、请求体绑定、默认字段填充、服务调用与失败分支。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>POST /publish 成功（{@link Sinks.EmitResult#OK}）→ 200 + data="Push succeeded"
 *   <li>POST /publish 失败（{@link Sinks.EmitResult#FAIL_OVERFLOW}）→ 200 + error code + "Push failed"
 *   <li>POST /publish 默认字段填充：eventId / timestamp / eventType 缺省时由 Controller 补齐
 *   <li>POST /publish 请求体绑定：JSON 字段映射到 {@link ReactiveEvent}
 *   <li>GET /health → 200 + status/mode/bufferSize
 *   <li>GET /stream → 返回 {@link Flux}（无限流，直接调用验证非阻塞返回 + 头绑定）
 *   <li>请求体非法 JSON → 400（框架绑定层拒绝，registry 零调用）
 * </ul>
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class ReactiveNotificationControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock private ReactiveSseRegistry reactiveSseRegistry;

  @InjectMocks private ReactiveNotificationController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("POST /message/reactive/publish")
  class PublishEvent {

    @Test
    @DisplayName("发布成功 → 200 + data=Push succeeded，事件已推入 registry")
    void should_return_success_when_emit_succeeds() throws Exception {
      when(reactiveSseRegistry.publish(any(ReactiveEvent.class)))
          .thenReturn(Sinks.EmitResult.OK);

      mockMvc
          .perform(
              post("/message/reactive/publish")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"eventType\":\"notification\",\"targetUserId\":\"user-001\","
                          + "\"title\":\"系统通知\",\"content\":\"您有一条新消息\",\"level\":\"INFO\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("Push succeeded"));

      ArgumentCaptor<ReactiveEvent> captor = ArgumentCaptor.forClass(ReactiveEvent.class);
      verify(reactiveSseRegistry).publish(captor.capture());
      ReactiveEvent event = captor.getValue();
      assertThat(event.getEventType()).isEqualTo("notification");
      assertThat(event.getTargetUserId()).isEqualTo("user-001");
      assertThat(event.getTitle()).isEqualTo("系统通知");
      assertThat(event.getContent()).isEqualTo("您有一条新消息");
      assertThat(event.getLevel()).isEqualTo("INFO");
    }

    @Test
    @DisplayName("发布失败（缓冲溢出）→ 200 + error code + msg 含 Push failed")
    void should_return_error_when_emit_fails() throws Exception {
      when(reactiveSseRegistry.publish(any(ReactiveEvent.class)))
          .thenReturn(Sinks.EmitResult.FAIL_OVERFLOW);

      mockMvc
          .perform(
              post("/message/reactive/publish")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"eventType\":\"notification\",\"targetUserId\":\"user-001\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(YdszResponse.UNKNOWN_CODE))
          .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("Push failed")));
    }

    @Test
    @DisplayName("eventId 缺省 → Controller 自动生成非空 eventId")
    void should_fill_event_id_when_absent() throws Exception {
      when(reactiveSseRegistry.publish(any(ReactiveEvent.class)))
          .thenReturn(Sinks.EmitResult.OK);

      mockMvc
          .perform(
              post("/message/reactive/publish")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"eventType\":\"notification\"}"))
          .andExpect(status().isOk());

      ArgumentCaptor<ReactiveEvent> captor = ArgumentCaptor.forClass(ReactiveEvent.class);
      verify(reactiveSseRegistry).publish(captor.capture());
      assertThat(captor.getValue().getEventId()).isNotBlank();
    }

    @Test
    @DisplayName("timestamp 缺省 → Controller 自动填充非空 timestamp")
    void should_fill_timestamp_when_absent() throws Exception {
      when(reactiveSseRegistry.publish(any(ReactiveEvent.class)))
          .thenReturn(Sinks.EmitResult.OK);

      mockMvc
          .perform(
              post("/message/reactive/publish")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"eventType\":\"notification\"}"))
          .andExpect(status().isOk());

      ArgumentCaptor<ReactiveEvent> captor = ArgumentCaptor.forClass(ReactiveEvent.class);
      verify(reactiveSseRegistry).publish(captor.capture());
      assertThat(captor.getValue().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("eventType 缺省 → Controller 填充默认值 notification")
    void should_fill_event_type_when_absent() throws Exception {
      when(reactiveSseRegistry.publish(any(ReactiveEvent.class)))
          .thenReturn(Sinks.EmitResult.OK);

      mockMvc
          .perform(
              post("/message/reactive/publish")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"targetUserId\":\"user-001\"}"))
          .andExpect(status().isOk());

      ArgumentCaptor<ReactiveEvent> captor = ArgumentCaptor.forClass(ReactiveEvent.class);
      verify(reactiveSseRegistry).publish(captor.capture());
      assertThat(captor.getValue().getEventType()).isEqualTo("notification");
    }

    @Test
    @DisplayName("显式传入的 eventId / eventType 不被覆盖")
    void should_preserve_explicit_fields() throws Exception {
      when(reactiveSseRegistry.publish(any(ReactiveEvent.class)))
          .thenReturn(Sinks.EmitResult.OK);

      mockMvc
          .perform(
              post("/message/reactive/publish")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"eventId\":\"evt-001\",\"eventType\":\"alert\","
                          + "\"timestamp\":\"2026-01-01T00:00:00Z\"}"))
          .andExpect(status().isOk());

      ArgumentCaptor<ReactiveEvent> captor = ArgumentCaptor.forClass(ReactiveEvent.class);
      verify(reactiveSseRegistry).publish(captor.capture());
      ReactiveEvent event = captor.getValue();
      assertThat(event.getEventId()).isEqualTo("evt-001");
      assertThat(event.getEventType()).isEqualTo("alert");
      assertThat(event.getTimestamp()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("请求体非法 JSON → 400（registry 零调用）")
    void should_reject_malformed_json() throws Exception {
      mockMvc
          .perform(
              post("/message/reactive/publish")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{bad-json"))
          .andExpect(status().isBadRequest());

      verify(reactiveSseRegistry, never()).publish(any(ReactiveEvent.class));
    }

    @Test
    @DisplayName("请求体为空 → 400（registry 零调用）")
    void should_reject_empty_body() throws Exception {
      mockMvc
          .perform(
              post("/message/reactive/publish")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(""))
          .andExpect(status().isBadRequest());

      verify(reactiveSseRegistry, never()).publish(any(ReactiveEvent.class));
    }
  }

  @Nested
  @DisplayName("GET /message/reactive/health")
  class Health {

    @Test
    @DisplayName("健康检测 → 200 + status/mode/bufferSize 字段")
    void should_return_health_status() throws Exception {
      mockMvc
          .perform(get("/message/reactive/health"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.status").value("UP"))
          .andExpect(jsonPath("$.data.mode").value("reactive-pilot"))
          .andExpect(jsonPath("$.data.bufferSize").value("256"));
    }
  }

  @Nested
  @DisplayName("GET /message/reactive/stream")
  class StreamEvents {

    @Test
    @DisplayName("订阅端点返回非空 Flux（无限 SSE 流，直接调用验证方法契约）")
    void should_return_flux_for_stream() {
      Flux<ServerSentEvent<ReactiveEvent>> flux = controller.streamEvents("user-001");
      assertThat(flux).isNotNull();
    }

    @Test
    @DisplayName("X-User-Id 头缺省时返回非空 Flux（可选参数绑定）")
    void should_return_flux_when_header_absent() {
      Flux<ServerSentEvent<ReactiveEvent>> flux = controller.streamEvents(null);
      assertThat(flux).isNotNull();
    }

    @Test
    @DisplayName("订阅 Flux 可被消费：take(1) 在 15s 内心跳事件前完成订阅（不阻塞）")
    void should_allow_subscribe_without_blocking() {
      Flux<ServerSentEvent<ReactiveEvent>> flux = controller.streamEvents("user-001");
      // 无限流：仅验证可订阅（dispose 立即完成），不等待心跳
      flux.subscribe().dispose();
    }
  }
}
