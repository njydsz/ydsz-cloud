package com.njydsz.cronjob.web.controller.job;

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
import com.njydsz.cronjob.domain.vo.JobHistoryVO;
import com.njydsz.cronjob.server.service.job.JobHistoryService;

/**
 * {@link JobHistoryController} Smoke Test。
 *
 * <p>验证任务配置历史版本端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class JobHistoryControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private JobHistoryService jobHistoryService;

  @org.mockito.InjectMocks
  private JobHistoryController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /cronjob/history/versions")
  class Versions {

    @Test
    @DisplayName("should return 200 when list versions succeeds")
    void should_return_200_when_list_versions_succeeds() throws Exception {
      when(jobHistoryService.listVersions("job-001")).thenReturn(List.of());

      mockMvc.perform(get("/cronjob/history/versions").param("jobId", "job-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /cronjob/history/detail")
  class Detail {

    @Test
    @DisplayName("should return 200 when get version detail succeeds")
    void should_return_200_when_get_version_detail_succeeds() throws Exception {
      JobHistoryVO vo = new JobHistoryVO();
      vo.setJobId("job-001");
      when(jobHistoryService.getVersion("job-001", 1)).thenReturn(vo);

      mockMvc.perform(get("/cronjob/history/detail").param("jobId", "job-001").param("version", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
