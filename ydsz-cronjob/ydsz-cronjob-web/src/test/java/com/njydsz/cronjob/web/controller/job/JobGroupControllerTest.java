package com.njydsz.cronjob.web.controller.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.cronjob.domain.dto.BatchResultDTO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.service.job.JobService;

/**
 * JobGroupController 集成测试。
 *
 * <p>覆盖按分组分页查询、批量暂停/恢复/触发、分组统计等核心场景。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ExtendWith(MockitoExtension.class)
class JobGroupControllerTest {

  private MockMvc mockMvc;

  @Mock
  private JobService jobService;

  @InjectMocks
  private JobGroupController jobGroupController;

  private JobVO sampleJob;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(jobGroupController).build();

    sampleJob = new JobVO();
    sampleJob.setId("job-001");
    sampleJob.setJobName("订单同步任务");
    sampleJob.setJobGroup("ORDER-CENTER");
    sampleJob.setJobKey("order.sync.job");
    sampleJob.setStatus("NORMAL");
  }

  @Nested
  @DisplayName("GET /cronjob/group/{jobGroup}/page - pageByGroup")
  class PageByGroup {

    @Test
    @DisplayName("按分组分页查询返回任务列表")
    void shouldReturnJobsByGroup() throws Exception {
      @SuppressWarnings("unchecked")
      PageResponse<List<JobVO>> pageResult = new PageResponse<>();
      pageResult.setData(List.of(sampleJob));
      pageResult.setTotal(1L);
      pageResult.setPageNum(1L);
      pageResult.setPageSize(20L);

      when(jobService.pageByGroup(eq("ORDER-CENTER"), eq(1), eq(20))).thenReturn(pageResult);

      mockMvc.perform(get("/cronjob/group/{jobGroup}/page", "ORDER-CENTER")
              .param("page", "1")
              .param("size", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.total").value(1))
          .andExpect(jsonPath("$.data.data[0].jobGroup").value("ORDER-CENTER"));

      verify(jobService, times(1)).pageByGroup("ORDER-CENTER", 1, 20);
    }
  }

  @Nested
  @DisplayName("POST /cronjob/group/{jobGroup}/pause - pauseByGroup")
  class PauseByGroup {

    @Test
    @DisplayName("分组下存在 NORMAL 任务时批量暂停成功")
    void shouldPauseAllNormalJobsInGroup() throws Exception {
      when(jobService.findByGroupAndStatus("ORDER-CENTER", "NORMAL"))
          .thenReturn(List.of(sampleJob));

      BatchResultDTO<String> batchResult = BatchResultDTO.allSuccess(1);
      when(jobService.batchPause(List.of("job-001"))).thenReturn(batchResult);

      mockMvc.perform(post("/cronjob/group/{jobGroup}/pause", "ORDER-CENTER"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.total").value(1))
          .andExpect(jsonPath("$.data.successCount").value(1));

      verify(jobService, times(1)).batchPause(List.of("job-001"));
    }

    @Test
    @DisplayName("分组下无 NORMAL 任务时返回 0/0")
    void shouldReturnEmptyWhenNoNormalJobs() throws Exception {
      when(jobService.findByGroupAndStatus("EMPTY-GROUP", "NORMAL"))
          .thenReturn(List.of());

      mockMvc.perform(post("/cronjob/group/{jobGroup}/pause", "EMPTY-GROUP"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.total").value(0));

      verify(jobService, times(0)).batchPause(any());
    }
  }

  @Nested
  @DisplayName("POST /cronjob/group/{jobGroup}/resume - resumeByGroup")
  class ResumeByGroup {

    @Test
    @DisplayName("分组下存在 PAUSED 任务时批量恢复成功")
    void shouldResumeAllPausedJobsInGroup() throws Exception {
      JobVO pausedJob = new JobVO();
      pausedJob.setId("job-002");
      pausedJob.setJobGroup("ORDER-CENTER");
      pausedJob.setStatus("PAUSED");

      when(jobService.findByGroupAndStatus("ORDER-CENTER", "PAUSED"))
          .thenReturn(List.of(pausedJob));

      BatchResultDTO<String> batchResult = BatchResultDTO.allSuccess(1);
      when(jobService.batchResume(List.of("job-002"))).thenReturn(batchResult);

      mockMvc.perform(post("/cronjob/group/{jobGroup}/resume", "ORDER-CENTER"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.total").value(1));

      verify(jobService, times(1)).batchResume(List.of("job-002"));
    }
  }

  @Nested
  @DisplayName("GET /cronjob/group/stats - groupStats")
  class GroupStats {

    @Test
    @DisplayName("返回各分组任务数量统计")
    void shouldReturnGroupStatistics() throws Exception {
      when(jobService.listDistinctGroups()).thenReturn(List.of("ORDER-CENTER", "FINANCE-DAILY"));
      when(jobService.countByGroup("ORDER-CENTER")).thenReturn(5L);
      when(jobService.countByGroup("FINANCE-DAILY")).thenReturn(3L);
      when(jobService.countAll()).thenReturn(10L);

      mockMvc.perform(get("/cronjob/group/stats"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").isArray())
          .andExpect(jsonPath("$.data.length()").value(3));

      verify(jobService, times(1)).listDistinctGroups();
    }
  }
}
