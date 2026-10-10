package com.njydsz.message.web.controller;

import static org.mockito.ArgumentMatchers.any;
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
import com.njydsz.message.domain.dto.MessageStatsDTO;
import com.njydsz.message.server.service.core.MessageStatsService;
import com.njydsz.message.web.controller.core.MessageStatsController;

/**
 * {@link MessageStatsController} Smoke Test。
 *
 * <p>验证消息统计看板端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class MessageStatsControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private MessageStatsService messageStatsService;

  @org.mockito.InjectMocks
  private MessageStatsController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/stats/overview")
  class Overview {

    @Test
    @DisplayName("should return 200 when overview succeeds")
    void should_return_200_when_overview_succeeds() throws Exception {
      MessageStatsDTO dto = new MessageStatsDTO();
      when(messageStatsService.getOverview(any(), any())).thenReturn(dto);

      mockMvc.perform(get("/message/stats/overview"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
