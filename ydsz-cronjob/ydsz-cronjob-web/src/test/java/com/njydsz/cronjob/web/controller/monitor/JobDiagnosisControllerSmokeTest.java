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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.domain.service.JobDiagnosisService;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.config.ExecutorConfig;
import com.njydsz.cronjob.server.config.LeaderConfig;
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

  @Mock
  private JobDiagnosisService jobDiagnosisService;

  @Mock
  private CronjobProperties cronjobProperties;

  @Mock
  private CronjobRedisOps cronjobRedisOps;

  @Mock
  private ObjectProvider<RunningTaskCounter> runningTaskCounterProvider;

  @InjectMocks
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
      // 配置摘要读取 getExecutor()/getLeader()，Mock 默认返回 null 会触发 NPE，故给出真实默认配置
      when(cronjobProperties.getExecutor()).thenReturn(new ExecutorConfig());
      when(cronjobProperties.getLeader()).thenReturn(new LeaderConfig());

      mockMvc.perform(get("/cronjob/monitor/diagnosis/{jobKey}", "job-key-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.jobKey").value("job-key-001"))
          .andExpect(jsonPath("$.data.config.maxConcurrent").value(16))
          .andExpect(jsonPath("$.data.config.leaderEnabled").value(true));
    }
  }
}
