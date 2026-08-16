package com.njydsz.common.search.engine.memory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.search.api.SearchHit;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.api.SearchSuggestion;
import com.njydsz.common.search.core.EngineCapability;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.IndexStrategy;
import com.njydsz.common.search.core.SearchStrategy;
import com.njydsz.common.search.core.SuggestStrategy;

/**
 * 内存搜索策略（测试 / 降级用）。
 *
 * <p>基于 {@link LinkedHashMap} LRU 缓存实现的轻量级搜索引擎，无需外部依赖。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class InMemorySearchStrategy implements SearchStrategy, IndexStrategy, SuggestStrategy {

    private static final String ENGINE_NAME = "memory";
    private static final int MAX_INDEX_SIZE = 10000;

    private final Map<String, IndexDocument> index = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, IndexDocument> eldest) {
                    return size() > MAX_INDEX_SIZE;
                }
            });

    @Override
    public SearchResponse search(SearchRequest request) {
        long start = System.currentTimeMillis();
        String keyword = request.getKeyword();
        if (keyword == null || keyword.isBlank()) {
            return SearchResponse.empty(request.getPage(), request.getPageSize());
        }
        String lowerKeyword = keyword.toLowerCase();

        var allHits = index.values().stream()
                .filter(doc -> {
                    if (request.getTypes() != null && !request.getTypes().isEmpty()
                            && !request.getTypes().contains(doc.getType())) {
                        return false;
                    }
                    if (request.getTenantId() != null && !request.getTenantId().isBlank()
                            && !request.getTenantId().equals(doc.getTenantId())) {
                        return false;
                    }
                    String text = buildSearchableText(doc);
                    return text != null && text.toLowerCase().contains(lowerKeyword);
                })
                .map(doc -> {
                    SearchHit hit = SearchHit.builder()
                            .id(doc.getId()).type(doc.getType()).title(doc.getTitle())
                            .subtitle(doc.getSubtitle()).snippet(doc.getSnippet())
                            .path(doc.getPath()).status(doc.getStatus()).tags(doc.getTags())
                            .score(1.0f).build();
                    if (request.isHighlight() && doc.getTitle() != null) {
                        hit.setHighlight(simpleHighlight(doc.getTitle(), keyword,
                                request.getHighlightPreTag(), request.getHighlightPostTag()));
                    }
                    return hit;
                }).collect(Collectors.toList());

        long total = allHits.size();
        int fromIndex = Math.min(request.getOffset(), allHits.size());
        int toIndex = Math.min(fromIndex + request.getPageSize(), allHits.size());

        return SearchResponse.builder()
                .hits(allHits.subList(fromIndex, toIndex))
                .total(total)
                .page(request.getPage())
                .pageSize(request.getPageSize())
                .tookMs(System.currentTimeMillis() - start)
                .engine(ENGINE_NAME)
                .build();
    }

    @Override
    public void index(IndexDocument document) {
        if (document != null && document.getId() != null) {
            index.put(document.getType() + ":" + document.getId(), document);
        }
    }

    @Override
    public void bulkIndex(List<IndexDocument> documents) {
        if (documents != null) {
            documents.forEach(this::index);
        }
    }

    @Override
    public void deleteIndex(String type, String documentId) {
        index.remove(type + ":" + documentId);
    }

    @Override
    public void deleteAllIndices(String type) {
        if (type == null) {
            index.clear();
        } else {
            index.entrySet().removeIf(e -> e.getKey().startsWith(type + ":"));
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
        var suggestions = index.values().stream()
                .map(IndexDocument::getTitle)
                .filter(t -> t != null && t.toLowerCase().contains(prefix.toLowerCase()))
                .distinct().limit(limit).collect(Collectors.toList());
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
        return true;
    }

    @Override
    public EngineCapability getCapability() {
        return EngineCapability.minimal();
    }

    private String buildSearchableText(IndexDocument doc) {
        StringBuilder sb = new StringBuilder();
        if (doc.getTitle() != null) sb.append(doc.getTitle());
        if (doc.getSubtitle() != null) sb.append(' ').append(doc.getSubtitle());
        if (doc.getContent() != null) sb.append(' ').append(doc.getContent());
        if (doc.getTags() != null) {
            for (String tag : doc.getTags()) {
                sb.append(' ').append(tag);
            }
        }
        return sb.toString();
    }

    private String simpleHighlight(String text, String keyword, String preTag, String postTag) {
        int idx = text.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx < 0) return text;
        return text.substring(0, idx) + preTag + text.substring(idx, idx + keyword.length()) + postTag
                + text.substring(idx + keyword.length());
    }
}
