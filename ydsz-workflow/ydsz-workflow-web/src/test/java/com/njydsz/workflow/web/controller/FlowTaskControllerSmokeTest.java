package com.njydsz.workflow.web.controller;

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
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.service.FlowTaskService;
import com.njydsz.workflow.server.service.FlowTodoCountPushService;

/**
 * {@link FlowTaskController} Smoke Test。
 *
 * <p>验证任务核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowTaskControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FlowTaskService taskService;

  @org.mockito.Mock
  private WorkflowFacade workflowFacade;

  @org.mockito.Mock
  private FlowTodoCountPushService todoCountPushService;

  @org.mockito.InjectMocks
  private FlowTaskController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /workflow/engine/task/todo")
  class Todo {

    @Test
    @DisplayName("should return 200 when todo task list query succeeds")
    void should_return_200_when_todo_task_list_succeeds() throws Exception {
      FlowRunTaskVO vo = new FlowRunTaskVO();
      vo.setId("task-001");
      PageResponse<List<FlowRunTaskVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(taskService.pageTodoVO(anyString(), anyString(), any(), any(), any(), any(), anyInt(), anyInt()))
          .thenReturn(pageResult);

      mockMvc.perform(get("/workflow/engine/task/todo")
              .param("pageNum", "1")
              .param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/task/{taskId}")
  class TaskDetail {

    @Test
    @DisplayName("should return 200 when task detail query succeeds")
    void should_return_200_when_task_detail_succeeds() throws Exception {
      when(workflowFacade.getTaskDetail(anyString())).thenReturn(java.util.Map.of());

      mockMvc.perform(get("/workflow/engine/task/{taskId}", "task-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
