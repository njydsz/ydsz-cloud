package com.njydsz.cronjob.web.controller.monitor;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.domain.service.JobDiagnosisService;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.executor.RunningTaskCounter;
import com.njydsz.cronjob.server.core.redis.CronjobRedisOps;

/**
 * {@link JobDiagnosisController} Smoke Test。
 *
 * <p>验证任务诊断端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class JobDiagnosisControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private JobDiagnosisService jobDiagnosisService;

  @org.mockito.Mock
  private CronjobProperties cronjobProperties;

  @org.mockito.Mock
  private CronjobRedisOps cronjobRedisOps;

  @org.mockito.Mock
  private ObjectProvider<RunningTaskCounter> runningTaskCounterProvider;

  @org.mockito.InjectMocks
  private JobDiagnosisController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /cronjob/monitor/diagnosis/{jobKey}")
  class Diagnose {

    @Test
    @DisplayName("should return 200 when diagnose succeeds")
    void should_return_200_when_diagnose_succeeds() throws Exception {
      when(jobDiagnosisService.findLatestByJobKey("job-key-001")).thenReturn(Optional.empty());
      when(jobDiagnosisService.findByJobKey("job-key-001", 5)).thenReturn(List.of());

      mockMvc.perform(get("/cronjob/monitor/diagnosis/{jobKey}", "job-key-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
