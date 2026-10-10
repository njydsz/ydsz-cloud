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

import com.njydsz.agent.domain.tool.SemanticToolSearchService;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link ToolController} Smoke Test.
 *
 * <p>Verify tool query endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class ToolControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private ToolRegistry toolRegistry;

  @org.mockito.Mock
  private SemanticToolSearchService semanticToolSearchService;

  @org.mockito.InjectMocks
  private ToolController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/tool/list")
  class ListAllTools {

    @Test
    @DisplayName("should return 200 when list tools succeeds")
    void should_return_200_when_list_tools_succeeds() throws Exception {
      when(toolRegistry.getToolDefinitions()).thenReturn(List.of());

      mockMvc.perform(get("/agent/tool/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
