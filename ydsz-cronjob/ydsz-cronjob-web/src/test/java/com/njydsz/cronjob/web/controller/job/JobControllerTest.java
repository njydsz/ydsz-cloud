package com.njydsz.cronjob.web.controller.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.service.job.JobService;

/**
 * JobController 纯 Mockito 单元测试（无 Spring 容器）。
 *
 * <p>因 Controller 依赖 @AuthApiPermission / @Idempotent / @RateLimit 等多个 AOP 切面，
 * 完整 @WebMvcTest 需要加载 Security + Redis 等重量级上下文，故退化为 Mockito 单元测试，
 * 验证 Controller 正确委托 Service 并封装响应。
 */
class JobControllerTest {

  @InjectMocks private JobController jobController;

  @Mock private JobService mockJobService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @DisplayName("page: 正常返回 A00000 及分页结构")
  void page_success() {
    // arrange
    PageResponse<List<JobVO>> emptyPage = PageResponse.empty(1L, 20L);
    when(mockJobService.page(anyInt(), anyInt(), any(), any(), any())).thenReturn(emptyPage);

    // act
    YdszResponse<PageResponse<List<JobVO>>> response =
        jobController.page(1, 20, null, null, null);

    // assert: code / success flag / data structure
    assertThat(response).isNotNull();
    assertThat(response.getCode()).isEqualTo("A00000");
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getTotal()).isEqualTo(0L);
    assertThat(response.getData().getPageNum()).isEqualTo(1L);
    assertThat(response.getData().getPageSize()).isEqualTo(20L);
    verify(mockJobService).page(1, 20, null, null, null);
  }

  @Test
  @DisplayName("getById: 正常返回任务详情")
  void getById_success() {
    // arrange
    JobVO mockJob = new JobVO();
    mockJob.setId("job-001");
    mockJob.setJobName("test-cron-job");
    when(mockJobService.getById("job-001")).thenReturn(mockJob);

    // act
    YdszResponse<JobVO> response = jobController.getById("job-001");

    // assert
    assertThat(response).isNotNull();
    assertThat(response.getCode()).isEqualTo("A00000");
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getJobName()).isEqualTo("test-cron-job");
    verify(mockJobService).getById("job-001");
  }

  @Test
  @DisplayName("validateCron: 合法表达式返回 valid=true")
  void validateCron_success() {
    // act
    YdszResponse<java.util.Map<String, Object>> response =
        jobController.validateCron("0 0/5 * * * ?", 3);

    // assert
    assertThat(response).isNotNull();
    assertThat(response.getCode()).isEqualTo("A00000");
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().get("valid")).isEqualTo(true);
    assertThat(response.getData().get("nextFireTimes")).isNotNull();
  }
}
