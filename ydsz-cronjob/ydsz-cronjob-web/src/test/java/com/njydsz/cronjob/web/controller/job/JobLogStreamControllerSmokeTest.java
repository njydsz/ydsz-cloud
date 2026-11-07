package com.njydsz.cronjob.web.controller.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.server.core.logger.LogStreamManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link JobLogStreamController} Smoke Test。
 *
 * <p>覆盖 SSE 日志流端点 {@code GET /cronjob/log/stream/{logId}}。
 *
 * <p>验证 {@code produces=text/event-stream} 契约、正常订阅把 {@link LogStreamManager} 返回的 emitter 作为异步结果透出、 以及 {@code logId} 为空时 Controller 自建 emitter 而不调用 manager 的负例。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class JobLogStreamControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private LogStreamManager logStreamManager;

  @org.mockito.InjectMocks
  private JobLogStreamController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Test
  @DisplayName("should declare text/event-stream and hand the manager emitter back as async result")
  void should_stream_sse() throws Exception {
    SseEmitter emitter = new SseEmitter(1000L);
    when(logStreamManager.subscribe("log-001")).thenReturn(emitter);

    MvcResult result =
        mockMvc
            .perform(
                get("/cronjob/log/stream/{logId}", "log-001")
                    .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andReturn();

    assertThat(result.getAsyncResult()).isSameAs(emitter);
    verify(logStreamManager).subscribe("log-001");
  }

  @Test
  @DisplayName("should skip manager when logId is blank (empty path variable)")
  void should_skip_manager_for_blank_log_id() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/cronjob/log/stream/{logId}", "").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andReturn();

    // Controller 自建并 complete 的 emitter 作为异步结果返回，且不触达 manager
    assertThat(result.getAsyncResult()).isInstanceOf(SseEmitter.class);
    verify(logStreamManager, never()).subscribe(anyString());
  }
}
