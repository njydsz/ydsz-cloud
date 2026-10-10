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
import com.njydsz.cronjob.server.core.dag.DagDefinitionCodec;
import com.njydsz.cronjob.server.core.dag.DagDefinitionValidator;
import com.njydsz.cronjob.server.service.dag.JobDagService;

/**
 * {@link JobDagController} Smoke Test。
 *
 * <p>验证 DAG 工作流定义端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class JobDagControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private JobDagService jobDagService;

  @org.mockito.Mock
  private DagDefinitionValidator dagDefinitionValidator;

  @org.mockito.Mock
  private DagDefinitionCodec dagDefinitionCodec;

  @org.mockito.InjectMocks
  private JobDagController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /cronjob/dag/enabled")
  class ListEnabledDags {

    @Test
    @DisplayName("should return 200 when list enabled dags succeeds")
    void should_return_200_when_list_enabled_dags_succeeds() throws Exception {
      when(jobDagService.listEnabledDags()).thenReturn(List.of());

      mockMvc.perform(get("/cronjob/dag/enabled"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
