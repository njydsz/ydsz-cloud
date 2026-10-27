package com.njydsz.agent.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
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
 * <p>Note: RagController 使用手写构造器，第三个入参是 {@link ObjectProvider}（HybridSearchService 可选注入），
 * 因此这里 mock 的是 Provider 本身而非 HybridSearchService，否则 {@code @InjectMocks} 只能做构造器注入，
 * 会把 {@code null} 传给该入参并在构造器内 {@code getIfAvailable()} 处抛 NPE。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RagControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private RagService ragService;

  @Mock
  private DocumentIngestionService ingestionService;

  @Mock
  private ObjectProvider<HybridSearchService> hybridSearchServiceProvider;

  @InjectMocks
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
