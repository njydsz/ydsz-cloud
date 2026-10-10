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
import com.njydsz.workflow.domain.vo.FlowAttachmentVO;
import com.njydsz.workflow.server.service.FlowAttachmentService;

/**
 * {@link FlowAttachmentController} Smoke Test。
 *
 * <p>验证审批附件核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowAttachmentControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FlowAttachmentService attachmentService;

  @org.mockito.InjectMocks
  private FlowAttachmentController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /workflow/engine/attachment/task/{taskId}")
  class ListByTask {

    @Test
    @DisplayName("should return 200 when list task attachments succeeds")
    void should_return_200_when_list_task_attachments_succeeds() throws Exception {
      FlowAttachmentVO vo = new FlowAttachmentVO();
      vo.setId("att-001");
      when(attachmentService.listByTask(anyString())).thenReturn(List.of(vo));

      mockMvc.perform(get("/workflow/engine/attachment/task/{taskId}", "task-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/attachment/{attachmentId}/preview")
  class Preview {

    @Test
    @DisplayName("should return 200 when attachment preview succeeds")
    void should_return_200_when_attachment_preview_succeeds() throws Exception {
      when(attachmentService.previewAttachment(anyString())).thenReturn(null);

      mockMvc.perform(get("/workflow/engine/attachment/{attachmentId}/preview", "att-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
