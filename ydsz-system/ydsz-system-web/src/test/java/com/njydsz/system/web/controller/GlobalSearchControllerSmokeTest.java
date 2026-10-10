package com.njydsz.system.web.controller;

import static org.mockito.ArgumentMatchers.any;
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

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.search.analytics.ClickFeedbackService;
import com.njydsz.common.search.analytics.SearchAnalyticsService;
import com.njydsz.common.search.analytics.SearchAnalyticsService.SearchAnalyticsSummary;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.api.SearchSuggestion;
import com.njydsz.common.search.service.IndexRebuildService;
import com.njydsz.common.search.service.UnifiedSearchService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link GlobalSearchController} Smoke Test。
 *
 * <p>验证全局搜索端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class GlobalSearchControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private UnifiedSearchService unifiedSearchService;

  @org.mockito.Mock
  private SearchAnalyticsService searchAnalyticsService;

  @org.mockito.Mock
  private ClickFeedbackService clickFeedbackService;

  @org.mockito.Mock
  private IndexRebuildService indexRebuildService;

  @org.mockito.InjectMocks
  private GlobalSearchController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    SearchResponse searchResponse = new SearchResponse();
    when(unifiedSearchService.search(any())).thenReturn(searchResponse);
    when(unifiedSearchService.suggest(any())).thenReturn(new SearchSuggestion());
    when(searchAnalyticsService.getHotKeywords(any(Integer.class))).thenReturn(List.of());
    when(searchAnalyticsService.getZeroResultKeywords(any(Integer.class))).thenReturn(List.of());
    when(searchAnalyticsService.getSummary()).thenReturn(new SearchAnalyticsSummary(
        "0", "0", "0.00%", "0", "0"));
  }

  @Nested
  @DisplayName("GET /search/analytics/summary")
  class Summary {

    @Test
    @DisplayName("should return 200 when summary query succeeds")
    void should_return_200_when_summary_query_succeeds() throws Exception {
      mockMvc.perform(get("/search/analytics/summary"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /search/analytics/hot")
  class HotKeywords {

    @Test
    @DisplayName("should return 200 when hot keywords query succeeds")
    void should_return_200_when_hot_keywords_query_succeeds() throws Exception {
      mockMvc.perform(get("/search/analytics/hot").param("limit", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
