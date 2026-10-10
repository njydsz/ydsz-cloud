package com.njydsz.agent.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.agent.server.knowledge.KnowledgeGraphService;
import com.njydsz.common.locales.util.I18nMessages;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link KnowledgeGraphController} Smoke Test.
 *
 * <p>Verify knowledge graph endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class KnowledgeGraphControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private KnowledgeGraphService knowledgeGraphService;

  @org.mockito.Mock
  private I18nMessages i18nMessages;

  @org.mockito.InjectMocks
  private KnowledgeGraphController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/knowledge/stats")
  class Stats {

    @Test
    @DisplayName("should return 200 when get stats succeeds")
    void should_return_200_when_get_stats_succeeds() throws Exception {
      when(knowledgeGraphService.getStats()).thenReturn(Map.of("entityCount", 100L, "relationCount", 500L));

      mockMvc.perform(get("/agent/knowledge/stats"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
