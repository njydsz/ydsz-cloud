package com.njydsz.cronjob.web.controller.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.njydsz.cronjob.domain.service.JobWebhookService;
import com.njydsz.cronjob.domain.vo.JobWebhookVO;

/**
 * {@link JobWebhookController} Smoke Test。
 *
 * <p>验证 WebHook 事件订阅端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class JobWebhookControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private JobWebhookService jobWebhookService;

  @org.mockito.InjectMocks
  private JobWebhookController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /cronjob/webhook/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page succeeds")
    void should_return_200_when_page_succeeds() throws Exception {
      PageResponse<List<JobWebhookVO>> result = PageResponse.success(0L, 1L, 20L, List.of());
      when(jobWebhookService.page(anyInt(), anyInt(), any(), any())).thenReturn(result);

      mockMvc.perform(get("/cronjob/webhook/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
