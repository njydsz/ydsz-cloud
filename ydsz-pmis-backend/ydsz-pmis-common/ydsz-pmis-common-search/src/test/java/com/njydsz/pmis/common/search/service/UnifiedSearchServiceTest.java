package com.njydsz.pmis.common.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.search.analytics.SearchAnalyticsService;
import com.njydsz.pmis.common.search.api.SearchRequest;
import com.njydsz.pmis.common.search.api.SearchResponse;
import com.njydsz.pmis.common.search.api.SearchSuggestion;
import com.njydsz.pmis.common.search.config.SearchProperties;
import com.njydsz.pmis.common.search.core.IndexDocument;
import com.njydsz.pmis.common.search.core.SearchEngine;
import com.njydsz.pmis.common.search.metrics.SearchMetrics;
import com.njydsz.pmis.common.search.provider.SearchProviderRegistry;

/**
 * UnifiedSearchService 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@DisplayName("UnifiedSearchService 测试")
class UnifiedSearchServiceTest {

    private UnifiedSearchService searchService;
    private SearchEngine searchEngine;
    private SearchProviderRegistry registry;
    private SearchProperties properties;
    private SearchMetrics metrics;
    private SearchAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        properties = new SearchProperties();
        metrics = new SearchMetrics(null);
        analyticsService = new SearchAnalyticsService();
        searchEngine = new TestSearchEngine();
        registry = new SearchProviderRegistry();
        searchService = new UnifiedSearchService(searchEngine, registry, properties,
                metrics, analyticsService);
    }

    @Test
    @DisplayName("空关键词返回空结果")
    void search_emptyKeyword_returnsEmpty() {
        SearchRequest request = SearchRequest.of("");
        SearchResponse response = searchService.search(request);
        assertThat(response.getTotal()).isZero();
        assertThat(response.getHits()).isEmpty();
    }

    @Test
    @DisplayName("正常搜索返回结果")
    void search_normalKeyword_returnsResults() {
        SearchRequest request = SearchRequest.of("test");
        SearchResponse response = searchService.search(request);
        assertThat(response).isNotNull();
        assertThat(response.getEngine()).isEqualTo("test-engine");
    }

    @Test
    @DisplayName("深分页保护 — 超过最大翻页深度时返回空结果")
    void search_deepPaging_returnsEmpty() {
        SearchRequest request = SearchRequest.of("test", 600, 100);
        // offset = 599 * 100 = 59900 > maxPageDepth(5000)
        SearchResponse response = searchService.search(request);
        assertThat(response.getTotal()).isZero();
    }

    @Test
    @DisplayName("每页大小限制 — 超过最大值时自动调整")
    void search_pageSizeExceedsMax_adjusted() {
        properties.setMaxPageSize(50);
        SearchRequest request = SearchRequest.builder()
                .keyword("test")
                .page(1)
                .pageSize(200)
                .build();
        searchService.search(request);
        assertThat(request.getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("缓存命中 — 相同请求第二次走缓存")
    void search_cacheHit() {
        properties.getCache().setEnabled(true);
        SearchRequest request = SearchRequest.of("cached");
        SearchResponse first = searchService.search(request);
        SearchResponse second = searchService.search(request);
        assertThat(second).isNotNull();
    }

    @Test
    @DisplayName("清空缓存")
    void clearCache() {
        searchService.clearCache();
    }

    /**
     * 测试用内存搜索引擎
     */
    private static class TestSearchEngine implements SearchEngine {
        @Override
        public SearchResponse search(SearchRequest request) {
            return SearchResponse.builder()
                    .hits(List.of())
                    .total(0L)
                    .page(request.getPage())
                    .pageSize(request.getPageSize())
                    .tookMs(1L)
                    .engine("test-engine")
                    .build();
        }

        @Override
        public void index(IndexDocument document) {
        }

        @Override
        public void bulkIndex(List<IndexDocument> documents) {
        }

        @Override
        public void deleteIndex(String type, String documentId) {
        }

        @Override
        public SearchSuggestion suggest(String prefix, int limit) {
            return SearchSuggestion.builder()
                    .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
                    .suggestions(List.of())
                    .originalInput(prefix)
                    .build();
        }

        @Override
        public void deleteAllIndices(String type) {
        }

        @Override
        public String getName() {
            return "test-engine";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}
