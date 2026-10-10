package com.njydsz.agent.web.controller;

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

import com.njydsz.agent.domain.vo.AgentDefinitionVO;
import com.njydsz.agent.server.agent.AgentDefinitionService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link AgentDefinitionController} Smoke Test。
 *
 * <p>验证 Agent 定义管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class AgentDefinitionControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private AgentDefinitionService agentDefinitionService;

  @org.mockito.InjectMocks
  private AgentDefinitionController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/definitions")
  class List {

    @Test
    @DisplayName("should return 200 when list succeeds")
    void should_return_200_when_list_succeeds() throws Exception {
      when(agentDefinitionService.listActive()).thenReturn(List.of());

      mockMvc.perform(get("/agent/definitions"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /agent/definitions/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      AgentDefinitionVO vo = new AgentDefinitionVO();
      vo.setId("agent-001");
      vo.setAgentCode("order-analysis-agent");
      when(agentDefinitionService.getById("agent-001")).thenReturn(vo);

      mockMvc.perform(get("/agent/definitions/{id}", "agent-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /agent/definitions/code/{code}")
  class GetByCode {

    @Test
    @DisplayName("should return 200 when get by code succeeds")
    void should_return_200_when_get_by_code_succeeds() throws Exception {
      AgentDefinitionVO vo = new AgentDefinitionVO();
      vo.setId("agent-001");
      vo.setAgentCode("order-analysis-agent");
      when(agentDefinitionService.getByCode("order-analysis-agent")).thenReturn(vo);

      mockMvc.perform(get("/agent/definitions/code/{code}", "order-analysis-agent"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
