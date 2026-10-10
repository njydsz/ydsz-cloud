package com.njydsz.agent.web.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.agent.domain.dto.DagExecutionDTO;
import com.njydsz.agent.domain.gateway.DagCheckpointStore;
import com.njydsz.agent.server.agent.DagDslParser;
import com.njydsz.agent.server.agent.DagOrchestrationExecutor;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link DagController} Smoke Test.
 *
 * <p>Verify DAG orchestration endpoints return HTTP 200 + YdszResponse envelope.
 *
 * <p>Note: DagController uses ObjectProvider for DagCheckpointStore,
 * so the controller is assembled manually rather than via @InjectMocks.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class DagControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    DagDslParser dslParser = mock(DagDslParser.class);
    DagOrchestrationExecutor dagExecutor = mock(DagOrchestrationExecutor.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<DagCheckpointStore> checkpointStoreProvider = mock(ObjectProvider.class);
    when(checkpointStoreProvider.getIfAvailable()).thenReturn(null);

    DagController dagController = new DagController(dslParser, dagExecutor, checkpointStoreProvider);
    mockMvc = standaloneSetup(dagController);
  }

  @Nested
  @DisplayName("POST /agent/dag/validate")
  class Validate {

    @Test
    @DisplayName("should return 200 when validate succeeds")
    void should_return_200_when_validate_succeeds() throws Exception {
      mockMvc.perform(post("/agent/dag/validate")
              .contentType("application/json")
              .content("{\"dsl\":\"name: test\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
