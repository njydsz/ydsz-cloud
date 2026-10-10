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

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link AgentMetadataController} Smoke Test.
 *
 * <p>Verify agent metadata query endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class AgentMetadataControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private LlmClient llmClient;

  @org.mockito.Mock
  private ToolRegistry toolRegistry;

  @org.mockito.InjectMocks
  private AgentMetadataController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/models")
  class Models {

    @Test
    @DisplayName("should return 200 when get models succeeds")
    void should_return_200_when_get_models_succeeds() throws Exception {
      when(llmClient.getProvider()).thenReturn("openai");

      mockMvc.perform(get("/agent/models"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /agent/tools")
  class Tools {

    @Test
    @DisplayName("should return 200 when get tools succeeds")
    void should_return_200_when_get_tools_succeeds() throws Exception {
      when(toolRegistry.getToolDefinitions()).thenReturn(List.of());

      mockMvc.perform(get("/agent/tools"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
