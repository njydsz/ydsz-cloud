package com.njydsz.cronjob.web.controller.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.service.job.JobService;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link InternalCronjobApiController} Smoke Test。
 *
 * <p>覆盖服务间 Feign 调用入口 {@code /internal/cronjob/**} 的五个端点： 触发（带/不带锁）、查询详情、暂停、恢复。
 *
 * <p>验证路径变量绑定、{@code holdLock} 请求参数分流（两个 {@code @PostMapping} 共用同一路径）、 成功信封字段，以及 Service 抛业务异常时 {@code MvcExceptionHandler} 产出的错误信封。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class InternalCronjobApiControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;
  private MockMvc adviceMockMvc;

  @org.mockito.Mock
  private JobService jobService;

  @org.mockito.InjectMocks
  private InternalCronjobApiController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    adviceMockMvc =
        standaloneSetupWithAdvice(
            new MvcExceptionHandler(
                new MockEnvironment(), null, absentProvider(), absentProvider(), absentProvider()),
            controller);
  }

  /**
   * 构造一个「容器里没有这个 bean」的 {@code ObjectProvider}，用于脱离 Spring 容器手工装配 advice。
   *
   * @param <T> Provider 承载的 bean 类型
   * @return 取不到任何 bean 的 provider
   */
  private static <T> ObjectProvider<T> absentProvider() {
    return new ObjectProvider<>() {
      @Override
      public T getIfAvailable() {
        return null;
      }

      @Override
      public T getIfUnique() {
        return null;
      }
    };
  }

  @Nested
  @DisplayName("POST /internal/cronjob/{id}/trigger")
  class Trigger {

    @Test
    @DisplayName("should delegate to trigger(id) when holdLock param is absent")
    void should_trigger_without_lock() throws Exception {
      when(jobService.trigger("job-001")).thenReturn("log-trigger-1");

      mockMvc.perform(post("/internal/cronjob/{id}/trigger", "job-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("log-trigger-1"));

      verify(jobService).trigger("job-001");
      verify(jobService, never()).trigger("job-001", false);
    }

    @Test
    @DisplayName("should route to trigger(id, holdLock) when holdLock param is present")
    void should_trigger_with_lock() throws Exception {
      when(jobService.trigger("job-001", true)).thenReturn("log-trigger-2");

      mockMvc.perform(post("/internal/cronjob/{id}/trigger", "job-001").param("holdLock", "true"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("log-trigger-2"));

      verify(jobService).trigger("job-001", true);
      verify(jobService, never()).trigger("job-001");
    }
  }

  @Nested
  @DisplayName("GET /internal/cronjob/{id}")
  class GetJobInfo {

    @Test
    @DisplayName("should return job detail in success envelope")
    void should_return_job_info() throws Exception {
      JobVO vo = new JobVO();
      vo.setId("job-001");
      vo.setJobKey("demo-key");
      vo.setJobName("demo-job");
      when(jobService.getById("job-001")).thenReturn(vo);

      mockMvc.perform(get("/internal/cronjob/{id}", "job-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("job-001"))
          .andExpect(jsonPath("$.data.jobKey").value("demo-key"));
    }

    @Test
    @DisplayName("should surface A01057 error envelope when service throws BusinessException")
    void should_surface_business_error() throws Exception {
      when(jobService.getById("job-404"))
          .thenThrow(BusinessException.of(CoreExceptionCode.BUSINESS_ERROR));

      adviceMockMvc
          .perform(get("/internal/cronjob/{id}", "job-404"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("A01057"));
    }
  }

  @Nested
  @DisplayName("POST /internal/cronjob/{id}/pause|resume")
  class PauseResume {

    @Test
    @DisplayName("should return success(null) envelope when pause succeeds")
    void should_pause() throws Exception {
      mockMvc.perform(post("/internal/cronjob/{id}/pause", "job-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(jobService).pause("job-001");
    }

    @Test
    @DisplayName("should return success(null) envelope when resume succeeds")
    void should_resume() throws Exception {
      mockMvc.perform(post("/internal/cronjob/{id}/resume", "job-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(jobService).resume("job-001");
    }

    @Test
    @DisplayName("should surface A01057 error envelope when pause throws")
    void should_surface_pause_error() throws Exception {
      doThrow(BusinessException.of(CoreExceptionCode.BUSINESS_ERROR))
          .when(jobService)
          .pause("job-boom");

      adviceMockMvc
          .perform(post("/internal/cronjob/{id}/pause", "job-boom"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("A01057"));
    }
  }
}
