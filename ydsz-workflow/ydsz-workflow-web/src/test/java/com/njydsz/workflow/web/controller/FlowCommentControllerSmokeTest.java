package com.njydsz.workflow.web.controller;

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

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.server.service.impl.notification.FlowCommentServiceImpl;

/**
 * {@link FlowCommentController} Smoke Test。
 *
 * <p>验证流程评论核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowCommentControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FlowCommentServiceImpl commentService;

  @org.mockito.InjectMocks
  private FlowCommentController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /workflow/comment/instance/{instanceId}")
  class ListByInstance {

    @Test
    @DisplayName("should return 200 when list by instance succeeds")
    void should_return_200_when_list_by_instance_succeeds() throws Exception {
      when(commentService.listByInstance(anyString())).thenReturn(List.of());

      mockMvc.perform(get("/workflow/comment/instance/{instanceId}", "inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/comment/quick")
  class ListQuickComments {

    @Test
    @DisplayName("should return 200 when list quick comments succeeds")
    void should_return_200_when_list_quick_comments_succeeds() throws Exception {
      when(commentService.listQuickComments(anyString(), anyString())).thenReturn(List.of());

      mockMvc.perform(get("/workflow/comment/quick"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
