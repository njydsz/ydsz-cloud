package com.njydsz.cronjob.web.controller.dag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.server.core.dag.DagInstanceControlService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link DagInstanceControlController} Smoke Test。
 *
 * <p>覆盖 DAG 实例运行时控制端点 {@code /cronjob/dag/instance/{instanceId}/{pause|resume|cancel|retryNode}}。
 *
 * <p>验证路径变量 + 请求参数（{@code jobKey}）绑定、成功信封 {@code data=true}、 失败分支返回 {@code B92103/B92104} 错误码（{@code YdszResponse.error} 直接返回，HTTP 仍 200）。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class DagInstanceControlControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private DagInstanceControlService dagInstanceControlService;

  @org.mockito.InjectMocks
  private DagInstanceControlController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("POST /cronjob/dag/instance/{instanceId}/pause")
  class Pause {

    @Test
    @DisplayName("should return data=true when pause succeeds")
    void should_pause_success() throws Exception {
      when(dagInstanceControlService.pause("inst-001")).thenReturn(true);

      mockMvc.perform(post("/cronjob/dag/instance/{instanceId}/pause", "inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(dagInstanceControlService).pause("inst-001");
    }

    @Test
    @DisplayName("should return B92103 when instance missing or non-RUNNING")
    void should_return_error_when_pause_fails() throws Exception {
      when(dagInstanceControlService.pause("inst-404")).thenReturn(false);

      mockMvc.perform(post("/cronjob/dag/instance/{instanceId}/pause", "inst-404"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("B92103"))
          .andExpect(jsonPath("$.msg").value("cronjob.dag.pause.failed"));
    }
  }

  @Nested
  @DisplayName("POST /cronjob/dag/instance/{instanceId}/resume")
  class Resume {

    @Test
    @DisplayName("should return data=true when resume succeeds")
    void should_resume_success() throws Exception {
      when(dagInstanceControlService.resume("inst-001")).thenReturn(true);

      mockMvc.perform(post("/cronjob/dag/instance/{instanceId}/resume", "inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("should return B92103 when instance missing or non-PAUSED")
    void should_return_error_when_resume_fails() throws Exception {
      when(dagInstanceControlService.resume("inst-404")).thenReturn(false);

      mockMvc.perform(post("/cronjob/dag/instance/{instanceId}/resume", "inst-404"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("B92103"))
          .andExpect(jsonPath("$.msg").value("cronjob.dag.resume.failed"));
    }
  }

  @Nested
  @DisplayName("POST /cronjob/dag/instance/{instanceId}/cancel")
  class Cancel {

    @Test
    @DisplayName("should return data=true when cancel succeeds")
    void should_cancel_success() throws Exception {
      when(dagInstanceControlService.cancel("inst-001")).thenReturn(true);

      mockMvc.perform(post("/cronjob/dag/instance/{instanceId}/cancel", "inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("should return B92103 when instance already terminal")
    void should_return_error_when_cancel_fails() throws Exception {
      when(dagInstanceControlService.cancel("inst-404")).thenReturn(false);

      mockMvc.perform(post("/cronjob/dag/instance/{instanceId}/cancel", "inst-404"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("B92103"))
          .andExpect(jsonPath("$.msg").value("cronjob.dag.cancel.failed"));
    }
  }

  @Nested
  @DisplayName("POST /cronjob/dag/instance/{instanceId}/retryNode")
  class RetryNode {

    @Test
    @DisplayName("should bind jobKey request param and return data=true")
    void should_retry_node_success() throws Exception {
      when(dagInstanceControlService.retryNode("inst-001", "job-key-A")).thenReturn(true);

      mockMvc.perform(
              post("/cronjob/dag/instance/{instanceId}/retryNode", "inst-001")
                  .param("jobKey", "job-key-A"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(dagInstanceControlService).retryNode("inst-001", "job-key-A");
    }

    @Test
    @DisplayName("should return B92104 when node missing or non-FAILED")
    void should_return_error_when_retry_fails() throws Exception {
      when(dagInstanceControlService.retryNode("inst-001", "job-key-B")).thenReturn(false);

      mockMvc.perform(
              post("/cronjob/dag/instance/{instanceId}/retryNode", "inst-001")
                  .param("jobKey", "job-key-B"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("B92104"))
          .andExpect(jsonPath("$.msg").value("cronjob.dag.retry.failed"));
    }

    @Test
    @DisplayName("should return 400 when jobKey param is missing (no service call)")
    void should_reject_missing_job_key_param() throws Exception {
      mockMvc.perform(post("/cronjob/dag/instance/{instanceId}/retryNode", "inst-001"))
          .andExpect(status().is4xxClientError());

      verify(dagInstanceControlService, never()).retryNode(any(), any());
    }
  }
}
