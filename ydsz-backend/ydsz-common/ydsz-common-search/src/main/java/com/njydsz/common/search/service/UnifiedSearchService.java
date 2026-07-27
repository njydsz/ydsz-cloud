package com.njydsz.common.search.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.search.analytics.SearchAnalyticsService;
import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.api.SearchHit;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.api.SearchSuggestion;
import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.core.SuggestStrategy;
import com.njydsz.common.search.metrics.SearchMetrics;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;
import com.njydsz.common.search.provider.SearchProviderRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 统一搜索服务
 * <p>
 * 聚合多个 {@link SearchProvider} 的搜索结果，提供跨实体统一搜索能力。
 * 通过 {@link SearchEngineRegistry} 委托搜索引擎策略，支持主引擎 + 降级链。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
public class UnifiedSearchService {

    private final SearchEngineRegistry engineRegistry;
    private final SearchProviderRegistry providerRegistry;
    private final SearchProperties properties;
    private final SearchCacheService cacheService;
    private final SearchMetrics metrics;
    private final SearchAnalyticsService analyticsService;
    private final SearchTextProcessor textProcessor;
    private final ThreadPoolTaskExecutor searchExecutor;

    private enum CircuitState { CLOSED, OPEN, HALF_OPEN }
    private final AtomicReference<CircuitState> circuitState = new AtomicReference<>(CircuitState.CLOSED);
    private volatile long circuitOpenTime = 0;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger halfOpenProbeCount = new AtomicInteger(0);
    private final Semaphore searchConcurrencyLimit;

    public UnifiedSearchService(SearchEngineRegistry engineRegistry,
                                SearchProviderRegistry providerRegistry,
                                SearchProperties properties,
                                SearchMetrics metrics,
                                SearchAnalyticsService analyticsService,
                                SearchTextProcessor textProcessor) {
        this.engineRegistry = engineRegistry;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
        this.metrics = metrics;
        this.analyticsService = analyticsService;
        this.textProcessor = textProcessor;
        this.cacheService = new SearchCacheService(properties);

        this.searchExecutor = new ThreadPoolTaskExecutor();
        this.searchExecutor.setCorePoolSize(Math.max(2, properties.getIndex().getThreadPoolSize()));
        this.searchExecutor.setMaxPoolSize(Math.max(4, properties.getIndex().getThreadPoolSize() * 2));
        this.searchExecutor.setQueueCapacity(256);
        this.searchExecutor.setThreadNamePrefix("search-");
        this.searchExecutor.setWaitForTasksToCompleteOnShutdown(true);
        this.searchExecutor.setAwaitTerminationSeconds(5);
        this.searchExecutor.initialize();

        this.searchConcurrencyLimit = new Semaphore(properties.getMaxPageSize(), true);
    }

    public SearchResponse search(SearchRequest request) {
        try {
            if (!searchConcurrencyLimit.tryAcquire(properties.getSearchTimeout(), TimeUnit.SECONDS)) {
                log.warn("[UnifiedSearch] 搜索并发数超限: keyword={}", request.getKeyword());
                return SearchResponse.empty(request.getPage(), request.getPageSize());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SearchResponse.empty(request.getPage(), request.getPageSize());
        }

        try {
            applyDefaults(request);
            validateRequest(request);

            if (request.getKeyword() == null || request.getKeyword().isBlank()) {
                return SearchResponse.empty(request.getPage(), request.getPageSize());
            }

            if (textProcessor != null) {
                String processed = textProcessor.process(request.getKeyword());
                if (processed != null && !processed.isBlank()) {
                    request.setKeyword(processed);
                }
            }

            if (isCircuitOpen()) {
                log.warn("[UnifiedSearch] 熔断器开启，拒绝搜索");
                return SearchResponse.empty(request.getPage(), request.getPageSize());
            }
            applyProviderFilters(request);

            SearchResponse cached = cacheService.get(request);
            if (cached != null) {
                return cached;
            }

            SearchResponse response;
            try {
                List<SearchProvider<?>> providers = providerRegistry.getProviders(request.getTypes());
                if (providers.isEmpty() || providers.size() == 1) {
                    response = searchWithTimeout(request);
                } else {
                    response = searchMultiType(request, providers);
                }

                long took = response.getTookMs();
                metrics.recordSearch(took, response.getTotal());
                analyticsService.recordSearch(request.getKeyword(), response.getTotal());
                closeCircuitIfHalfOpen();
                cacheService.put(request, response);
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
        } finally {
            searchConcurrencyLimit.release();
        }
    }

    public SearchSuggestion suggest(String prefix) {
        Optional<SuggestStrategy> suggestStrategy = engineRegistry.getSuggestStrategy();
        if (suggestStrategy.isPresent()) {
            return suggestStrategy.get().suggest(prefix, properties.getSuggestLimit());
        }
        return SearchSuggestion.builder()
                .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
                .suggestions(List.of())
                .originalInput(prefix)
                .build();
    }

    public SearchSuggestion didYouMean(String keyword) {
        SearchSuggestion suggestion = suggest(keyword);
        if (suggestion != null) {
            suggestion.setType(SearchSuggestion.SuggestionType.DID_YOU_MEAN);
        }
        return suggestion;
    }

    public void clearCache() {
        cacheService.clear();
    }

    public int getCacheSize() {
        return cacheService.size();
    }

    public void shutdown() {
        searchExecutor.shutdown();
        log.info("[UnifiedSearch] 线程池已关闭");
    }

    // ==================== 私有方法 ====================

    private void applyDefaults(SearchRequest request) {
        if (request.getPage() <= 0) request.setPage(1);
        if (request.getPageSize() <= 0) request.setPageSize(properties.getPageSize());
        if (request.getPageSize() > properties.getMaxPageSize()) request.setPageSize(properties.getMaxPageSize());
        if (request.getHighlightPreTag() == null) request.setHighlightPreTag(properties.getHighlightPreTag());
        if (request.getHighlightPostTag() == null) request.setHighlightPostTag(properties.getHighlightPostTag());
        if (request.getHighlightFragmentSize() <= 0) request.setHighlightFragmentSize(properties.getHighlightFragmentSize());
    }

    private void validateRequest(SearchRequest request) {
        if (request.getOffset() > properties.getMaxPageDepth()) {
            throw new IllegalArgumentException(
                    "翻页深度超过上限: offset=" + request.getOffset() + ", max=" + properties.getMaxPageDepth());
        }
    }

    private void applyProviderFilters(SearchRequest request) {
        List<SearchProvider<?>> providers = providerRegistry.getProviders(request.getTypes());
        if (providers.isEmpty()) return;

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
                log.warn("[UnifiedSearch] Provider {} 权限过滤获取失败: {}", provider.getType(), e.getMessage());
            }
        }
        request.setFilters(allFilters);
    }

    private SearchResponse searchWithTimeout(SearchRequest request) {
        CompletableFuture<SearchResponse> future = CompletableFuture.supplyAsync(
                () -> engineRegistry.search(request), searchExecutor);
        try {
            return future.get(properties.getSearchTimeout(), TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            log.warn("[UnifiedSearch] 搜索超时: keyword={}, timeout={}s", request.getKeyword(), properties.getSearchTimeout());
            return SearchResponse.empty(request.getPage(), request.getPageSize());
        }
    }

    private SearchResponse searchMultiType(SearchRequest request, List<SearchProvider<?>> providers) {
        int perTypeLimit = request.getOffset() + request.getPageSize();

        List<CompletableFuture<SearchResponse>> futures = providers.stream()
                .map(provider -> CompletableFuture.supplyAsync(() -> {
                    try {
                        SearchRequest typeRequest = copyRequest(request);
                        typeRequest.setTypes(List.of(provider.getType()));
                        typeRequest.setPage(1);
                        typeRequest.setPageSize(perTypeLimit);
                        return engineRegistry.search(typeRequest);
                    } catch (Exception e) {
                        log.warn("[UnifiedSearch] 类型 {} 搜索失败: {}", provider.getType(), e.getMessage());
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

        SearchSuggestion suggestion = null;
        if (allHits.isEmpty() && request.getKeyword() != null) {
            suggestion = didYouMean(request.getKeyword());
        }

        return SearchResponse.builder()
                .hits(allHits.subList(fromIndex, toIndex))
                .total(total)
                .page(request.getPage())
                .pageSize(request.getPageSize())
                .tookMs(0)
                .suggestion(suggestion)
                .engine(engineRegistry.getPrimary() != null ? engineRegistry.getPrimary().getEngineName() : "unknown")
                .build();
    }

    private boolean isCircuitOpen() {
        CircuitState state = circuitState.get();
        if (state == CircuitState.CLOSED) return false;
        if (state == CircuitState.OPEN) {
            long waitMs = properties.getCircuitBreaker().getWaitDuration() * 1000L;
            if (System.currentTimeMillis() - circuitOpenTime > waitMs) {
                if (circuitState.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                    halfOpenProbeCount.set(0);
                    return false;
                }
                return circuitState.get() == CircuitState.OPEN;
            }
            return true;
        }
        return halfOpenProbeCount.incrementAndGet() > properties.getCircuitBreaker().getHalfOpenRequests();
    }

    private void openCircuit() {
        circuitState.set(CircuitState.OPEN);
        circuitOpenTime = System.currentTimeMillis();
        log.warn("[UnifiedSearch] 熔断器开启: failures={}", consecutiveFailures.get());
    }

    private void closeCircuitIfHalfOpen() {
        if (circuitState.get() == CircuitState.HALF_OPEN) {
            circuitState.set(CircuitState.CLOSED);
            halfOpenProbeCount.set(0);
            log.info("[UnifiedSearch] 熔断器恢复");
        }
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
