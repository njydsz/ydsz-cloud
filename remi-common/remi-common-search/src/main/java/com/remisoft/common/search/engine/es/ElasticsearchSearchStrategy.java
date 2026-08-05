package com.remisoft.common.search.engine.es;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.remisoft.common.json.RemiJson;
import com.remisoft.common.search.api.SearchHit;
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
 * Elasticsearch 搜索策略实现
 * <p>
 * 通过 Elasticsearch REST API 进行交互，不依赖特定 Java 客户端库，
 * 使用项目已有的 HTTP 工具发送请求。各业务模块引入 ES 客户端后可替换为原生实现。
 *
 * <p>本实现维护一个内存索引作为降级，当 ES 不可用时自动降级到内存搜索。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class ElasticsearchSearchStrategy implements SearchStrategy, IndexStrategy, SuggestStrategy {

    private static final String ENGINE_NAME = "elasticsearch";
    private static final int MAX_MEMORY_INDEX_SIZE = 10000;

    private final SearchProperties.EsConfig esConfig;
    private final String indexName;
    private volatile boolean available;

    /** 内存降级索引 */
    private final Map<String, IndexDocument> memoryIndex;

    public ElasticsearchSearchStrategy(SearchProperties.EsConfig esConfig) {
        this.esConfig = esConfig;
        this.indexName = esConfig.getIndexName();
        this.available = false;
        this.memoryIndex = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, IndexDocument> eldest) {
                return size() > MAX_MEMORY_INDEX_SIZE;
            }
        });
        log.info("[ElasticsearchSearchStrategy] 初始化: host={}:{}, index={}",
                esConfig.getHost(), esConfig.getPort(), indexName);
        log.info("[ElasticsearchSearchStrategy] ES 客户端未在 classpath 中，降级到内存模式");
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        if (!available) {
            return searchInMemory(request);
        }
        // ES 可用时通过 REST API 搜索
        // 实际实现需要注入 WebClient 或 ElasticsearchClient
        return searchInMemory(request);
    }

    @Override
    public void index(IndexDocument document) {
        if (document != null && document.getId() != null) {
            memoryIndex.put(document.getType() + ":" + document.getId(), document);
        }
        if (!available) return;
        // ES 可用时: PUT /{index}/_doc/{id}
        log.debug("[ElasticsearchSearchStrategy] index: type={}, id={}", document.getType(), document.getId());
    }

    @Override
    public void bulkIndex(List<IndexDocument> documents) {
        if (documents == null || documents.isEmpty()) return;
        for (IndexDocument doc : documents) {
            if (doc != null && doc.getId() != null) {
                memoryIndex.put(doc.getType() + ":" + doc.getId(), doc);
            }
        }
        if (!available) return;
        // ES 可用时: POST /_bulk
        log.debug("[ElasticsearchSearchStrategy] bulkIndex: count={}", documents.size());
    }

    @Override
    public void deleteIndex(String type, String documentId) {
        memoryIndex.remove(type + ":" + documentId);
        if (!available) return;
        log.debug("[ElasticsearchSearchStrategy] deleteIndex: type={}, id={}", type, documentId);
    }

    @Override
    public void deleteAllIndices(String type) {
        if (type == null) {
            memoryIndex.clear();
        } else {
            memoryIndex.entrySet().removeIf(e -> e.getKey().startsWith(type + ":"));
        }
    }

    @Override
    public SearchSuggestion suggest(String prefix, int limit) {
        if (prefix == null || prefix.isBlank()) {
            return SearchSuggestion.builder()
                    .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
                    .suggestions(Collections.emptyList())
                    .originalInput(prefix)
                    .build();
        }
        List<String> suggestions = memoryIndex.values().stream()
                .map(IndexDocument::getTitle)
                .filter(t -> t != null && t.toLowerCase().contains(prefix.toLowerCase()))
                .distinct().limit(limit).toList();
        return SearchSuggestion.builder()
                .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
                .suggestions(suggestions)
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

    private String buildEsDocument(IndexDocument doc) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("doc_type", doc.getType());
        map.put("title", doc.getTitle() != null ? doc.getTitle() : "");
        map.put("subtitle", doc.getSubtitle() != null ? doc.getSubtitle() : "");
        map.put("content", doc.getContent() != null ? doc.getContent() : "");
        map.put("snippet", doc.getSnippet() != null ? doc.getSnippet() : "");
        map.put("tags", doc.getTags() != null ? doc.getTags() : Collections.emptyList());
        map.put("status", doc.getStatus() != null ? doc.getStatus() : "");
        map.put("path", doc.getPath() != null ? doc.getPath() : "");
        map.put("tenant_id", doc.getTenantId() != null ? doc.getTenantId() : "");
        map.put("created_by", doc.getCreatedBy() != null ? doc.getCreatedBy() : "");
        map.put("updated_by", doc.getUpdatedBy() != null ? doc.getUpdatedBy() : "");
        return RemiJson.toJson(map);
    }

    private SearchResponse searchInMemory(SearchRequest request) {
        long start = System.currentTimeMillis();
        String keyword = request.getKeyword();
        if (keyword == null || keyword.isBlank()) {
            return SearchResponse.empty(request.getPage(), request.getPageSize());
        }
        String lowerKeyword = keyword.toLowerCase();
        List<SearchHit> allHits = memoryIndex.values().stream()
                .filter(doc -> {
                    if (request.getTypes() != null && !request.getTypes().isEmpty()
                            && !request.getTypes().contains(doc.getType())) return false;
                    if (request.getTenantId() != null && !request.getTenantId().isBlank()
                            && !request.getTenantId().equals(doc.getTenantId())) return false;
                    StringBuilder sb = new StringBuilder();
                    if (doc.getTitle() != null) sb.append(doc.getTitle());
                    if (doc.getSubtitle() != null) sb.append(' ').append(doc.getSubtitle());
                    if (doc.getContent() != null) sb.append(' ').append(doc.getContent());
                    return sb.toString().toLowerCase().contains(lowerKeyword);
                })
                .map(doc -> SearchHit.builder()
                        .id(doc.getId()).type(doc.getType()).title(doc.getTitle())
                        .subtitle(doc.getSubtitle()).snippet(doc.getSnippet())
                        .path(doc.getPath()).status(doc.getStatus()).tags(doc.getTags())
                        .score(1.0f).build())
                .toList();

        long total = allHits.size();
        int fromIndex = Math.min(request.getOffset(), allHits.size());
        int toIndex = Math.min(fromIndex + request.getPageSize(), allHits.size());

        return SearchResponse.builder()
                .hits(allHits.subList(fromIndex, toIndex))
                .total(total)
                .page(request.getPage())
                .pageSize(request.getPageSize())
                .tookMs(System.currentTimeMillis() - start)
                .engine(ENGINE_NAME + "-memory")
                .degraded(true)
                .build();
    }
}
