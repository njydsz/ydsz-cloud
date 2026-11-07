package com.njydsz.cronjob.web.controller.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.domain.dto.post.JobPostDTO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.service.job.JobService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ClusterMigrationInternalController} Smoke Test。
 *
 * <p>覆盖集群漂移内部接收端 {@code /cronjob/internal/migrate/**} 的两个端点： 任务注册（register）与注销（unregister）。
 *
 * <p>验证 {@link JobVO} → {@link JobPostDTO} 的字段映射（Controller 逐字段复制）、 成功信封字段，以及 Service 抛异常时 Controller 转成 {@code SysException} 的负例。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class ClusterMigrationInternalControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;
  private MockMvc adviceMockMvc;

  @org.mockito.Mock
  private JobService jobService;

  @org.mockito.InjectMocks
  private ClusterMigrationInternalController controller;

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

  private JobVO jobVo() {
    JobVO vo = new JobVO();
    vo.setJobKey("migrated-key");
    vo.setJobName("migrated-job");
    vo.setJobGroup("group-A");
    vo.setScheduleType("CRON");
    vo.setCronExpression("0 0 2 * * ?");
    vo.setFixedRateMs(30000L);
    vo.setFixedDelayMs(5000L);
    vo.setHandler("demoHandler");
    vo.setParamsJson("{\"k\":1}");
    vo.setShardTotal(2);
    vo.setSlowThresholdMs(1000L);
    vo.setCluster("cluster-B");
    vo.setTimezone("Asia/Shanghai");
    vo.setStatus("NORMAL");
    return vo;
  }

  @Nested
  @DisplayName("POST /cronjob/internal/migrate/register")
  class Register {

    @Test
    @DisplayName("should map JobVO fields into JobPostDTO and return true")
    void should_map_and_register() throws Exception {
      when(jobService.register(any(JobPostDTO.class))).thenReturn(true);

      mockMvc.perform(
              post("/cronjob/internal/migrate/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper().toJson(jobVo())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      ArgumentCaptor<JobPostDTO> dtoCaptor = ArgumentCaptor.forClass(JobPostDTO.class);
      verify(jobService).register(dtoCaptor.capture());
      JobPostDTO dto = dtoCaptor.getValue();
      assertThat(dto.getJobKey()).isEqualTo("migrated-key");
      assertThat(dto.getJobName()).isEqualTo("migrated-job");
      assertThat(dto.getJobGroup()).isEqualTo("group-A");
      assertThat(dto.getScheduleType()).isEqualTo("CRON");
      assertThat(dto.getCronExpression()).isEqualTo("0 0 2 * * ?");
      assertThat(dto.getFixedRateMs()).isEqualTo(30000L);
      assertThat(dto.getFixedDelayMs()).isEqualTo(5000L);
      assertThat(dto.getHandler()).isEqualTo("demoHandler");
      assertThat(dto.getParamsJson()).isEqualTo("{\"k\":1}");
      assertThat(dto.getShardTotal()).isEqualTo(2);
      assertThat(dto.getSlowThresholdMs()).isEqualTo(1000L);
      assertThat(dto.getCluster()).isEqualTo("cluster-B");
      assertThat(dto.getTimezone()).isEqualTo("Asia/Shanghai");
      assertThat(dto.getStatus()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("should return success(false) when service reports duplicate registration")
    void should_return_false_when_service_rejects() throws Exception {
      when(jobService.register(any(JobPostDTO.class))).thenReturn(false);

      mockMvc.perform(
              post("/cronjob/internal/migrate/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper().toJson(jobVo())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("should convert service exception into B01052 + HTTP 500 error envelope")
    void should_convert_exception_to_sys_exception() throws Exception {
      when(jobService.register(any(JobPostDTO.class)))
          .thenThrow(new IllegalStateException("duplicate key"));

      adviceMockMvc
          .perform(
              post("/cronjob/internal/migrate/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper().toJson(jobVo())))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));
    }
  }

  @Nested
  @DisplayName("POST /cronjob/internal/migrate/unregister")
  class Unregister {

    @Test
    @DisplayName("should bind UnregisterRequest record and delegate by jobKey")
    void should_unregister() throws Exception {
      when(jobService.unregister("migrated-key")).thenReturn(true);

      mockMvc.perform(
              post("/cronjob/internal/migrate/unregister")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"jobKey\":\"migrated-key\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(jobService).unregister("migrated-key");
    }

    @Test
    @DisplayName("should return success(false) when jobKey unknown")
    void should_return_false_for_unknown_key() throws Exception {
      when(jobService.unregister("unknown-key")).thenReturn(false);

      mockMvc.perform(
              post("/cronjob/internal/migrate/unregister")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"jobKey\":\"unknown-key\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("should convert service exception into B01052 + HTTP 500 error envelope")
    void should_convert_exception() throws Exception {
      when(jobService.unregister("boom-key")).thenThrow(new IllegalStateException("db down"));

      adviceMockMvc
          .perform(
              post("/cronjob/internal/migrate/unregister")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"jobKey\":\"boom-key\"}"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));

      verify(jobService).unregister(eq("boom-key"));
      verify(jobService, never()).register(any());
    }
  }
}
