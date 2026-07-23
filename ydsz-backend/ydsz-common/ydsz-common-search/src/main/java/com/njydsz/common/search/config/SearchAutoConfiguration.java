package com.njydsz.common.search.config;

import java.util.List;

import javax.sql.DataSource;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.njydsz.common.search.analytics.SearchAnalyticsService;
import com.njydsz.common.search.core.SearchEngine;
import com.njydsz.common.search.indexer.ContentExtractor;
import com.njydsz.common.search.indexer.ContentIndexer;
import com.njydsz.common.search.engine.memory.InMemorySearchEngine;
import com.njydsz.common.search.engine.pg.PgSearchEngine;
import com.njydsz.common.search.health.SearchHealthIndicator;
import com.njydsz.common.search.metrics.SearchMetrics;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderRegistry;
import com.njydsz.common.search.service.IndexRebuildService;
import com.njydsz.common.search.service.IndexSyncService;
import com.njydsz.common.search.service.SearchCacheService;
import com.njydsz.common.search.service.SearchTextProcessor;
import com.njydsz.common.search.service.SuggestionService;
import com.njydsz.common.search.service.UnifiedSearchService;
import com.njydsz.common.search.sync.IndexSyncListener;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索服务 Spring Boot 自动配置
 *
 * <p>配置前缀：{@code ydsz.search}
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(SearchEngine.class)
@EnableConfigurationProperties(SearchProperties.class)
@ConditionalOnProperty(prefix = "ydsz.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SearchAutoConfiguration {

    private PgSearchEngine pgSearchEngineInstance;
    private UnifiedSearchService unifiedSearchServiceInstance;
    private IndexSyncService indexSyncServiceInstance;

    /**
     * PG 搜索引擎（默认引擎）
     */
    @Configuration
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    @ConditionalOnProperty(prefix = "ydsz.search", name = "engine", havingValue = "pg", matchIfMissing = true)
    static class PgSearchEngineConfiguration {

        @Bean
        @ConditionalOnMissingBean(SearchEngine.class)
        public SearchEngine pgSearchEngine(DataSource dataSource, SearchProperties properties) {
            log.info("[SearchAutoConfiguration] 初始化 PgSearchEngine: highlight={}, fuzzy={}",
                    properties.isHighlight(), properties.isFuzzy());
            return new PgSearchEngine(dataSource, properties);
        }
    }

    /**
     * 内存搜索引擎（测试/降级用）
     */
    @Configuration
    @ConditionalOnProperty(prefix = "ydsz.search", name = "engine", havingValue = "memory")
    static class InMemorySearchEngineConfiguration {

        @Bean
        @ConditionalOnMissingBean(SearchEngine.class)
        public SearchEngine inMemorySearchEngine() {
            log.info("[SearchAutoConfiguration] 初始化 InMemorySearchEngine");
            return new InMemorySearchEngine();
        }
    }

    /**
     * 搜索提供者注册中心 — 自动收集所有 SearchProvider Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchProviderRegistry searchProviderRegistry(
            List<SearchProvider<?>> providers) {
        return new SearchProviderRegistry(providers);
    }

    /**
     * 搜索缓存服务
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchCacheService searchCacheService(SearchProperties properties) {
        return new SearchCacheService(properties);
    }

    /**
     * P1-12: 搜索文本处理器（同义词/停用词/拼音）
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchTextProcessor searchTextProcessor(SearchProperties properties) {
        return new SearchTextProcessor(properties);
    }

    /**
     * 统一搜索服务
     */
    @Bean
    @ConditionalOnMissingBean
    public UnifiedSearchService unifiedSearchService(SearchEngine searchEngine,
                                                      SearchProviderRegistry providerRegistry,
                                                      SearchProperties properties,
                                                      SearchMetrics searchMetrics,
                                                      SearchAnalyticsService searchAnalyticsService,
                                                      SearchTextProcessor searchTextProcessor) {
        unifiedSearchServiceInstance = new UnifiedSearchService(searchEngine, providerRegistry, properties,
                searchMetrics, searchAnalyticsService, searchTextProcessor);
        return unifiedSearchServiceInstance;
    }

    /**
     * 索引同步服务
     */
    @Bean
    @ConditionalOnMissingBean
    public IndexSyncService indexSyncService(SearchEngine searchEngine,
                                              SearchProviderRegistry providerRegistry,
                                              SearchProperties properties,
                                              SearchMetrics searchMetrics) {
        indexSyncServiceInstance = new IndexSyncService(searchEngine, providerRegistry, properties, searchMetrics);
        return indexSyncServiceInstance;
    }

    /**
     * 索引同步事件监听器
     */
    @Bean
    @ConditionalOnMissingBean
    public IndexSyncListener indexSyncListener(IndexSyncService indexSyncService) {
        return new IndexSyncListener(indexSyncService);
    }

    /**
     * 索引重建服务
     */
    @Bean
    @ConditionalOnMissingBean
    public IndexRebuildService indexRebuildService(IndexSyncService indexSyncService,
                                                    SearchEngine searchEngine,
                                                    SearchProviderRegistry providerRegistry) {
        return new IndexRebuildService(indexSyncService, searchEngine, providerRegistry);
    }

    /**
     * 搜索建议服务
     */
    @Bean
    @ConditionalOnMissingBean
    public SuggestionService suggestionService(SearchEngine searchEngine,
                                                SearchProperties properties) {
        return new SuggestionService(searchEngine, properties);
    }

    /**
     * 搜索分析服务
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchAnalyticsService searchAnalyticsService() {
        return new SearchAnalyticsService();
    }

    /**
     * 搜索指标（Micrometer 可用时自动注册）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(MeterRegistry.class)
    public SearchMetrics searchMetrics(MeterRegistry meterRegistry) {
        return new SearchMetrics(meterRegistry);
    }

    /**
     * P1-6: 内容索引器（ContentExtractor 可用时自动注册）
     */
    @Bean
    @ConditionalOnMissingBean
    public ContentIndexer contentIndexer(
            ObjectProvider<ContentExtractor> extractorProvider) {
        return new ContentIndexer(extractorProvider.getIfAvailable());
    }

    /**
     * 搜索健康检查（Spring Boot Actuator 可用时自动注册）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    public SearchHealthIndicator searchHealthIndicator(SearchEngine searchEngine) {
        return new SearchHealthIndicator(searchEngine);
    }

    /**
     * P1-10: 线程池生命周期管理 — 关闭所有线程池
     */
    @PreDestroy
    public void shutdown() {
        log.info("[SearchAutoConfiguration] 开始关闭搜索服务资源...");
        if (unifiedSearchServiceInstance != null) {
            unifiedSearchServiceInstance.shutdown();
        }
        if (indexSyncServiceInstance != null) {
            indexSyncServiceInstance.shutdown();
        }
        if (pgSearchEngineInstance != null) {
            pgSearchEngineInstance.shutdown();
        }
        log.info("[SearchAutoConfiguration] 搜索服务资源已关闭");
    }
}
