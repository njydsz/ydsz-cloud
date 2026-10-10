package com.njydsz.agent.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.agent.server.rag.DocumentIngestionService;
import com.njydsz.agent.server.rag.RagService;
import com.njydsz.agent.server.search.HybridSearchService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link RagController} Smoke Test.
 *
 * <p>Verify RAG endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RagControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RagService ragService;

  @org.mockito.Mock
  private DocumentIngestionService ingestionService;

  @org.mockito.Mock
  private HybridSearchService hybridSearchService;

  @org.mockito.InjectMocks
  private RagController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/rag/stats")
  class Stats {

    @Test
    @DisplayName("should return 200 when get stats succeeds")
    void should_return_200_when_get_stats_succeeds() throws Exception {
      DocumentIngestionService.VectorStoreStats stats = new DocumentIngestionService.VectorStoreStats(10L, "10", "100", 0);
      when(ingestionService.getStats()).thenReturn(stats);

      mockMvc.perform(get("/agent/rag/stats"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
