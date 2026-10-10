package com.njydsz.cronjob.web.controller.dag;

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
import com.njydsz.cronjob.domain.service.JobTaskQueryService;
import com.njydsz.cronjob.domain.vo.JobTaskVO;

/**
 * {@link com.njydsz.cronjob.web.controller.job.JobTaskController} Smoke Test。
 *
 * <p>验证 MapReduce 子任务查询端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class JobTaskControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private JobTaskQueryService jobTaskQueryService;

  @org.mockito.InjectMocks
  private com.njydsz.cronjob.web.controller.job.JobTaskController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /cronjob/task/list")
  class List {

    @Test
    @DisplayName("should return 200 when list succeeds")
    void should_return_200_when_list_succeeds() throws Exception {
      when(jobTaskQueryService.findByLogId("log-001")).thenReturn(java.util.Collections.emptyList());

      mockMvc.perform(get("/cronjob/task/list").param("logId", "log-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
