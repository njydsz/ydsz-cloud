package com.njydsz.cronjob.web.controller.export;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.TenantContext;
import com.njydsz.common.core.context.TenantContextHolder;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.domain.vo.ExportTaskVO;
import com.njydsz.cronjob.server.service.export.ExportTaskService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ExportTaskController} Smoke Test。
 *
 * <p>覆盖异步导出任务端点 {@code /cronjob/export-tasks/**}： 提交 / 查询 / 列表 / 取消 / 下载链接。
 *
 * <p>验证 {@code Map<String,Object>} 请求体绑定（含默认值回退）、租户/用户上下文透传、 成功信封字段，以及 {@code download} 端点的两个负例分支（任务不存在 / 未完成）。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class ExportTaskControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private ExportTaskService exportTaskService;

  @org.mockito.InjectMocks
  private ExportTaskController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    // Controller 通过 AuthContextUtils 读取当前用户；测试中手工注入登录用户
    LoginUser user = LoginUser.builder().userId("user-001").tenantId("1").build();
    AuthContextUtils.setCurrent(user);
    TenantContextHolder.set(TenantContext.builder("1").build());
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
    AuthContextUtils.clear();
  }

  private ExportTaskVO taskVo(String status) {
    ExportTaskVO vo = new ExportTaskVO();
    vo.setId("task-001");
    vo.setTaskName("导出任务");
    vo.setTaskType("JOB_EXPORT");
    vo.setStatus(status);
    vo.setProgressPercent("SUCCEEDED".equals(status) ? 100 : 40);
    vo.setDownloadUrl("https://cdn.example.com/export/task-001.xlsx");
    return vo;
  }

  @Nested
  @DisplayName("POST /cronjob/export-tasks")
  class Submit {

    @Test
    @DisplayName("should bind Map body and return taskId")
    void should_submit() throws Exception {
      when(exportTaskService.submitTask(
              eq("cronjob"), eq("JOB_EXPORT"), eq("任务导出"), eq(Map.of("jobId", "job-001")),
              eq("user-001"), eq("1")))
          .thenReturn("task-001");

      mockMvc.perform(
              post("/cronjob/export-tasks")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"taskType\":\"JOB_EXPORT\",\"taskName\":\"任务导出\",\"params\":{\"jobId\":\"job-001\"}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("task-001"));
    }

    @Test
    @DisplayName("should fall back to DEFAULT_EXPORT taskType when body omits it")
    void should_default_task_type() throws Exception {
      when(exportTaskService.submitTask(
              eq("cronjob"), eq("DEFAULT_EXPORT"), eq("导出任务"), eq(Map.of()),
              eq("user-001"), eq("1")))
          .thenReturn("task-002");

      mockMvc.perform(
              post("/cronjob/export-tasks")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("task-002"));
    }
  }

  @Nested
  @DisplayName("GET /cronjob/export-tasks/{id}")
  class GetTask {

    @Test
    @DisplayName("should return task detail in success envelope")
    void should_get_task() throws Exception {
      when(exportTaskService.getTask("task-001", "1")).thenReturn(taskVo("PROCESSING"));

      mockMvc.perform(get("/cronjob/export-tasks/{id}", "task-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("task-001"))
          .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }
  }

  @Nested
  @DisplayName("GET /cronjob/export-tasks/my")
  class ListMyTasks {

    @Test
    @DisplayName("should default limit to 20 and return task list")
    void should_list_my_tasks() throws Exception {
      when(exportTaskService.listUserTasks("1", "user-001", 20))
          .thenReturn(List.of(taskVo("SUCCEEDED")));

      mockMvc.perform(get("/cronjob/export-tasks/my"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].id").value("task-001"));

      verify(exportTaskService).listUserTasks("1", "user-001", 20);
    }

    @Test
    @DisplayName("should bind explicit limit param")
    void should_bind_limit() throws Exception {
      when(exportTaskService.listUserTasks("1", "user-001", 5)).thenReturn(List.of());

      mockMvc.perform(get("/cronjob/export-tasks/my").param("limit", "5"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").isEmpty());
    }
  }

  @Nested
  @DisplayName("DELETE /cronjob/export-tasks/{id}")
  class Cancel {

    @Test
    @DisplayName("should cancel task and return boolean result")
    void should_cancel() throws Exception {
      when(exportTaskService.cancelTask("task-001", "user-001", "1")).thenReturn(true);

      mockMvc.perform(delete("/cronjob/export-tasks/{id}", "task-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("should return false when task already terminal")
    void should_return_false_when_terminal() throws Exception {
      when(exportTaskService.cancelTask("task-001", "user-001", "1")).thenReturn(false);

      mockMvc.perform(delete("/cronjob/export-tasks/{id}", "task-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(false));
    }
  }

  @Nested
  @DisplayName("GET /cronjob/export-tasks/{id}/download")
  class Download {

    @Test
    @DisplayName("should return download url for SUCCEEDED task")
    void should_return_download_url() throws Exception {
      when(exportTaskService.getTask("task-001", "1")).thenReturn(taskVo("SUCCEEDED"));

      mockMvc.perform(get("/cronjob/export-tasks/{id}/download", "task-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("https://cdn.example.com/export/task-001.xlsx"));
    }

    @Test
    @DisplayName("should return C99999 error envelope when task not found")
    void should_reject_missing_task() throws Exception {
      when(exportTaskService.getTask("task-404", "1")).thenReturn(null);

      mockMvc.perform(get("/cronjob/export-tasks/{id}/download", "task-404"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("C99999"))
          .andExpect(jsonPath("$.msg").value("cronjob.export.task.not.found"));
    }

    @Test
    @DisplayName("should return C99999 error envelope when task not completed")
    void should_reject_not_completed() throws Exception {
      when(exportTaskService.getTask("task-001", "1")).thenReturn(taskVo("PROCESSING"));

      mockMvc.perform(get("/cronjob/export-tasks/{id}/download", "task-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("C99999"))
          .andExpect(jsonPath("$.msg").value("cronjob.export.task.not.completed"));
    }
  }
}
