package com.njydsz.cronjob.web.controller.job;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.cronjob.server.core.logger.LogStreamManager;

/**
 * JobLogStreamController 集成测试。
 *
 * <p>验证 SSE 日志流端点的订阅逻辑：
 * <ul>
 *   <li>正常 logId 时建立 SSE 连接并订阅</li>
 *   <li>空 logId 时直接拒绝</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ExtendWith(MockitoExtension.class)
class JobLogStreamControllerTest {

  private MockMvc mockMvc;

  @Mock
  private LogStreamManager logStreamManager;

  @InjectMocks
  private JobLogStreamController logStreamController;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(logStreamController).build();
  }

  @Nested
  @DisplayName("GET /cronjob/log/stream/{logId} - stream")
  class Stream {

    @Test
    @DisplayName("有效 logId 时返回 200 + async started")
    void shouldEstablishSseConnectionForValidLogId() throws Exception {
      SseEmitter mockEmitter = new SseEmitter();
      when(logStreamManager.subscribe("log-001")).thenReturn(mockEmitter);

      mockMvc.perform(get("/cronjob/log/stream/{logId}", "log-001"))
          .andExpect(status().isOk())
          .andExpect(request().asyncStarted());

      verify(logStreamManager, times(1)).subscribe("log-001");
    }

    @Test
    @DisplayName("空 logId 时建立连接后立即完成（返回 200）")
    void shouldImmediatelyCompleteForBlankLogId() throws Exception {
      mockMvc.perform(get("/cronjob/log/stream/{logId}", " "))
          .andExpect(status().isOk())
          .andExpect(request().asyncStarted());

      verify(logStreamManager, times(0)).subscribe(anyString());
    }
  }
}
