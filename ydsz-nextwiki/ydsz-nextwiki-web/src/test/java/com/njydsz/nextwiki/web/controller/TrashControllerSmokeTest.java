package com.njydsz.nextwiki.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.server.service.TrashApplicationService;

/**
 * {@link TrashController} Smoke Test。
 *
 * <p>验证回收站端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class TrashControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private TrashApplicationService trashApplicationService;

  @org.mockito.InjectMocks
  private TrashController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/trash/list")
  class ListTrash {

    @Test
    @DisplayName("should return 200 when list trash succeeds")
    void should_return_200_when_list_trash_succeeds() throws Exception {
      when(trashApplicationService.listTrash("user-001")).thenReturn(java.util.List.of());

      mockMvc.perform(get("/nextwiki/trash/list")
              .header("X-User-Id", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
