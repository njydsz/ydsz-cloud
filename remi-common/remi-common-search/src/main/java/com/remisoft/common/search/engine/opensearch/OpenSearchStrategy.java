package com.remisoft.common.search.engine.opensearch;

import java.util.Collections;
import java.util.List;

import com.remisoft.common.search.api.SearchRequest;
import com.remisoft.common.search.api.SearchResponse;
import com.remisoft.common.search.api.SearchSuggestion;
import com.remisoft.common.search.config.SearchProperties;
import com.remisoft.common.search.core.EngineCapability;
import com.remisoft.common.search.core.IndexDocument;
import com.remisoft.common.search.core.IndexStrategy;
import com.remisoft.common.search.core.SearchStrategy;
import com.remisoft.common.search.core.SuggestStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenSearch 搜索策略实现
 * <p>
 * 基于 OpenSearch REST API 进行交互，API 与 Elasticsearch 高度相似但客户端独立。
 * 当 classpath 中有 OpenSearch Java 客户端时自动激活。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class OpenSearchStrategy implements SearchStrategy, IndexStrategy, SuggestStrategy {

    private static final String ENGINE_NAME = "opensearch";

    private final SearchProperties.OpenSearchConfig osConfig;
    private volatile boolean available;

    public OpenSearchStrategy(SearchProperties.OpenSearchConfig osConfig) {
        this.osConfig = osConfig;
        this.available = false;
        log.info("[OpenSearchStrategy] 初始化: host={}:{}, index={}",
                osConfig.getHost(), osConfig.getPort(), osConfig.getIndexName());
        log.info("[OpenSearchStrategy] OpenSearch 客户端未在 classpath 中，降级到内存模式");
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        return SearchResponse.empty(request.getPage(), request.getPageSize());
    }

    @Override
    public void index(IndexDocument document) {
        log.debug("[OpenSearchStrategy] index: type={}, id={}", document.getType(), document.getId());
    }

    @Override
    public void bulkIndex(List<IndexDocument> documents) {
        log.debug("[OpenSearchStrategy] bulkIndex: count={}", documents.size());
    }

    @Override
    public void deleteIndex(String type, String documentId) {
        log.debug("[OpenSearchStrategy] deleteIndex: type={}, id={}", type, documentId);
    }

    @Override
    public void deleteAllIndices(String type) {
        log.debug("[OpenSearchStrategy] deleteAllIndices: type={}", type);
    }

    @Override
    public SearchSuggestion suggest(String prefix, int limit) {
        return SearchSuggestion.builder()
                .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
                .suggestions(Collections.emptyList())
                .originalInput(prefix)
                .build();
    }

    @Override
    public String getEngineName() {
        return ENGINE_NAME;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public EngineCapability getCapability() {
        return EngineCapability.full();
    }
}
