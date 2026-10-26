package com.njydsz.system.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.search.analytics.ClickFeedbackService;
import com.njydsz.common.search.analytics.SearchAnalyticsService;
import com.njydsz.common.search.analytics.SearchAnalyticsService.SearchAnalyticsSummary;
import com.njydsz.common.search.api.SearchRequest;
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
 * <p>桩数据按端点就近声明（MockitoExtension 为 STRICT_STUBS，集中在 setUp 里全量打桩会让
 * 每个用例都触发 UnnecessaryStubbingException，导致整类恒红）。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class GlobalSearchControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private UnifiedSearchService unifiedSearchService;

  @Mock
  private SearchAnalyticsService searchAnalyticsService;

  @Mock
  private ClickFeedbackService clickFeedbackService;

  @Mock
  private IndexRebuildService indexRebuildService;

  @InjectMocks
  private GlobalSearchController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /search")
  class Search {

    @Test
    @DisplayName("should return 200 when cross-type search succeeds")
    void should_return_200_when_search_succeeds() throws Exception {
      when(unifiedSearchService.search(any(SearchRequest.class)))
          .thenReturn(SearchResponse.builder().build());

      mockMvc.perform(get("/search").param("keyword", "云顶"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /search/suggest")
  class Suggest {

    @Test
    @DisplayName("should return 200 when suggestion query succeeds")
    void should_return_200_when_suggest_succeeds() throws Exception {
      when(unifiedSearchService.suggest("云顶")).thenReturn(SearchSuggestion.builder().build());

      mockMvc.perform(get("/search/suggest").param("prefix", "云顶"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /search/analytics/summary")
  class Summary {

    @Test
    @DisplayName("should return 200 when summary query succeeds")
    void should_return_200_when_summary_query_succeeds() throws Exception {
      when(searchAnalyticsService.getSummary())
          .thenReturn(new SearchAnalyticsSummary(0L, 0L, 0.0, 0, 0));

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
      when(searchAnalyticsService.getHotKeywords(anyInt())).thenReturn(List.of());

      mockMvc.perform(get("/search/analytics/hot").param("limit", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /search/analytics/zero")
  class ZeroResultKeywords {

    @Test
    @DisplayName("should return 200 when zero result keywords query succeeds")
    void should_return_200_when_zero_result_keywords_query_succeeds() throws Exception {
      when(searchAnalyticsService.getZeroResultKeywords(anyInt())).thenReturn(List.of());

      mockMvc.perform(get("/search/analytics/zero").param("limit", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
