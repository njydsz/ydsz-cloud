package com.njydsz.message.web.controller.archive;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.service.archive.MessageArchiveService;

/**
 * {@link MessageArchiveController} Smoke Test。
 *
 * <p>验证消息归档搜索端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class MessageArchiveControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private MessageArchiveService messageArchiveService;

  @org.mockito.InjectMocks
  private MessageArchiveController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/archive/search")
  class Search {

    @Test
    @DisplayName("should return 200 when search succeeds")
    void should_return_200_when_search_succeeds() throws Exception {
      PageResponse<List<MsgLogVO>> result = PageResponse.success(0L, 1L, 20L, List.of());
      when(messageArchiveService.search(
          any(), any(), any(), any(), any(), any(), any(), any(int.class), any(int.class)))
          .thenReturn(result);

      mockMvc.perform(get("/message/archive/search").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
