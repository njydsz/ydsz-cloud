package com.njydsz.cronjob.web.controller.job;

import static org.mockito.ArgumentMatchers.any;
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
import com.njydsz.cronjob.domain.query.JobLogQuery;
import com.njydsz.cronjob.domain.query.JobQuery;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.service.job.JobService;

/**
 * {@link JobController} Smoke Test。
 *
 * <p>验证任务调度端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class JobControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private JobService jobService;

  @org.mockito.InjectMocks
  private JobController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /cronjob/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      JobVO vo = new JobVO();
      vo.setId("job-001");
      vo.setJobName("测试任务");
      PageResponse<List<JobVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(jobService.page(any(JobQuery.class))).thenReturn(pageResult);

      mockMvc.perform(get("/cronjob/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /cronjob/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      JobVO vo = new JobVO();
      vo.setId("job-001");
      vo.setJobName("测试任务");
      when(jobService.getById("job-001")).thenReturn(vo);

      mockMvc.perform(get("/cronjob/{id}", "job-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /cronjob/log/page")
  class PageLog {

    @Test
    @DisplayName("should return 200 when page log query succeeds")
    void should_return_200_when_page_log_query_succeeds() throws Exception {
      PageResponse<List<JobLogVO>> pageResult = PageResponse.success(0L, 1L, 20L, List.of());
      when(jobService.pageLog(any(JobLogQuery.class))).thenReturn(pageResult);

      mockMvc.perform(get("/cronjob/log/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
