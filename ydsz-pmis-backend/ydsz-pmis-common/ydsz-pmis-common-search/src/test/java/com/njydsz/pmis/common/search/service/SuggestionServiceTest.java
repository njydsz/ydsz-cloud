package com.njydsz.pmis.common.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.search.api.SearchSuggestion;
import com.njydsz.pmis.common.search.config.SearchProperties;
import com.njydsz.pmis.common.search.core.IndexDocument;
import com.njydsz.pmis.common.search.core.SearchEngine;

/**
 * SuggestionService 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@DisplayName("SuggestionService 测试")
class SuggestionServiceTest {

    private SuggestionService suggestionService;
    private SearchProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SearchProperties();
        properties.setSuggestLimit(5);
        SearchEngine engine = new TestSearchEngine();
        suggestionService = new SuggestionService(engine, properties);
    }

    @Test
    @DisplayName("空前缀返回空列表")
    void autocomplete_emptyPrefix_returnsEmpty() {
        assertThat(suggestionService.autocomplete("")).isEmpty();
        assertThat(suggestionService.autocomplete(null)).isEmpty();
    }

    @Test
    @DisplayName("自动补全返回建议列表")
    void autocomplete_returnsSuggestions() {
        List<String> suggestions = suggestionService.autocomplete("pro");
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions).contains("project");
    }

    @Test
    @DisplayName("didYouMean 返回纠错建议")
    void didYouMean_returnsCorrections() {
        List<String> corrections = suggestionService.didYouMean("projct");
        assertThat(corrections).isNotNull();
    }

    @Test
    @DisplayName("didYouMean 空关键词返回空列表")
    void didYouMean_emptyKeyword_returnsEmpty() {
        assertThat(suggestionService.didYouMean("")).isEmpty();
        assertThat(suggestionService.didYouMean(null)).isEmpty();
    }

    /**
     * 测试用搜索引擎
     */
    private static class TestSearchEngine implements SearchEngine {
        @Override
        public com.njydsz.pmis.common.search.api.SearchResponse search(
                com.njydsz.pmis.common.search.api.SearchRequest request) {
            return com.njydsz.pmis.common.search.api.SearchResponse.empty(1, 20);
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
                    .suggestions(List.of("project", "projection", "property"))
                    .originalInput(prefix)
                    .build();
        }

        @Override
        public void deleteAllIndices(String type) {
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}
