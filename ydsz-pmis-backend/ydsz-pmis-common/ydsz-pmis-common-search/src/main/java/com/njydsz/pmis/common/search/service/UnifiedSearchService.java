package com.njydsz.pmis.common.search.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.njydsz.pmis.common.search.api.SearchHit;
import com.njydsz.pmis.common.search.api.SearchRequest;
import com.njydsz.pmis.common.search.api.SearchResponse;
import com.njydsz.pmis.common.search.api.SearchSuggestion;
import com.njydsz.pmis.common.search.config.SearchProperties;
import com.njydsz.pmis.common.search.core.SearchEngine;
import com.njydsz.pmis.common.search.provider.SearchProvider;
import com.njydsz.pmis.common.search.provider.SearchProviderRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 统一搜索服务
 * <p>
 * 聚合多个 {@link SearchProvider} 的搜索结果，提供跨实体统一搜索能力。
 * 支持按类型搜索、结果合并排序、权限过滤。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
public class UnifiedSearchService {

    private final SearchEngine searchEngine;
    private final SearchProviderRegistry providerRegistry;
    private final SearchProperties properties;
    private final Executor searchExecutor;

    public UnifiedSearchService(SearchEngine searchEngine,
                                 SearchProviderRegistry providerRegistry,
                                 SearchProperties properties) {
        this.searchEngine = searchEngine;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
        this.searchExecutor = Executors.newFixedThreadPool(
                Math.max(2, properties.getIndex().getThreadPoolSize()));
    }

    /**
     * 统一搜索（跨实体）
     * <p>
     * 当 {@code request.types} 为空时，搜索所有已注册的实体类型。
     * 各类型的搜索并行执行，结果合并后按相关性排序。
     *
     * @param request 搜索请求
     * @return 搜索响应
     */
    public SearchResponse search(SearchRequest request) {
        long start = System.currentTimeMillis();

        // 应用默认配置
        applyDefaults(request);

        if (request.getKeyword() == null || request.getKeyword().isBlank()) {
            return SearchResponse.empty(request.getPage(), request.getPageSize());
        }

        // 获取需要搜索的 Provider
        List<SearchProvider<?>> providers = providerRegistry.getProviders(request.getTypes());
        if (providers.isEmpty()) {
            // 无 Provider 注册，直接使用搜索引擎
            return searchEngine.search(request);
        }

        // 单类型搜索：直接走搜索引擎
        if (providers.size() == 1) {
            return searchEngine.search(request);
        }

        // 多类型搜索：并行搜索各类型，合并结果
        return searchMultiType(request, providers, start);
    }

    /**
     * 搜索建议
     */
    public SearchSuggestion suggest(String prefix) {
        return searchEngine.suggest(prefix, properties.getSuggestLimit());
    }

    /**
     * 搜索建议（零结果纠错）
     */
    public SearchSuggestion didYouMean(String keyword) {
        SearchSuggestion suggestion = searchEngine.suggest(keyword, properties.getSuggestLimit());
        if (suggestion != null) {
            suggestion.setType(SearchSuggestion.SuggestionType.DID_YOU_MEAN);
        }
        return suggestion;
    }

    // ==================== 私有方法 ====================

    private void applyDefaults(SearchRequest request) {
        if (request.getPage() <= 0) {
            request.setPage(1);
        }
        if (request.getPageSize() <= 0) {
            request.setPageSize(properties.getPageSize());
        }
        if (request.getHighlightPreTag() == null) {
            request.setHighlightPreTag(properties.getHighlightPreTag());
        }
        if (request.getHighlightPostTag() == null) {
            request.setHighlightPostTag(properties.getHighlightPostTag());
        }
        if (request.getHighlightFragmentSize() <= 0) {
            request.setHighlightFragmentSize(properties.getHighlightFragmentSize());
        }
    }

    private SearchResponse searchMultiType(SearchRequest request, List<SearchProvider<?>> providers, long start) {
        // 每个类型分配的页大小
        int perTypeLimit = Math.max(request.getPageSize(), 10);

        // 并行搜索各类型
        List<CompletableFuture<SearchResponse>> futures = providers.stream()
                .map(provider -> CompletableFuture.supplyAsync(() -> {
                    try {
                        SearchRequest typeRequest = copyRequest(request);
                        typeRequest.setTypes(List.of(provider.getType()));
                        typeRequest.setPageSize(perTypeLimit);
                        return searchEngine.search(typeRequest);
                    } catch (Exception e) {
                        log.warn("[UnifiedSearch] 类型 {} 搜索失败: {}",
                                provider.getType(), e.getMessage());
                        return SearchResponse.empty(1, perTypeLimit);
                    }
                }, searchExecutor))
                .toList();

        // 等待所有搜索完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 合并结果
        List<SearchHit> allHits = new ArrayList<>();
        long total = 0;
        for (CompletableFuture<SearchResponse> future : futures) {
            SearchResponse resp = future.join();
            allHits.addAll(resp.getHits());
            total += resp.getTotal();
        }

        // 按分数排序
        allHits.sort(Comparator.comparingDouble((SearchHit h) -> -h.getScore()));

        // 分页截取
        int fromIndex = Math.min(request.getOffset(), allHits.size());
        int toIndex = Math.min(fromIndex + request.getPageSize(), allHits.size());
        List<SearchHit> pageHits = allHits.subList(fromIndex, toIndex);

        long took = System.currentTimeMillis() - start;

        // 如果无结果，生成纠错建议
        SearchSuggestion suggestion = null;
        if (allHits.isEmpty() && request.getKeyword() != null) {
            suggestion = didYouMean(request.getKeyword());
        }

        return SearchResponse.builder()
                .hits(pageHits)
                .total(total)
                .page(request.getPage())
                .pageSize(request.getPageSize())
                .tookMs(took)
                .suggestion(suggestion)
                .engine(searchEngine.getName())
                .build();
    }

    private SearchRequest copyRequest(SearchRequest original) {
        return SearchRequest.builder()
                .keyword(original.getKeyword())
                .types(original.getTypes())
                .page(original.getPage())
                .pageSize(original.getPageSize())
                .sortBy(original.getSortBy())
                .ascending(original.isAscending())
                .highlight(original.isHighlight())
                .highlightPreTag(original.getHighlightPreTag())
                .highlightPostTag(original.getHighlightPostTag())
                .highlightFragmentSize(original.getHighlightFragmentSize())
                .fuzzy(original.isFuzzy())
                .fuzzyMinSimilarity(original.getFuzzyMinSimilarity())
                .filters(original.getFilters())
                .aggregations(original.getAggregations())
                .tenantId(original.getTenantId())
                .userId(original.getUserId())
                .titleOnly(original.isTitleOnly())
                .build();
    }
}
