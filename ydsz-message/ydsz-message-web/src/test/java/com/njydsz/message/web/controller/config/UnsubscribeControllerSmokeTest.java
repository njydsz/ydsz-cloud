package com.njydsz.message.web.controller.config;

import static org.mockito.ArgumentMatchers.any;
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
import com.njydsz.message.domain.dto.UnsubscribeQueryDTO;
import com.njydsz.message.domain.vo.MsgSubscriptionVO;
import com.njydsz.message.server.service.config.UnsubscribeService;

/**
 * {@link UnsubscribeController} Smoke Test。
 *
 * <p>验证退订管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class UnsubscribeControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private UnsubscribeService unsubscribeService;

  @org.mockito.InjectMocks
  private UnsubscribeController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/unsubscribe/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page unsubscribed query succeeds")
    void should_return_200_when_page_unsubscribed_query_succeeds() throws Exception {
      PageResponse<List<MsgSubscriptionVO>> pageResult = PageResponse.success(0L, 1L, 20L, List.of());
      when(unsubscribeService.pageUnsubscribed(any(UnsubscribeQueryDTO.class))).thenReturn(pageResult);

      mockMvc.perform(get("/message/unsubscribe/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
