package com.njydsz.common.search.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.njydsz.common.search.analytics.SearchAnalyticsService;
import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.api.SearchHit;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.api.SearchSuggestion;
import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.search.core.SearchEngine;
import com.njydsz.common.search.metrics.SearchMetrics;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;
import com.njydsz.common.search.provider.SearchProviderRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 统一搜索服务
 * <p>
 * 聚合多个 {@link SearchProvider} 的搜索结果，提供跨实体统一搜索能力。
 * 支持按类型搜索、结果合并排序、权限过滤、结果缓存、超时保护。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
public class UnifiedSearchService {

    private final SearchEngine searchEngine;
    private final SearchProviderRegistry providerRegistry;
    private final SearchProperties properties;
    private final SearchCacheService cacheService;
    private final SearchMetrics metrics;
    private final SearchAnalyticsService analyticsService;
    private final SearchTextProcessor textProcessor;
    private final ExecutorService searchExecutor;
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private volatile long circuitOpenTime = 0;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    public UnifiedSearchService(SearchEngine searchEngine,
                                 SearchProviderRegistry providerRegistry,
                                 SearchProperties properties,
                                 SearchMetrics metrics,
                                 SearchAnalyticsService analyticsService,
                                 SearchTextProcessor textProcessor) {
        this.searchEngine = searchEngine;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
        this.metrics = metrics;
        this.analyticsService = analyticsService;
        this.textProcessor = textProcessor;
        this.cacheService = new SearchCacheService(properties);
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

        applyDefaults(request);
        validateRequest(request);

        if (request.getKeyword() == null || request.getKeyword().isBlank()) {
            return SearchResponse.empty(request.getPage(), request.getPageSize());
        }

        // P0-3: process keyword (synonyms/stopwords/pinyin) before search
        if (textProcessor != null) {
            String processed = textProcessor.process(request.getKeyword());
            if (processed != null && !processed.isBlank()) {
                request.setKeyword(processed);
            }
        }

        // P1-7: 熔断检查
        if (isCircuitOpen()) {
            log.warn("[UnifiedSearch] 熔断器开启，拒绝搜索请求");
            return SearchResponse.empty(request.getPage(), request.getPageSize());
        }
        // P0-4: 权限过滤 — 注入 Provider 级别的过滤条件
        applyProviderFilters(request);


        // P0-2: 缓存命中检查
        SearchResponse cached = cacheService.get(request);
        if (cached != null) {
            log.debug("[UnifiedSearch] 缓存命中: keyword={}", request.getKeyword());
            return cached;
        }

        SearchResponse response;
        try {
            List<SearchProvider<?>> providers = providerRegistry.getProviders(request.getTypes());
            if (providers.isEmpty()) {
                response = searchWithTimeout(request);
            } else if (providers.size() == 1) {
                response = searchWithTimeout(request);
            } else {
                response = searchMultiType(request, providers, start);
            }

            // 记录指标和分析
            long took = System.currentTimeMillis() - start;
            metrics.recordSearch(took, response.getTotal());
            analyticsService.recordSearch(request.getKeyword(), response.getTotal());

            // 缓存结果
            cacheService.put(request, response);

            // 重置失败计数
            consecutiveFailures.set(0);

        } catch (Exception e) {
            log.error("[UnifiedSearch] 搜索失败: keyword={}", request.getKeyword(), e);
            consecutiveFailures.incrementAndGet();
            if (consecutiveFailures.get() >= properties.getCircuitBreaker().getFailureThreshold()) {
                openCircuit();
            }
            response = SearchResponse.empty(request.getPage(), request.getPageSize());
        }

        return response;
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

    /**
     * 清空搜索缓存
     */
    public void clearCache() {
        cacheService.clear();
    }

    /**
     * 关闭线程池（Spring 生命周期回调）
     */
    public void shutdown() {
        searchExecutor.shutdown();
        try {
            if (!searchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                searchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            searchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("[UnifiedSearch] 线程池已关闭");
    }

    // ==================== 私有方法 ====================

    private void applyDefaults(SearchRequest request) {
        if (request.getPage() <= 0) {
            request.setPage(1);
        }
        if (request.getPageSize() <= 0) {
            request.setPageSize(properties.getPageSize());
        }
        // P2-18: 深分页保护 — 限制每页最大大小
        if (request.getPageSize() > properties.getMaxPageSize()) {
            request.setPageSize(properties.getMaxPageSize());
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

    /**
     * P2-18: 深分页保护 — 限制翻页深度
     */
    private void validateRequest(SearchRequest request) {
        int offset = request.getOffset();
        if (offset > properties.getMaxPageDepth()) {
            throw new IllegalArgumentException(
                    "翻页深度超过上限: offset=" + offset + ", max=" + properties.getMaxPageDepth());
        }
    }

    /**
     * P0-4: 权限过滤 — 调用 Provider 的 getFilters 注入权限条件
     */
    private void applyProviderFilters(SearchRequest request) {
        List<SearchProvider<?>> providers = providerRegistry.getProviders(request.getTypes());
        if (providers.isEmpty()) {
            return;
        }

        SearchProviderContext context = SearchProviderContext.builder()
                .userId(request.getUserId())
                .tenantId(request.getTenantId())
                .build();

        List<SearchFilter> allFilters = new ArrayList<>(request.getFilters());
        for (SearchProvider<?> provider : providers) {
            try {
                List<SearchFilter> providerFilters = provider.getFilters(context);
                if (providerFilters != null && !providerFilters.isEmpty()) {
                    allFilters.addAll(providerFilters);
                }
            } catch (Exception e) {
                log.warn("[UnifiedSearch] Provider {} 权限过滤获取失败: {}",
                        provider.getType(), e.getMessage());
            }
        }
        request.setFilters(allFilters);
    }

    /**
     * P1-7: 搜索超时保护
     */
    private SearchResponse searchWithTimeout(SearchRequest request) {
        CompletableFuture<SearchResponse> future = CompletableFuture.supplyAsync(
                () -> searchEngine.search(request), searchExecutor);

        try {
            return future.get(properties.getSearchTimeout(), TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            log.warn("[UnifiedSearch] 搜索超时: keyword={}, timeout={}s",
                    request.getKeyword(), properties.getSearchTimeout());
            return SearchResponse.empty(request.getPage(), request.getPageSize());
        }
    }

    private SearchResponse searchMultiType(SearchRequest request, List<SearchProvider<?>> providers, long start) {
        int perTypeLimit = Math.max(request.getPageSize(), 10);

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

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<SearchHit> allHits = new ArrayList<>();
        long total = 0;
        for (CompletableFuture<SearchResponse> future : futures) {
            SearchResponse resp = future.join();
            allHits.addAll(resp.getHits());
            total += resp.getTotal();
        }

        allHits.sort(Comparator.comparingDouble((SearchHit h) -> -h.getScore()));

        int fromIndex = Math.min(request.getOffset(), allHits.size());
        int toIndex = Math.min(fromIndex + request.getPageSize(), allHits.size());
        List<SearchHit> pageHits = allHits.subList(fromIndex, toIndex);

        long took = System.currentTimeMillis() - start;

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

    private boolean isCircuitOpen() {
        if (!circuitOpen.get()) {
            return false;
        }
        // 检查是否过了熔断等待时间
        long waitMs = properties.getCircuitBreaker().getWaitDuration() * 1000L;
        if (System.currentTimeMillis() - circuitOpenTime > waitMs) {
            // 半开状态：尝试恢复
            log.info("[UnifiedSearch] 熔断器半开，尝试恢复");
            circuitOpen.set(false);
            return false;
        }
        return true;
    }

    private void openCircuit() {
        circuitOpen.set(true);
        circuitOpenTime = System.currentTimeMillis();
        log.warn("[UnifiedSearch] 熔断器开启: failures={}, wait={}s",
                consecutiveFailures.get(), properties.getCircuitBreaker().getWaitDuration());
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
