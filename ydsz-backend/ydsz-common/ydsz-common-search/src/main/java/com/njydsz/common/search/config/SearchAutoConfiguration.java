package com.njydsz.common.search.config;

import java.util.List;

import javax.sql.DataSource;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.njydsz.common.search.analytics.SearchAnalyticsService;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.core.SearchStrategy;
import com.njydsz.common.search.engine.es.ElasticsearchSearchStrategy;
import com.njydsz.common.search.engine.memory.InMemorySearchStrategy;
import com.njydsz.common.search.engine.opensearch.OpenSearchStrategy;
import com.njydsz.common.search.engine.pg.PgSearchStrategy;
import com.njydsz.common.search.engine.redis.RediSearchStrategy;
import com.njydsz.common.search.engine.solr.SolrSearchStrategy;
import com.njydsz.common.search.health.SearchHealthIndicator;
import com.njydsz.common.search.metrics.SearchMetrics;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderRegistry;
import com.njydsz.common.search.service.IndexRebuildService;
import com.njydsz.common.search.service.IndexRebuildService;
import com.njydsz.common.search.service.IndexSyncService;
import com.njydsz.common.search.service.SearchCacheService;
import com.njydsz.common.search.service.SearchTextProcessor;
import com.njydsz.common.search.service.SuggestionService;
import com.njydsz.common.search.service.UnifiedSearchService;
import com.njydsz.common.search.sync.IndexSyncListener;
import com.njydsz.common.search.sync.SearchIndexEventBridge;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索服务自动配置
 * <p>
 * 按引擎类型分区装配，通过 {@code @ConditionalOnClass} 门控确保 classpath
 * 中有对应客户端依赖时才激活引擎策略。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(SearchStrategy.class)
@ConditionalOnProperty(prefix = "ydsz.search", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SearchProperties.class)
public class SearchAutoConfiguration {

    private UnifiedSearchService unifiedSearchServiceInstance;
    private IndexSyncService indexSyncServiceInstance;
    @Autowired
    private ObjectProvider<PgSearchStrategy> pgSearchStrategyProvider;
    @Autowired
    private ObjectProvider<IndexRebuildService> indexRebuildServiceProvider;

    // ==================== 引擎策略装配 ====================

    /**
     * PG 引擎 — classpath 有 DataSource + JdbcTemplate 时激活
     */
    @Configuration
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    static class PgEngineConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "pgSearchStrategy")
        @ConditionalOnProperty(prefix = "ydsz.search", name = "primary", havingValue = "pg", matchIfMissing = true)
        public SearchStrategy pgSearchStrategy(ObjectProvider<DataSource> dataSourceProvider,
                                                 SearchProperties properties) {
            DataSource ds = dataSourceProvider.getIfAvailable();
            if (ds == null) {
                log.warn("[SearchAutoConfig] DataSource 不可用，PG 引擎降级");
                return new InMemorySearchStrategy();
            }
            return new PgSearchStrategy(ds, properties.getPg());
        }
    }

    /**
     * Elasticsearch 引擎 — 需手动配置 ydsz.search.primary=es
     */
    @Bean
    @ConditionalOnMissingBean(name = "esSearchStrategy")
    @ConditionalOnProperty(prefix = "ydsz.search", name = "primary", havingValue = "es")
    public SearchStrategy esSearchStrategy(SearchProperties properties) {
        return new ElasticsearchSearchStrategy(properties.getEs());
    }

    /**
     * RediSearch 引擎
     */
    @Bean
    @ConditionalOnMissingBean(name = "rediSearchStrategy")
    @ConditionalOnProperty(prefix = "ydsz.search", name = "primary", havingValue = "redis")
    public SearchStrategy rediSearchStrategy(SearchProperties properties) {
        return new RediSearchStrategy(properties.getRedis());
    }

    /**
     * Solr 引擎
     */
    @Bean
    @ConditionalOnMissingBean(name = "solrSearchStrategy")
    @ConditionalOnProperty(prefix = "ydsz.search", name = "primary", havingValue = "solr")
    public SearchStrategy solrSearchStrategy(SearchProperties properties) {
        return new SolrSearchStrategy(properties.getSolr());
    }

    /**
     * OpenSearch 引擎
     */
    @Bean
    @ConditionalOnMissingBean(name = "openSearchStrategy")
    @ConditionalOnProperty(prefix = "ydsz.search", name = "primary", havingValue = "opensearch")
    public SearchStrategy openSearchStrategy(SearchProperties properties) {
        return new OpenSearchStrategy(properties.getOpensearch());
    }

    /**
     * Memory 引擎 — 始终可用（降级兜底）
     */
    @Bean
    @ConditionalOnMissingBean(name = "memorySearchStrategy")
    public SearchStrategy memorySearchStrategy() {
        return new InMemorySearchStrategy();
    }

    // ==================== 核心服务装配 ====================

    @Bean
    @ConditionalOnMissingBean
    public SearchProviderRegistry searchProviderRegistry(ObjectProvider<List<SearchProvider<?>>> providersProvider) {
        List<SearchProvider<?>> providers = providersProvider.getIfAvailable();
        return new SearchProviderRegistry(providers);
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchEngineRegistry searchEngineRegistry(List<SearchStrategy> strategies,
                                                      SearchProperties properties) {
        return new SearchEngineRegistry(strategies, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchCacheService searchCacheService(SearchProperties properties) {
        return new SearchCacheService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchTextProcessor searchTextProcessor(SearchProperties properties) {
        return new SearchTextProcessor(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchMetrics searchMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new SearchMetrics(meterRegistryProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchAnalyticsService searchAnalyticsService(ObjectProvider<StringRedisTemplate> redisProvider) {
        return new SearchAnalyticsService(redisProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public UnifiedSearchService unifiedSearchService(SearchEngineRegistry engineRegistry,
                                                      SearchProviderRegistry providerRegistry,
                                                      SearchProperties properties,
                                                      SearchMetrics searchMetrics,
                                                      SearchAnalyticsService searchAnalyticsService,
                                                      SearchTextProcessor searchTextProcessor) {
        unifiedSearchServiceInstance = new UnifiedSearchService(engineRegistry, providerRegistry, properties,
                searchMetrics, searchAnalyticsService, searchTextProcessor);
        return unifiedSearchServiceInstance;
    }

    @Bean
    @ConditionalOnMissingBean
    public IndexSyncService indexSyncService(SearchEngineRegistry engineRegistry,
                                              SearchProviderRegistry providerRegistry,
                                              SearchProperties properties,
                                              SearchMetrics searchMetrics) {
        indexSyncServiceInstance = new IndexSyncService(engineRegistry, providerRegistry, properties, searchMetrics);
        return indexSyncServiceInstance;
    }

    @Bean
    @ConditionalOnMissingBean
    public IndexRebuildService indexRebuildService(IndexSyncService indexSyncService,
                                                    SearchEngineRegistry engineRegistry,
                                                    SearchProviderRegistry providerRegistry) {
        return new IndexRebuildService(indexSyncService, engineRegistry, providerRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public SuggestionService suggestionService(SearchEngineRegistry engineRegistry,
                                                SearchProperties properties) {
        return new SuggestionService(engineRegistry, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public IndexSyncListener indexSyncListener(IndexSyncService indexSyncService) {
        return new IndexSyncListener(indexSyncService);
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchIndexEventBridge searchIndexEventBridge(IndexSyncService indexSyncService,
                                                          SearchProviderRegistry providerRegistry,
                                                          SearchEngineRegistry engineRegistry,
                                                          SearchMetrics searchMetrics) {
        return new SearchIndexEventBridge(indexSyncService, providerRegistry, engineRegistry, searchMetrics);
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchHealthIndicator searchHealthIndicator(SearchEngineRegistry engineRegistry,
                                                        SearchCacheService cacheService,
                                                        SearchMetrics searchMetrics) {
        return new SearchHealthIndicator(engineRegistry, cacheService, searchMetrics);
    }

    @PreDestroy
    public void destroy() {
        if (unifiedSearchServiceInstance != null) {
            unifiedSearchServiceInstance.shutdown();
        }
        if (indexSyncServiceInstance != null) {
            indexSyncServiceInstance.shutdown();
        }
        if (pgSearchStrategyProvider != null) {
            PgSearchStrategy pgStrategy = pgSearchStrategyProvider.getIfAvailable();
            if (pgStrategy != null) {
                pgStrategy.shutdown();
            }
        }
        if (indexRebuildServiceProvider != null) {
            IndexRebuildService rebuildService = indexRebuildServiceProvider.getIfAvailable();
            if (rebuildService != null) {
                rebuildService.shutdown();
            }
        }
    }
}
