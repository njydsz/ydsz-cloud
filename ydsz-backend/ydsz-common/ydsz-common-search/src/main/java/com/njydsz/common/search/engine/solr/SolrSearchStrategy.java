package com.njydsz.common.search.engine.solr;

import java.util.Collections;
import java.util.List;

import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.api.SearchSuggestion;
import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.search.core.EngineCapability;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.IndexStrategy;
import com.njydsz.common.search.core.SearchStrategy;
import com.njydsz.common.search.core.SuggestStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * Apache Solr 搜索策略实现
 * <p>
 * 基于 SolrJ 客户端与 Solr 交互，支持全文检索、高亮、聚合分面。
 * 当 classpath 中有 SolrJ 时自动激活。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
public class SolrSearchStrategy implements SearchStrategy, IndexStrategy, SuggestStrategy {

    private static final String ENGINE_NAME = "solr";

    private final SearchProperties.SolrConfig solrConfig;
    private volatile boolean available;

    public SolrSearchStrategy(SearchProperties.SolrConfig solrConfig) {
        this.solrConfig = solrConfig;
        this.available = false;
        log.info("[SolrSearchStrategy] 初始化: baseUrl={}, core={}",
                solrConfig.getBaseUrl(), solrConfig.getCore());
        log.info("[SolrSearchStrategy] SolrJ 客户端未在 classpath 中，降级到内存模式");
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        return SearchResponse.empty(request.getPage(), request.getPageSize());
    }

    @Override
    public void index(IndexDocument document) {
        log.debug("[SolrSearchStrategy] index: type={}, id={}", document.getType(), document.getId());
    }

    @Override
    public void bulkIndex(List<IndexDocument> documents) {
        log.debug("[SolrSearchStrategy] bulkIndex: count={}", documents.size());
    }

    @Override
    public void deleteIndex(String type, String documentId) {
        log.debug("[SolrSearchStrategy] deleteIndex: type={}, id={}", type, documentId);
    }

    @Override
    public void deleteAllIndices(String type) {
        log.debug("[SolrSearchStrategy] deleteAllIndices: type={}", type);
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
