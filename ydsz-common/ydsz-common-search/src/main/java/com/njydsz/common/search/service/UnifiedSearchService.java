package com.njydsz.common.search.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.search.analytics.SearchAnalyticsService;
import com.njydsz.common.search.analytics.SearchQualityTracker;
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

/**
 * 统一搜索服务接口。
 *
 * <p>跨多业务实体（项目/合同/任务/文档）联合检索。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class UnifiedSearchService {

  private final SearchEngineRegistry engineRegistry;
  private final SearchProviderRegistry providerRegistry;
  private final SearchProperties properties;
  private final SearchCacheService cacheService;
  private final SearchMetrics metrics;
  private final SearchAnalyticsService analyticsService;
  private final SearchQualityTracker qualityTracker;
  private final SearchTextProcessor textProcessor;
  private final ThreadPoolTaskExecutor searchExecutor;
  private final BusinessRanker ranker;

  /** Resilience4j 熔断器，提供标准化状态机与 HALF_OPEN 自动探测 */
  private final CircuitBreaker circuitBreaker;

  private final Semaphore searchConcurrencyLimit;

  /**
   * 创建统一搜索服务（使用外部注入的线程池和共享缓存）。
   *
   * <p>推荐用法：由 {@code SearchAutoConfiguration} 注入通过 {@code ydsz.thread.pools.searchExecutor}
   * 配置的统一管理线程池， 或直接注入业务方自定义的 {@link ThreadPoolTaskExecutor}。
   *
   * @param engineRegistry 引擎注册表
   * @param providerRegistry 提供者注册表
   * @param properties 搜索配置
   * @param metrics 指标采集器
   * @param analyticsService 行为分析服务
   * @param qualityTracker 搜索质量追踪器
   * @param textProcessor 查询文本预处理器
   * @param ranker 业务重排器
   * @param searchCacheService 共享搜索缓存服务
   * @param searchExecutor 外部注入的线程池（不可为 {@code null}）
   */
  public UnifiedSearchService(
      SearchEngineRegistry engineRegistry,
      SearchProviderRegistry providerRegistry,
      SearchProperties properties,
      SearchMetrics metrics,
      SearchAnalyticsService analyticsService,
      SearchQualityTracker qualityTracker,
      SearchTextProcessor textProcessor,
      BusinessRanker ranker,
      SearchCacheService searchCacheService,
      ThreadPoolTaskExecutor searchExecutor) {
    this.engineRegistry = engineRegistry;
    this.providerRegistry = providerRegistry;
    this.properties = properties;
    this.metrics = metrics;
    this.analyticsService = analyticsService;
    this.qualityTracker = qualityTracker;
    this.textProcessor = textProcessor;
    this.ranker = ranker;
    this.cacheService = searchCacheService;
    this.searchExecutor = searchExecutor;
    this.searchConcurrencyLimit = new Semaphore(properties.getMaxPageSize(), true);
    this.circuitBreaker = createCircuitBreaker(properties);
  }

  /**
   * 创建统一搜索服务（使用默认自创建线程池，兼容无统一线程池场景）。
   *
   * <p>当 classpath 上不存在 {@code ydsz-common-thread} 或未配置 {@code ydsz.thread.pools.searchExecutor}
   * 时，回退为内部创建线程池以保证可用性。
   *
   * @param engineRegistry 引擎注册表
   * @param providerRegistry 提供者注册表
   * @param properties 搜索配置
   * @param metrics 指标采集器
   * @param analyticsService 行为分析服务
   * @param qualityTracker 搜索质量追踪器
   * @param textProcessor 查询文本预处理器
   * @param ranker 业务重排器
   */
  public UnifiedSearchService(
      SearchEngineRegistry engineRegistry,
      SearchProviderRegistry providerRegistry,
      SearchProperties properties,
      SearchMetrics metrics,
      SearchAnalyticsService analyticsService,
      SearchQualityTracker qualityTracker,
      SearchTextProcessor textProcessor,
      BusinessRanker ranker) {
    this(
        engineRegistry,
        providerRegistry,
        properties,
        metrics,
        analyticsService,
        qualityTracker,
        textProcessor,
        ranker,
        new SearchCacheService(properties),
        createDefaultSearchExecutor(properties));
  }

  /**
   * 创建默认搜索线程池。
   *
   * <p>仅在未注入外部线程池时使用，线程池参数与原有逻辑保持一致。
   *
   * @param properties 搜索配置
   * @return 默认搜索线程池
   */
  public static ThreadPoolTaskExecutor createDefaultSearchExecutor(SearchProperties properties) {
    int coreSize = Math.max(2, properties.getIndex().getThreadPoolSize());
    int maxSize = Math.max(4, properties.getIndex().getThreadPoolSize() * 2);
        // CHECKSTYLE.OFF: RegexpSinglelineJava
    // 兜底线程池：仅在外部未注入线程池时使用，生产环境由 ydsz.thread.pools.* 统一管理
    ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
    // CHECKSTYLE.ON: RegexpSinglelineJava
    taskExecutor.setCorePoolSize(coreSize);
    taskExecutor.setMaxPoolSize(maxSize);
    taskExecutor.setQueueCapacity(256);
    taskExecutor.setThreadNamePrefix("ydsz-search-");
    taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
    taskExecutor.setAwaitTerminationSeconds(5);
    taskExecutor.initialize();
    return taskExecutor;
  }

  /**
   * 执行跨实体类型的统一检索，串联限流、熔断、缓存、分词、权限过滤与业务重排。
   *
   * <p><b>处理顺序</b>（任一环节短路都会返回空结果而非抛异常）：
   *
   * <ol>
   *   <li>信号量限流：许可数为 {@code maxPageSize}，等待超过 {@code searchTimeout} 秒即放弃
   *   <li>参数兜底与校验：page/pageSize/高亮参数取默认值，翻页深度超 {@code maxPageDepth} 抛异常
   *   <li>文本预处理：交由 {@link SearchTextProcessor} 做分词与同义词改写，处理结果为空时保留原词
   *   <li>熔断判定：连续失败达 {@code failureThreshold} 后开启熔断，OPEN 期间直接拒绝
   *   <li>权限过滤：向各 {@link SearchProvider} 索取数据权限条件并合并进 filters
   *   <li>缓存查询：命中则直接返回，不再落引擎
   *   <li>检索执行：单类型走带超时的单引擎查询，多类型走并行分片查询后归并排序
   *   <li>后置处理：{@link BusinessRanker} 重排、指标上报、搜索行为埋点、结果回填缓存
   * </ol>
   *
   * <p><b>降级策略</b>：限流失败、线程中断、熔断开启、关键词为空、引擎异常或超时， 统一返回 {@link SearchResponse#empty}
   * 的空结果，保证搜索接口对上游始终可用。
   *
   * <p><b>副作用</b>：本方法会<b>就地修改</b>传入的 {@code request} （回填默认分页参数、覆盖 keyword 为分词结果、追加权限 filters），
   * 调用方不应复用同一个 request 对象发起第二次检索。
   *
   * <p>线程安全：熔断状态与失败计数均为原子变量，实例可被多线程并发调用。
   *
   * @param request 检索请求，不可为 {@code null}；keyword 为空时直接返回空结果
   * @return 检索响应，永不为 {@code null}；失败与降级场景返回空结果而非异常
   * @throws IllegalArgumentException 翻页深度 {@code offset} 超过 {@code maxPageDepth} 时抛出， 用于阻断深分页拖垮引擎
   */
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

      // P5-13: 启动阶段计时器
      SearchMetrics.SearchPhaseTimer phaseTimer = SearchMetrics.SearchPhaseTimer.start();

      if (textProcessor != null) {
        String processed = textProcessor.process(request.getKeyword());
        if (processed != null && !processed.isBlank()) {
          request.setKeyword(processed);
        }
      }
      long textProcessMs = phaseTimer.lap();
      metrics.recordTextProcess(textProcessMs);

      // Resilience4j 熔断器状态判断
      if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
        log.warn("[UnifiedSearch] 熔断器开启，拒绝搜索: state={}", circuitBreaker.getState());
        return SearchResponse.empty(request.getPage(), request.getPageSize());
      }
      applyProviderFilters(request);

      long cacheStart = System.nanoTime();
      SearchResponse cached = cacheService.get(request);
      long cacheQueryMs = (System.nanoTime() - cacheStart) / 1_000_000;
      metrics.recordCacheQuery(cacheQueryMs);

      if (cached != null) {
        // 回填阶段耗时信息
        cached.setTiming(
            Map.of(
                "textProcess",
                textProcessMs,
                "cacheQuery",
                cacheQueryMs,
                "engineQuery",
                0L,
                "ranking",
                0L));
        return cached;
      }

      SearchResponse response;
      try {
        // 使用 Resilience4j Try + CircuitBreaker 包装搜索执行，自动统计成功/失败
        List<SearchProvider<?>> providers = providerRegistry.getProviders(request.getTypes());
        long engineStart = System.nanoTime();
        response =
            Try.ofSupplier(
                    CircuitBreaker.decorateSupplier(
                        circuitBreaker,
                        () -> {
                          if (providers.isEmpty() || providers.size() == 1) {
                            return searchWithTimeout(request);
                          } else {
                            return searchMultiType(request, providers);
                          }
                        }))
                .getOrElseGet(
                    throwable -> {
                      // CallNotPermittedException = 熔断开启；其他异常 = 搜索失败
                      return SearchResponse.empty(request.getPage(), request.getPageSize());
                    });

        long engineQueryMs = (System.nanoTime() - engineStart) / 1_000_000;
        metrics.recordEngineQuery(engineQueryMs);

        long rankingStart = System.nanoTime();
        if (response.getHits() != null && !response.getHits().isEmpty()) {
          response.setHits(ranker.reRank(response.getHits(), request));
        }
        long rankingMs = (System.nanoTime() - rankingStart) / 1_000_000;
        metrics.recordRanking(rankingMs);

        long took = response.getTookMs();
        metrics.recordSearch(took, response.getTotal());
        analyticsService.recordSearch(request.getKeyword(), response.getTotal());
        if (qualityTracker != null) {
          qualityTracker.recordSearchEvent(response.getTotal(), took);
        }
        cacheService.put(request, response);

        // P5-13: 回填阶段耗时到响应（供前端/调试使用）
        response.setTiming(
            Map.of(
                "textProcess", textProcessMs,
                "cacheQuery", cacheQueryMs,
                "engineQuery", engineQueryMs,
                "ranking", rankingMs));

      } catch (Exception e) {
        log.error(
            "[UnifiedSearch] 搜索失败: keyword={}, circuitState={}",
            request.getKeyword(),
            circuitBreaker.getState(),
            e);
        response = SearchResponse.empty(request.getPage(), request.getPageSize());
      }

      return response;
    } finally {
      searchConcurrencyLimit.release();
    }
  }

  /**
   * 按前缀获取自动补全建议。
   *
   * <p>委派给主引擎注册的 {@link SuggestStrategy}； 若当前引擎不支持建议能力（如降级到基础 PG 全文检索）， 返回 {@code suggestions}
   * 为空列表的占位对象而非 {@code null}， 便于前端统一处理。
   *
   * @param prefix 用户已输入的前缀，原样透传给底层策略
   * @return 建议结果，类型为 {@code AUTOCOMPLETE}，永不为 {@code null}
   */
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

  /**
   * 生成「您是不是要找」纠错建议，在零结果场景下引导用户重新检索。
   *
   * <p>复用 {@link #suggest(String)} 的召回结果，仅把类型改写为 {@code DID_YOU_MEAN}，不做额外的编辑距离过滤； 需要更精确的纠错请使用
   * {@link SuggestionService#didYouMean(String)}。
   *
   * @param keyword 原始关键词，通常是命中数为 0 的查询词
   * @return 纠错建议；底层策略返回 {@code null} 时原样返回 {@code null}
   */
  public SearchSuggestion didYouMean(String keyword) {
    SearchSuggestion suggestion = suggest(keyword);
    if (suggestion != null) {
      suggestion.setType(SearchSuggestion.SuggestionType.DID_YOU_MEAN);
    }
    return suggestion;
  }

  /**
   * 清空搜索结果缓存。
   *
   * <p>索引重建或数据批量变更后必须调用，否则在缓存 TTL 内会持续返回陈旧结果。 清空后短时间内缓存命中率降为 0，全部请求穿透到引擎， 应避开业务高峰执行。
   */
  public void clearCache() {
    cacheService.clear();
  }

  /**
   * 获取当前搜索缓存条目数。
   *
   * @return 缓存中的搜索响应条目数量
   */
  public int getCacheSize() {
    return cacheService.size();
  }

  /**
   * 关闭内部搜索线程池，释放线程资源。
   *
   * <p>由容器在 Bean 销毁阶段调用。线程池配置了 {@code waitForTasksToCompleteOnShutdown=true} 与 5 秒等待，
   * 因此本方法会让在途检索任务尽量执行完毕，最多阻塞约 5 秒。
   *
   * <p>调用后本实例不可再用于检索，重复调用是安全的（幂等）。
   */
  public void shutdown() {
    searchExecutor.shutdown();
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

  private void validateRequest(SearchRequest request) {
    if (request.getOffset() > properties.getMaxPageDepth()) {
      throw new IllegalArgumentException(
          "翻页深度超过上限: offset=" + request.getOffset() + ", max=" + properties.getMaxPageDepth());
    }
  }

  private void applyProviderFilters(SearchRequest request) {
    List<SearchProvider<?>> providers = providerRegistry.getProviders(request.getTypes());
    if (providers.isEmpty()) {
      return;
    }

    SearchProviderContext context =
        SearchProviderContext.builder()
            .userId(request.getUserId())
            .tenantId(request.getTenantId())
            .roles(request.getRoles())
            .deptId(request.getDeptId())
            .admin(request.isAdmin())
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
    CompletableFuture<SearchResponse> future =
        CompletableFuture.supplyAsync(() -> engineRegistry.search(request), searchExecutor);
    try {
      return future.get(properties.getSearchTimeout(), TimeUnit.SECONDS);
    } catch (Exception e) {
      future.cancel(true);
      log.warn(
          "[UnifiedSearch] 搜索超时: keyword={}, timeout={}s",
          request.getKeyword(),
          properties.getSearchTimeout());
      return SearchResponse.empty(request.getPage(), request.getPageSize());
    }
  }

  private SearchResponse searchMultiType(SearchRequest request, List<SearchProvider<?>> providers) {
    int perTypeLimit = request.getOffset() + request.getPageSize();

    List<CompletableFuture<SearchResponse>> futures =
        providers.stream()
            .map(
                provider ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          try {
                            SearchRequest typeRequest = copyRequest(request);
                            typeRequest.setTypes(List.of(provider.getType()));
                            typeRequest.setPage(1);
                            typeRequest.setPageSize(perTypeLimit);
                            return engineRegistry.search(typeRequest);
                          } catch (Exception e) {
                            log.warn(
                                "[UnifiedSearch] 类型 {} 搜索失败: {}",
                                provider.getType(),
                                e.getMessage());
                            return SearchResponse.empty(1, perTypeLimit);
                          }
                        },
                        searchExecutor))
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
        .engine(
            engineRegistry.getPrimary() != null
                ? engineRegistry.getPrimary().getEngineName()
                : "unknown")
        .build();
  }

  /**
   * 创建 Resilience4j 熔断器实例。
   *
   * <p>使用搜索配置中的熔断参数，启动滑动窗口统计。
   * 配置包括：失败阈值、熔断等待时长、半开状态允许请求数。
   *
   * @param properties 搜索配置
   * @return Resilience4j CircuitBreaker 实例
   */
  static CircuitBreaker createCircuitBreaker(SearchProperties properties) {
    SearchProperties.CircuitBreakerConfig searchCb = properties.getCircuitBreaker();

    CircuitBreakerConfig cbConfig =
        CircuitBreakerConfig.custom()
            // 基于计数率的熔断统计：滑动窗口大小为 10 次调用
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            // 失败率阈值达到 50% 时触发熔断
            .failureRateThreshold(50)
            // 熔断持续时间（秒）
            .waitDurationInOpenState(Duration.ofSeconds(searchCb.getWaitDuration()))
            // 半开状态允许通过的请求数
            .permittedNumberOfCallsInHalfOpenState(searchCb.getHalfOpenRequests())
            // 慢调用视为失败：搜索超过 80% 超时时长即视为慢调用
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(
                Duration.ofMillis(properties.getSearchTimeout() * 800L))
            // 自动从 OPEN 转换到 HALF_OPEN
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();

    CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(cbConfig);
    CircuitBreaker breaker = registry.circuitBreaker("search-circuit-breaker");

    // 注册状态变更监听器，输出结构化日志
    breaker
        .getEventPublisher()
        .onStateTransition(
            (CircuitBreakerOnStateTransitionEvent event) ->
                log.warn(
                    "[UnifiedSearch] 熔断器状态变更: {} -> {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));
    breaker
        .getEventPublisher()
        .onError(
            event ->
                log.debug(
                    "[UnifiedSearch] 熔断器记录失败: {}",
                    event.getThrowable() == null ? "unknown" : event.getThrowable().getMessage()));
    breaker
        .getEventPublisher()
        .onSuccess(event -> log.debug("[UnifiedSearch] 熔断器记录成功"));

    return breaker;
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
        .roles(original.getRoles())
        .deptId(original.getDeptId())
        .admin(original.isAdmin())
        .titleOnly(original.isTitleOnly())
        .build();
  }
}
