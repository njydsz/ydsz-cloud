package com.njydsz.cronjob.web.controller.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.domain.query.JobQuery;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.service.job.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link JobController} Smoke Test（轻量级 MockMvc 独立容器模式）。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>正常分页列表接口 HTTP 200 + 响应体 code='A00000'
 * </ul>
 *
 * @author ydsz-smoke-test
 * @since 26.10.01
 */
class CronjobJobSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private JobService jobService;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private JobController jobController;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(jobController);
  }

  @Test
  @DisplayName("GET /cronjob/page 应返回 HTTP 200 + code=A00000")
  void page_shouldReturn200() throws Exception {
    // Given
    PageResponse<List<JobVO>> pageResponse = PageResponse.success(1L, 1L, 10L, List.of(new JobVO()));

    when(jobService.page(any(JobQuery.class))).thenReturn(pageResponse);

    // When
    mockMvc.perform(get("/cronjob/page")
            .param("pageNum", "1")
            .param("pageSize", "10"))
        // Then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
  }
}
