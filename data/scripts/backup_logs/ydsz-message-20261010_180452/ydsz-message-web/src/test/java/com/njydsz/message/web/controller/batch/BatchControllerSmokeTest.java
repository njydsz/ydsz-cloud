package com.njydsz.message.web.controller.batch;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.domain.dto.BatchProgressDTO;
import com.njydsz.message.server.service.SseEmitterService;
import com.njydsz.message.server.service.batch.BatchService;

/**
 * {@link BatchController} Smoke Test。
 *
 * <p>验证批量发送端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class BatchControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private BatchService batchService;

  @org.mockito.Mock
  private SseEmitterService sseEmitterService;

  @org.mockito.InjectMocks
  private BatchController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/batch/progress/{batchId}")
  class GetProgress {

    @Test
    @DisplayName("should return 200 when get progress succeeds")
    void should_return_200_when_get_progress_succeeds() throws Exception {
      BatchProgressDTO dto = new BatchProgressDTO();
      dto.setBatchId("batch-001");
      when(batchService.getProgress("batch-001")).thenReturn(dto);

      mockMvc.perform(get("/message/batch/progress/{batchId}", "batch-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
