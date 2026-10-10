package com.njydsz.agent.web.controller.asynctask;

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

import com.njydsz.agent.server.asynctask.AsyncTaskService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link AsyncTaskController} Smoke Test.
 *
 * <p>Verify async task endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class AsyncTaskControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private AsyncTaskService asyncTaskService;

  @org.mockito.InjectMocks
  private AsyncTaskController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/async-task/{taskId}")
  class GetTask {

    @Test
    @DisplayName("should return 200 when get task succeeds")
    void should_return_200_when_get_task_succeeds() throws Exception {
      when(asyncTaskService.getTask("task-001")).thenReturn(null);

      mockMvc.perform(get("/agent/async-task/{taskId}", "task-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /agent/async-task/active")
  class ListActiveTasks {

    @Test
    @DisplayName("should return 200 when list active tasks succeeds")
    void should_return_200_when_list_active_tasks_succeeds() throws Exception {
      when(asyncTaskService.listActiveTasks("tenant-001")).thenReturn(List.of());

      mockMvc.perform(get("/agent/async-task/active")
              .param("tenantCode", "tenant-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
