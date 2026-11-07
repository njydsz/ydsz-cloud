package com.njydsz.cronjob.web.controller.job;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.domain.job.MapContext;
import com.njydsz.cronjob.domain.job.MapProcessor;
import com.njydsz.cronjob.domain.job.ProcessResult;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.core.dispatch.RemoteSubTaskRequest;
import com.njydsz.cronjob.server.core.dispatch.RemoteTaskRequest;
import com.njydsz.cronjob.server.core.dispatch.TaskDispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link InternalJobController} Smoke Test。
 *
 * <p>覆盖集群节点间远程派发接收端的三个端点：
 *
 * <ul>
 *   <li>{@code POST /cronjob/internal/execute} — 单分片远程派发
 *   <li>{@code POST /cronjob/internal/executeSubTask} — MapReduce 子任务派发
 *   <li>{@code POST /cronjob/internal/executeBatch} — 批量分片派发
 * </ul>
 *
 * <p>验证请求体绑定（{@link RemoteTaskRequest} / {@link RemoteSubTaskRequest}）、成功响应信封字段、 参数校验负例（{@code A10002}），以及「执行异常吞掉返回 success(null)」的契约语义。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class InternalJobControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private TaskDispatcher taskDispatcher;

  @org.mockito.Mock
  private ApplicationContext applicationContext;

  @org.mockito.InjectMocks
  private InternalJobController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  private RemoteTaskRequest taskRequest(String jobKey) {
    JobVO job = new JobVO();
    job.setJobKey(jobKey);
    job.setJobName("remote-job");
    return new RemoteTaskRequest(job, "CRON", 0, 2, "trace-001");
  }

  @Nested
  @DisplayName("POST /cronjob/internal/execute")
  class Execute {

    @Test
    @DisplayName("should bind RemoteTaskRequest and return logId in success envelope")
    void should_bind_and_return_log_id() throws Exception {
      when(taskDispatcher.executeLocally(any(JobVO.class), eq("CRON"), eq(0), eq(2)))
          .thenReturn("log-1001");

      mockMvc.perform(
              post("/cronjob/internal/execute")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper().toJson(taskRequest("job-key-001"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("log-1001"));

      ArgumentCaptor<JobVO> jobCaptor = ArgumentCaptor.forClass(JobVO.class);
      verify(taskDispatcher).executeLocally(jobCaptor.capture(), eq("CRON"), eq(0), eq(2));
      assertThat(jobCaptor.getValue().getJobKey()).isEqualTo("job-key-001");
    }

    @Test
    @DisplayName("should return A10002 error envelope when jobKey is null")
    void should_reject_missing_job_key() throws Exception {
      JobVO job = new JobVO();
      job.setJobName("no-key-job");
      RemoteTaskRequest request = new RemoteTaskRequest(job, "CRON", -1, 1, null);

      mockMvc.perform(
              post("/cronjob/internal/execute")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper().toJson(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A10002"))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(taskDispatcher, never()).executeLocally(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("should return success(null) when dispatcher throws, without surfacing error")
    void should_swallow_execution_exception() throws Exception {
      when(taskDispatcher.executeLocally(any(JobVO.class), any(), anyInt(), anyInt()))
          .thenThrow(new IllegalStateException("executor boom"));

      mockMvc.perform(
              post("/cronjob/internal/execute")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper().toJson(taskRequest("job-key-boom"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());
    }
  }

  @Nested
  @DisplayName("POST /cronjob/internal/executeSubTask")
  class ExecuteSubTask {

    private RemoteSubTaskRequest subTaskRequest() {
      return new RemoteSubTaskRequest(
          "job-001", "log-2001", "job-key-001", "demoProcessor", "shard-0", "{}", "trace-002");
    }

    @Test
    @DisplayName("should resolve MapProcessor bean, bind MapContext and return ProcessResult")
    void should_resolve_processor_and_return_result() throws Exception {
      MapProcessor processor =
          context -> {
            // 断言 Controller 已把 RemoteSubTaskRequest 字段正确复制进 MapContext
            assertThat(context.getJobKey()).isEqualTo("job-key-001");
            assertThat(context.getTaskName()).isEqualTo("shard-0");
            assertThat(context.getTaskParams()).isEqualTo("{}");
            return ProcessResult.success("{\"sum\":42}");
          };
      when(applicationContext.getBean("demoProcessor", MapProcessor.class)).thenReturn(processor);

      mockMvc.perform(
              post("/cronjob/internal/executeSubTask")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper().toJson(subTaskRequest())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.isSuccess").value(true))
          .andExpect(jsonPath("$.data.result").value("{\"sum\":42}"));

      verify(applicationContext).getBean("demoProcessor", MapProcessor.class);
    }

    @Test
    @DisplayName("should return A10002 when jobKey/handler are null")
    void should_reject_missing_params() throws Exception {
      RemoteSubTaskRequest request =
          new RemoteSubTaskRequest("job-001", "log-2001", null, null, "shard-0", null, null);

      mockMvc.perform(
              post("/cronjob/internal/executeSubTask")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper().toJson(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A10002"));

      verify(applicationContext, never()).getBean(any(String.class), eq(MapProcessor.class));
    }

    @Test
    @DisplayName("should return failed ProcessResult when processor bean lookup fails")
    void should_return_failed_result_when_bean_missing() throws Exception {
      when(applicationContext.getBean("missingProcessor", MapProcessor.class))
          .thenThrow(new NoSuchBeanDefinitionException("missingProcessor"));

      RemoteSubTaskRequest request =
          new RemoteSubTaskRequest(
              "job-001", "log-2002", "job-key-001", "missingProcessor", "shard-0", "{}", "trace-003");

      mockMvc.perform(
              post("/cronjob/internal/executeSubTask")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper().toJson(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.isSuccess").value(false))
          .andExpect(jsonPath("$.data.errorMessage").exists());
    }

    @Test
    @DisplayName("should return success result when processor returns null")
    void should_normalize_null_processor_result() throws Exception {
      when(applicationContext.getBean("nullProcessor", MapProcessor.class))
          .thenReturn(context -> null);

      RemoteSubTaskRequest request =
          new RemoteSubTaskRequest(
              "job-001", "log-2003", "job-key-001", "nullProcessor", "shard-0", "{}", "trace-004");

      mockMvc.perform(
              post("/cronjob/internal/executeSubTask")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper().toJson(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.isSuccess").value(true));
    }
  }

  @Nested
  @DisplayName("POST /cronjob/internal/executeBatch")
  class ExecuteBatch {

    @Test
    @DisplayName("should execute every shard and return logId list")
    void should_execute_all_shards() throws Exception {
      when(taskDispatcher.executeLocally(any(JobVO.class), any(), anyInt(), anyInt()))
          .thenReturn("log-batch-1", "log-batch-2");

      List<RemoteTaskRequest> requests =
          List.of(taskRequest("job-key-A"), taskRequest("job-key-B"));

      mockMvc.perform(
              post("/cronjob/internal/executeBatch")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper().toJson(requests)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0]").value("log-batch-1"))
          .andExpect(jsonPath("$.data[1]").value("log-batch-2"));

      verify(taskDispatcher, times(2)).executeLocally(any(JobVO.class), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("should return empty list when request body is empty array")
    void should_return_empty_list_for_empty_body() throws Exception {
      mockMvc.perform(
              post("/cronjob/internal/executeBatch")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("[]"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").isEmpty());

      verify(taskDispatcher, never()).executeLocally(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("should map invalid shard element to null and keep going")
    void should_skip_invalid_shard_element() throws Exception {
      when(taskDispatcher.executeLocally(any(JobVO.class), any(), anyInt(), anyInt()))
          .thenReturn("log-ok");

      JobVO noKey = new JobVO();
      RemoteTaskRequest invalid = new RemoteTaskRequest(noKey, "CRON", -1, 1, null);
      List<RemoteTaskRequest> requests = List.of(taskRequest("job-key-ok"), invalid);

      mockMvc.perform(
              post("/cronjob/internal/executeBatch")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper().toJson(requests)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0]").value("log-ok"))
          .andExpect(jsonPath("$.data[1]").doesNotExist());
    }
  }
}
