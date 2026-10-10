package com.njydsz.workflow.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.server.simulator.FlowSimulationService;
import com.njydsz.workflow.server.simulator.SimulationResult;

/**
 * {@link FlowSimulationController} Smoke Test。
 *
 * <p>验证流程模拟核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * <p>注意：FlowSimulationController 仅有 POST 端点，无 GET 端点，因此测试覆盖 POST /workflow/simulation/run。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowSimulationControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FlowSimulationService simulationService;

  @org.mockito.InjectMocks
  private FlowSimulationController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("POST /workflow/simulation/run")
  class RunSimulation {

    @Test
    @DisplayName("should return 200 when simulation run succeeds")
    void should_return_200_when_simulation_run_succeeds() throws Exception {
      SimulationResult result = new SimulationResult();
      when(simulationService.simulate(anyString(), any())).thenReturn(result);

      String body = "{\"definitionId\":\"def-001\",\"variables\":{}}";

      mockMvc.perform(post("/workflow/simulation/run")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
