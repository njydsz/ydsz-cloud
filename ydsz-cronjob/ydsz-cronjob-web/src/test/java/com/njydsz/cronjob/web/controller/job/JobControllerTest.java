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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.cronjob.domain.query.JobQuery;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.service.job.JobService;

/**
 * JobController 集成测试。
 *
 * <p>使用 {@code MockMvcBuilders.standaloneSetup()} 构建轻量级 Web 层测试，
 * 通过 Mockito {@code @Mock} 模拟 Service 依赖，验证 HTTP 路由、请求参数绑定、响应格式正确性。
 *
 * <p>注：当前 corporate Maven 镜像的 spring-boot-test-autoconfigure 缺失 web/servlet 切片类，
 * 故使用 standaloneSetup 模式替代 {@code @WebMvcTest}。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ExtendWith(MockitoExtension.class)
class JobControllerTest {

  private MockMvc mockMvc;

  @Mock
  private JobService jobService;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private JobController jobController;

  private JobVO sampleJob;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(jobController).build();

    sampleJob = new JobVO();
    sampleJob.setId("job-001");
    sampleJob.setJobName("测试任务");
    sampleJob.setJobGroup("DEFAULT");
    sampleJob.setJobKey("test.job.key");
    sampleJob.setHandler("testHandler");
    sampleJob.setStatus("NORMAL");
    sampleJob.setCronExpression("0/5 * * * * ?");
    sampleJob.setScheduleType("CRON");
  }

  @Nested
  @DisplayName("GET /cronjob/{id} - getById")
  class GetById {

    @Test
    @DisplayName("任务存在时返回 200 + JobVO")
    void shouldReturnJobWhenExists() throws Exception {
      when(jobService.getById("job-001")).thenReturn(sampleJob);

      mockMvc.perform(get("/cronjob/{id}", "job-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.id").value("job-001"))
          .andExpect(jsonPath("$.data.jobName").value("测试任务"))
          .andExpect(jsonPath("$.data.cronExpression").value("0/5 * * * * ?"));

      verify(jobService, times(1)).getById("job-001");
    }

    @Test
    @DisplayName("任务不存在时返回 200 + null data")
    void shouldReturnNullWhenNotFound() throws Exception {
      when(jobService.getById("job-404")).thenReturn(null);

      mockMvc.perform(get("/cronjob/{id}", "job-404"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(jobService, times(1)).getById("job-404");
    }
  }

  @Nested
  @DisplayName("GET /cronjob/page - page")
  class Page {

    @Test
    @DisplayName("分页查询返回 PageResponse 包装数据")
    void shouldReturnPagedResult() throws Exception {
      @SuppressWarnings("unchecked")
      PageResponse<List<JobVO>> pageResult = new PageResponse<>();
      pageResult.setData(List.of(sampleJob));
      pageResult.setTotal(1L);
      pageResult.setPageNum(1L);
      pageResult.setPageSize(20L);

      when(jobService.page(any(JobQuery.class))).thenReturn(pageResult);

      mockMvc.perform(get("/cronjob/page")
              .param("pageNum", "1")
              .param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.total").value(1))
          .andExpect(jsonPath("$.data.data[0].id").value("job-001"));

      verify(jobService, times(1)).page(any(JobQuery.class));
    }
  }

  @Nested
  @DisplayName("GET /cronjob/cron/validate - validateCron")
  class ValidateCron {

    @Test
    @DisplayName("合法 Cron 表达式返回 valid=true")
    void shouldReturnValidForCorrectCron() throws Exception {
      mockMvc.perform(get("/cronjob/cron/validate")
              .param("expr", "0/5 * * * * ?")
              .param("count", "3"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.valid").value(true))
          .andExpect(jsonPath("$.data.nextFireTimes").isArray());
    }

    @Test
    @DisplayName("非法 Cron 表达式返回 valid=false 与 error 字段")
    void shouldReturnInvalidForBadCron() throws Exception {
      mockMvc.perform(get("/cronjob/cron/validate")
              .param("expr", "invalid-cron"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.valid").value(false))
          .andExpect(jsonPath("$.data.error").value("Cron 表达式非法"));
    }
  }

  @Nested
  @DisplayName("POST /cronjob/{id}/pause - pause")
  class Pause {

    @Test
    @DisplayName("暂停任务返回 200 + 成功码")
    void shouldPauseJobSuccessfully() throws Exception {
      mockMvc.perform(post("/cronjob/{id}/pause", "job-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(jobService, times(1)).pause("job-001");
    }
  }

  @Nested
  @DisplayName("POST /cronjob/{id}/trigger - trigger")
  class Trigger {

    @Test
    @DisplayName("触发任务返回 200 + logId")
    void shouldTriggerJobAndReturnLogId() throws Exception {
      when(jobService.trigger(eq("job-001"), eq(false))).thenReturn("log-123");

      mockMvc.perform(post("/cronjob/{id}/trigger", "job-001")
              .param("holdLock", "false"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").value("log-123"));

      verify(jobService, times(1)).trigger("job-001", false);
    }
  }
}
