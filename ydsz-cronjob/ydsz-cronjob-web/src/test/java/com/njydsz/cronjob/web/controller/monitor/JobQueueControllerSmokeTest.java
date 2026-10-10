package com.njydsz.cronjob.web.controller.monitor;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher;

/**
 * {@link JobQueueController} Smoke Test。
 *
 * <p>验证执行队列状态端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class JobQueueControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private ObjectProvider<DefaultTaskDispatcher> taskDispatcherProvider;

  @org.mockito.InjectMocks
  private JobQueueController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /cronjob/queue/status")
  class GetQueueStatus {

    @Test
    @DisplayName("should return 200 when get queue status succeeds")
    void should_return_200_when_get_queue_status_succeeds() throws Exception {
      when(taskDispatcherProvider.getIfAvailable()).thenReturn(null);

      mockMvc.perform(get("/cronjob/queue/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
